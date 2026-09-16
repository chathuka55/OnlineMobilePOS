package com.possaas.subscription.gateway;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.possaas.subscription.config.SubscriptionProperties;
import com.possaas.subscription.domain.GatewayEvent;
import com.possaas.subscription.domain.PaymentGatewayType;
import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.repository.GatewayEventRepository;
import com.possaas.subscription.repository.SubscriptionRepository;
import com.possaas.subscription.service.SubscriptionLifecycleService;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * PayHere checkout + notify URL stub for Sri Lankan card payments.
 */
@Component
public class PayHereGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(PayHereGateway.class);

    private final SubscriptionProperties properties;
    private final GatewayEventRepository gatewayEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;

    public PayHereGateway(SubscriptionProperties properties,
                          GatewayEventRepository gatewayEventRepository,
                          SubscriptionRepository subscriptionRepository,
                          SubscriptionLifecycleService lifecycleService,
                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.gatewayEventRepository = gatewayEventRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.lifecycleService = lifecycleService;
        this.objectMapper = objectMapper;
    }

    @Override
    public PaymentGatewayType type() {
        return PaymentGatewayType.PAYHERE;
    }

    @Override
    @Transactional
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        // TODO: Build signed PayHere checkout form / hash with merchant secret
        String orderId = "ph_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String host = properties.payhere().sandbox()
                ? "https://sandbox.payhere.lk/pay/checkout"
                : "https://www.payhere.lk/pay/checkout";

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("merchant_id", properties.payhere().merchantId());
        params.add("order_id", orderId);
        params.add("amount", request.amount() == null ? "0" : request.amount().toPlainString());
        params.add("currency", request.currency());
        params.add("return_url", request.successUrl());
        params.add("cancel_url", request.cancelUrl());
        params.add("custom_1", request.tenantId().toString());

        String checkoutUrl = UriComponentsBuilder.fromUriString(host)
                .queryParams(params)
                .build(true)
                .toUriString();

        Subscription subscription = request.subscription();
        subscription.setGateway(PaymentGatewayType.PAYHERE.name());
        subscription.setGatewayCustomerRef(request.customerEmail());
        subscriptionRepository.save(subscription);

        log.info("PayHere checkout session stub created: orderId={} tenant={} plan={} sandbox={}",
                orderId, request.tenantId(), request.planCode(), properties.payhere().sandbox());

        return new CheckoutSession(orderId, checkoutUrl, PaymentGatewayType.PAYHERE);
    }

    @Override
    @Transactional
    public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
        // TODO: Verify md5sig against merchant_id + order_id + amount + currency + status + secret
        Map<String, Object> body = parsePayload(payload);
        String orderId = stringVal(body.get("order_id"), "ph_stub_" + UUID.randomUUID());
        String statusCode = stringVal(body.get("status_code"), "2");
        String eventType = "payhere.status." + statusCode;
        String externalEventId = orderId + ":" + statusCode + ":"
                + stringVal(body.get("payment_id"), "0");

        if (gatewayEventRepository.existsByGatewayAndExternalEventId(
                PaymentGatewayType.PAYHERE, externalEventId)) {
            log.info("Ignoring duplicate PayHere event {}", externalEventId);
            return new WebhookResult(false, externalEventId, eventType, null, "duplicate");
        }

        UUID tenantId = extractTenantId(body);
        GatewayEvent event = GatewayEvent.received(
                PaymentGatewayType.PAYHERE, externalEventId, eventType, tenantId, body);
        gatewayEventRepository.save(event);

        try {
            if ("2".equals(statusCode)) {
                applyPaid(tenantId, orderId);
            } else if ("0".equals(statusCode) || "-1".equals(statusCode) || "-2".equals(statusCode)) {
                applyPaymentFailed(tenantId);
            }
            event.markProcessed();
            gatewayEventRepository.save(event);
            return new WebhookResult(true, externalEventId, eventType, tenantId, "ok");
        } catch (RuntimeException ex) {
            event.markFailed(ex.getMessage());
            gatewayEventRepository.save(event);
            log.error("Failed processing PayHere event {}", externalEventId, ex);
            return new WebhookResult(false, externalEventId, eventType, tenantId, ex.getMessage());
        }
    }

    private void applyPaid(UUID tenantId, String orderId) {
        if (tenantId == null) {
            log.warn("PayHere paid notify missing tenant id");
            return;
        }
        subscriptionRepository.findByTenantId(tenantId).ifPresent(subscription -> {
            Instant periodEnd = subscription.getBillingCycle() != null
                    && "YEARLY".equals(subscription.getBillingCycle().name())
                    ? Instant.now().plus(Duration.ofDays(365))
                    : Instant.now().plus(Duration.ofDays(30));
            lifecycleService.activate(subscription, periodEnd);
            subscription.setGatewaySubscriptionRef(orderId);
            subscriptionRepository.save(subscription);
        });
    }

    private void applyPaymentFailed(UUID tenantId) {
        if (tenantId == null) {
            return;
        }
        subscriptionRepository.findByTenantId(tenantId)
                .ifPresent(lifecycleService::markPastDue);
    }

    private Map<String, Object> parsePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("order_id", "ph_stub_" + UUID.randomUUID());
            empty.put("status_code", "2");
            return empty;
        }
        String trimmed = payload.trim();
        if (trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                // fall through to form parsing
            }
        }
        Map<String, Object> form = new LinkedHashMap<>();
        for (String pair : trimmed.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                form.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        if (!form.containsKey("order_id")) {
            form.put("order_id", "ph_stub_" + UUID.randomUUID());
        }
        if (!form.containsKey("status_code")) {
            form.put("status_code", "2");
        }
        return form;
    }

    private UUID extractTenantId(Map<String, Object> body) {
        Object custom = body.get("custom_1");
        if (custom == null) {
            custom = body.get("tenant_id");
        }
        if (custom == null) {
            return null;
        }
        try {
            return UUID.fromString(custom.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String stringVal(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }
}
