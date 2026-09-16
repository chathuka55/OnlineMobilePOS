package com.possaas.subscription.gateway;

import com.possaas.common.error.ApiException;
import com.possaas.subscription.config.SubscriptionProperties;
import com.possaas.subscription.domain.PaymentGatewayType;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PaymentGatewayResolver {

    private final Map<PaymentGatewayType, PaymentGateway> byType;
    private final SubscriptionProperties properties;

    public PaymentGatewayResolver(List<PaymentGateway> gateways,
                                  SubscriptionProperties properties) {
        this.properties = properties;
        this.byType = new EnumMap<>(PaymentGatewayType.class);
        for (PaymentGateway gateway : gateways) {
            byType.put(gateway.type(), gateway);
        }
    }

    public PaymentGateway resolveConfigured() {
        String configured = properties.gateway() == null
                ? "none"
                : properties.gateway().trim().toLowerCase(Locale.ROOT);
        return switch (configured) {
            case "stripe" -> require(PaymentGatewayType.STRIPE);
            case "payhere" -> require(PaymentGatewayType.PAYHERE);
            default -> require(PaymentGatewayType.STRIPE); // default stub for checkout demos
        };
    }

    public PaymentGateway require(PaymentGatewayType type) {
        PaymentGateway gateway = byType.get(type);
        if (gateway == null) {
            throw ApiException.of(
                    com.possaas.common.error.ErrorCode.INTERNAL_ERROR,
                    "Payment gateway not configured: " + type);
        }
        return gateway;
    }
}
