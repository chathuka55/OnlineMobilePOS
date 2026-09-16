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

/**
 * Stripe Checkout + webhook stub. Real Stripe SDK calls are TODO; events are
 * persisted for idempotent redelivery handling.
 */
@Component
public class StripeGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);

    private final SubscriptionProperties properties;
    private final GatewayEventRepository gatewayEventRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionLifecycleService lifecycleService;
    private final ObjectMapper objectMapper;

    public StripeGateway(SubscriptionProperties properties,
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
        return PaymentGatewayType.STRIPE;
    }

    @Override
    @Transactional
    public CheckoutSession createCheckoutSession(CheckoutRequest request) {
        // TODO: Call Stripe Checkout Sessions API with properties.stripe().secretKey()
        if (properties.stripe().secretKey() == null || properties.stripe().secretKey().isBlank()) {
            log.warn("Stripe secret key is not configured; returning a stub checkout session");
        }
        String sessionId = "cs_test_" + UUID.randomUUID().toString().replace("-", "");
        String checkoutUrl = "https://checkout.stripe.com/c/pay/" + sessionId
                + "?success=" + encode(request.successUrl())
                + "&cancel=" + encode(request.cancelUrl());

        Subscription subscription = request.subscription();
        subscription.setGateway(PaymentGatewayType.STRIPE.name());
        subscription.setGatewayCustomerRef("cus_stub_" + request.tenantId());
        subscriptionRepository.save(subscription);

        log.info("Stripe checkout session stub created: sessionId={} tenant={} plan={} amount={} {}",
                sessionId, request.tenantId(), request.planCode(),
                request.amount(), request.currency());

        return new CheckoutSession(sessionId, checkoutUrl, PaymentGatewayType.STRIPE);
    }

    @Override
    @Transactional
    public WebhookResult handleWebhook(String payload, Map<String, String> headers) {
        // TODO: Verify Stripe-Signature header against properties.stripe().webhookSecret()
        Map<String, Object> body = parsePayload(payload);
        String externalEventId = stringVal(body.get("id"), "evt_stub_" + UUID.randomUUID());
        String eventType = stringVal(body.get("type"), "checkout.session.completed");

        if (gatewayEventRepository.existsByGatewayAndExternalEventId(
                PaymentGatewayType.STRIPE, externalEventId)) {
            log.info("Ignoring duplicate Stripe event {}", externalEventId);
            return new WebhookResult(false, externalEventId, eventType, null, "duplicate");
        }

        UUID tenantId = extractTenantId(body);
        GatewayEvent event = GatewayEvent.received(
                PaymentGatewayType.STRIPE, externalEventId, eventType, tenantId, body);
        gatewayEventRepository.save(event);

        try {
            if ("checkout.session.completed".equals(eventType)
                    || "invoice.paid".equals(eventType)) {
                applyPaid(tenantId, body);
            } else if ("invoice.payment_failed".equals(eventType)) {
                applyPaymentFailed(tenantId);
            }
            event.markProcessed();
            gatewayEventRepository.save(event);
            return new WebhookResult(true, externalEventId, eventType, tenantId, "ok");
        } catch (RuntimeException ex) {
            event.markFailed(ex.getMessage());
            gatewayEventRepository.save(event);
            log.error("Failed processing Stripe event {}", externalEventId, ex);
            return new WebhookResult(false, externalEventId, eventType, tenantId, ex.getMessage());
        }
    }

    private void applyPaid(UUID tenantId, Map<String, Object> body) {
        if (tenantId == null) {
            log.warn("Stripe paid event missing tenant id; payload logged only");
            return;
        }
        subscriptionRepository.findByTenantId(tenantId).ifPresent(subscription -> {
            boolean yearly = subscription.getBillingCycle() != null
                    && "YEARLY".equals(subscription.getBillingCycle().name());
            Instant periodEnd = Instant.now().plus(Duration.ofDays(yearly ? 365 : 30));
            lifecycleService.activate(subscription, periodEnd);
            subscription.setGatewaySubscriptionRef(stringVal(body.get("id"), null));
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
            return Map.of("type", "checkout.session.completed",
                    "id", "evt_stub_" + UUID.randomUUID());
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {
            });
        } catch (Exception ex) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("type", "checkout.session.completed");
            fallback.put("id", "evt_stub_" + UUID.randomUUID());
            fallback.put("raw", payload);
            return fallback;
        }
    }

    @SuppressWarnings("unchecked")
    private UUID extractTenantId(Map<String, Object> body) {
        Object data = body.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object object = dataMap.get("object");
            if (object instanceof Map<?, ?> objectMap) {
                Object metadata = objectMap.get("metadata");
                if (metadata instanceof Map<?, ?> meta) {
                    Object tenant = meta.get("tenant_id");
                    if (tenant != null) {
                        return UUID.fromString(tenant.toString());
                    }
                }
                Object clientRef = objectMap.get("client_reference_id");
                if (clientRef != null) {
                    try {
                        return UUID.fromString(clientRef.toString());
                    } catch (IllegalArgumentException ignored) {
                        // fall through
                    }
                }
            }
        }
        Object tenant = body.get("tenant_id");
        if (tenant != null) {
            try {
                return UUID.fromString(tenant.toString());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private static String stringVal(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private static String encode(String url) {
        return url == null ? "" : url.replace(" ", "%20");
    }
}
