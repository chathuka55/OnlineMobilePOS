package com.possaas.subscription.gateway;

import com.possaas.subscription.domain.BillingCycle;
import com.possaas.subscription.domain.PaymentGatewayType;
import com.possaas.subscription.domain.Subscription;
import java.util.Map;
import java.util.UUID;

/**
 * Abstraction over Stripe / PayHere (and future gateways). Implementations must
 * persist {@code gateway_events} for every webhook delivery.
 */
public interface PaymentGateway {

    PaymentGatewayType type();

    CheckoutSession createCheckoutSession(CheckoutRequest request);

    WebhookResult handleWebhook(String payload, Map<String, String> headers);

    record CheckoutRequest(
            UUID tenantId,
            Subscription subscription,
            String planCode,
            BillingCycle billingCycle,
            String currency,
            java.math.BigDecimal amount,
            String successUrl,
            String cancelUrl,
            String customerEmail
    ) {
    }

    record CheckoutSession(
            String sessionId,
            String checkoutUrl,
            PaymentGatewayType gateway
    ) {
    }

    record WebhookResult(
            boolean processed,
            String externalEventId,
            String eventType,
            UUID tenantId,
            String message
    ) {
    }
}
