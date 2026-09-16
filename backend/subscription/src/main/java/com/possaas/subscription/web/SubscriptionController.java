package com.possaas.subscription.web;

import com.possaas.common.tenant.TenantContext;
import com.possaas.subscription.api.dto.SubscriptionDtos.CheckoutSessionResponse;
import com.possaas.subscription.api.dto.SubscriptionDtos.CreateCheckoutRequest;
import com.possaas.subscription.api.dto.SubscriptionDtos.PlanResponse;
import com.possaas.subscription.api.dto.SubscriptionDtos.SubscriptionResponse;
import com.possaas.subscription.domain.BillingCycle;
import com.possaas.subscription.domain.Plan;
import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.gateway.PaymentGateway;
import com.possaas.subscription.gateway.PaymentGatewayResolver;
import com.possaas.subscription.repository.PlanRepository;
import com.possaas.subscription.repository.SubscriptionRepository;
import com.possaas.subscription.service.SubscriptionQueryService;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.TenantRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscription")
public class SubscriptionController {

    private final SubscriptionQueryService queryService;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final TenantRepository tenantRepository;
    private final PaymentGatewayResolver gatewayResolver;

    public SubscriptionController(SubscriptionQueryService queryService,
                                  SubscriptionRepository subscriptionRepository,
                                  PlanRepository planRepository,
                                  TenantRepository tenantRepository,
                                  PaymentGatewayResolver gatewayResolver) {
        this.queryService = queryService;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.tenantRepository = tenantRepository;
        this.gatewayResolver = gatewayResolver;
    }

    @GetMapping("/current")
    public SubscriptionResponse current() {
        return queryService.current();
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return queryService.publicPlans();
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutSessionResponse checkout(@Valid @RequestBody CreateCheckoutRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElseThrow(() -> com.possaas.common.error.ApiException.of(
                        com.possaas.common.error.ErrorCode.SUBSCRIPTION_INACTIVE,
                        "No subscription found for this workspace"));
        Plan plan = planRepository.findById(subscription.getPlanId())
                .orElseThrow(() -> com.possaas.common.error.ApiException.notFound(
                        "Plan", subscription.getPlanId()));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> com.possaas.common.error.ApiException.notFound(
                        "Tenant", tenantId));

        BillingCycle cycle = request.billingCycle() == null
                ? BillingCycle.MONTHLY
                : request.billingCycle();
        var amount = cycle == BillingCycle.YEARLY
                ? plan.getPriceYearly()
                : plan.getPriceMonthly();
        subscription.setBillingCycle(cycle);
        subscription.setUnitAmount(amount);
        subscriptionRepository.save(subscription);

        PaymentGateway.CheckoutSession session = gatewayResolver.resolveConfigured()
                .createCheckoutSession(new PaymentGateway.CheckoutRequest(
                        tenantId,
                        subscription,
                        plan.getCode(),
                        cycle,
                        plan.getCurrency(),
                        amount,
                        request.successUrl(),
                        request.cancelUrl(),
                        tenant.getContactEmail()));

        return new CheckoutSessionResponse(
                session.sessionId(), session.checkoutUrl(), session.gateway().name());
    }
}
