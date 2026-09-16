package com.possaas.subscription.web;

import com.possaas.subscription.domain.PaymentGatewayType;
import com.possaas.subscription.gateway.PaymentGateway;
import com.possaas.subscription.gateway.PaymentGatewayResolver;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks")
public class WebhookController {

    private final PaymentGatewayResolver gatewayResolver;

    public WebhookController(PaymentGatewayResolver gatewayResolver) {
        this.gatewayResolver = gatewayResolver;
    }

    @PostMapping(value = "/stripe", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.ALL_VALUE})
    public ResponseEntity<Map<String, Object>> stripe(
            @RequestBody(required = false) String payload,
            @RequestHeader Map<String, String> headers) {
        PaymentGateway.WebhookResult result = gatewayResolver.require(PaymentGatewayType.STRIPE)
                .handleWebhook(payload == null ? "" : payload, headers);
        return ResponseEntity.ok(toBody(result));
    }

    @PostMapping(value = "/payhere",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    MediaType.ALL_VALUE})
    public ResponseEntity<Map<String, Object>> payhere(
            @RequestBody(required = false) String payload,
            @RequestHeader Map<String, String> headers) {
        PaymentGateway.WebhookResult result = gatewayResolver.require(PaymentGatewayType.PAYHERE)
                .handleWebhook(payload == null ? "" : payload, headers);
        return ResponseEntity.ok(toBody(result));
    }

    private static Map<String, Object> toBody(PaymentGateway.WebhookResult result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("processed", result.processed());
        body.put("externalEventId", result.externalEventId());
        body.put("eventType", result.eventType());
        body.put("tenantId", result.tenantId());
        body.put("message", result.message());
        return body;
    }
}
