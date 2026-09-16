package com.possaas.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pos.subscription")
public record SubscriptionProperties(
        int trialDays,
        int gracePeriodDays,
        String defaultCurrency,
        String gateway,
        Stripe stripe,
        PayHere payhere
) {

    public SubscriptionProperties {
        if (trialDays <= 0) {
            trialDays = 14;
        }
        if (gracePeriodDays <= 0) {
            gracePeriodDays = 7;
        }
        if (defaultCurrency == null || defaultCurrency.isBlank()) {
            defaultCurrency = "LKR";
        }
        if (gateway == null || gateway.isBlank()) {
            gateway = "none";
        }
        if (stripe == null) {
            stripe = new Stripe("", "");
        }
        if (payhere == null) {
            payhere = new PayHere("", "", true);
        }
    }

    public record Stripe(String secretKey, String webhookSecret) {
    }

    public record PayHere(String merchantId, String merchantSecret, boolean sandbox) {
    }
}
