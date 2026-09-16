package com.possaas.platform.service;

import com.possaas.common.api.PageResponse;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.tenant.TenantContext;
import com.possaas.identity.domain.User;
import com.possaas.identity.repository.UserRepository;
import com.possaas.identity.security.JwtService;
import com.possaas.identity.security.PosPrincipal;
import com.possaas.platform.api.dto.PlatformDtos.ImpersonateResponse;
import com.possaas.platform.api.dto.PlatformDtos.SuspendTenantRequest;
import com.possaas.platform.api.dto.PlatformDtos.TenantDetailResponse;
import com.possaas.platform.api.dto.PlatformDtos.TenantSummaryResponse;
import com.possaas.subscription.domain.Plan;
import com.possaas.subscription.domain.Subscription;
import com.possaas.subscription.repository.PlanRepository;
import com.possaas.subscription.repository.SubscriptionRepository;
import com.possaas.tenancy.domain.Outlet;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.domain.TenantStatus;
import com.possaas.tenancy.repository.OutletRepository;
import com.possaas.tenancy.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformTenantService {

    private final TenantRepository tenantRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UserRepository userRepository;
    private final OutletRepository outletRepository;
    private final JwtService jwtService;

    public PlatformTenantService(TenantRepository tenantRepository,
                                 SubscriptionRepository subscriptionRepository,
                                 PlanRepository planRepository,
                                 UserRepository userRepository,
                                 OutletRepository outletRepository,
                                 JwtService jwtService) {
        this.tenantRepository = tenantRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.outletRepository = outletRepository;
        this.jwtService = jwtService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TenantSummaryResponse> list(String search, TenantStatus status, Pageable pageable) {
        return PageResponse.of(
                tenantRepository.search(blankToNull(search), status, pageable),
                this::toSummary);
    }

    @Transactional(readOnly = true)
    public TenantDetailResponse get(UUID tenantId) {
        Tenant tenant = requireTenant(tenantId);
        return toDetail(tenant);
    }

    @Transactional
    public TenantDetailResponse suspend(UUID tenantId, SuspendTenantRequest request) {
        Tenant tenant = requireTenant(tenantId);
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "Suspended by platform admin"
                : request.reason().trim();
        tenant.suspend(reason);
        tenantRepository.save(tenant);

        subscriptionRepository.findByTenantId(tenantId).ifPresent(subscription -> {
            subscription.setStatus(com.possaas.subscription.domain.SubscriptionStatus.SUSPENDED);
            subscriptionRepository.save(subscription);
        });
        return toDetail(tenant);
    }

    @Transactional
    public TenantDetailResponse unsuspend(UUID tenantId) {
        Tenant tenant = requireTenant(tenantId);
        tenant.reinstate();
        tenantRepository.save(tenant);

        subscriptionRepository.findByTenantId(tenantId).ifPresent(subscription -> {
            if (subscription.getStatus()
                    == com.possaas.subscription.domain.SubscriptionStatus.SUSPENDED) {
                subscription.setStatus(com.possaas.subscription.domain.SubscriptionStatus.ACTIVE);
                subscription.setCancelledAt(null);
                subscriptionRepository.save(subscription);
            }
        });
        return toDetail(tenant);
    }

    /**
     * Issues a short-lived access token scoped to the tenant, with the platform admin
     * recorded as {@code impersonatorId} for audit trails.
     */
    @Transactional(readOnly = true)
    public ImpersonateResponse impersonate(UUID tenantId) {
        PosPrincipal admin = requirePlatformAdmin();
        Tenant tenant = requireTenant(tenantId);
        if (!tenant.canAuthenticate()) {
            throw new ApiException(ErrorCode.TENANT_SUSPENDED,
                    "Cannot impersonate a cancelled workspace");
        }

        AtomicReference<User> actingUser = new AtomicReference<>();
        AtomicReference<UUID> outletId = new AtomicReference<>();
        TenantContext.runAs(
                TenantContext.Scope.forTenant(tenant.getId(), tenant.getSlug(),
                        null, admin.userId(), admin.email()),
                () -> {
                    List<User> owners = userRepository.findActiveOwners(tenant.getId());
                    User user = owners.isEmpty()
                            ? userRepository.findActiveByTenantId(tenant.getId()).stream()
                            .findFirst()
                            .orElse(null)
                            : owners.getFirst();
                    actingUser.set(user);
                    outletId.set(outletRepository.findByDefaultOutletIsTrue()
                            .map(Outlet::getId)
                            .orElse(null));
                });

        User target = actingUser.get();
        if (target == null) {
            throw new ApiException(ErrorCode.NOT_FOUND,
                    "No active user available to impersonate in this workspace");
        }

        PosPrincipal impersonated = new PosPrincipal(
                target.getId(),
                target.getEmail(),
                target.getFullName(),
                tenant.getId(),
                tenant.getSlug(),
                outletId.get(),
                target.roleCodes(),
                target.permissionCodes(),
                false,
                admin.userId(),
                null,
                admin.deviceId());

        JwtService.IssuedAccessToken access = jwtService.issueAccessToken(impersonated);
        return new ImpersonateResponse(
                access.token(),
                access.expiresInSeconds(),
                access.expiresAt(),
                tenant.getId(),
                tenant.getSlug(),
                target.getId(),
                admin.userId());
    }

    private TenantSummaryResponse toSummary(Tenant tenant) {
        Optional<Subscription> subscription = subscriptionRepository.findByTenantId(tenant.getId());
        String planCode = subscription
                .flatMap(s -> planRepository.findById(s.getPlanId()))
                .map(Plan::getCode)
                .orElse(null);
        return new TenantSummaryResponse(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getBusinessName(),
                tenant.getContactEmail(),
                tenant.getStatus(),
                tenant.getDefaultCurrency(),
                tenant.getCreatedAt(),
                tenant.getSuspendedAt(),
                subscription.map(Subscription::getStatus).orElse(null),
                planCode);
    }

    private TenantDetailResponse toDetail(Tenant tenant) {
        Optional<Subscription> subscription = subscriptionRepository.findByTenantId(tenant.getId());
        Optional<Plan> plan = subscription.flatMap(s -> planRepository.findById(s.getPlanId()));
        return new TenantDetailResponse(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getBusinessName(),
                tenant.getLegalName(),
                tenant.getTaxIdentifier(),
                tenant.getStatus(),
                tenant.getDefaultCurrency(),
                tenant.getDefaultLocale(),
                tenant.getTimeZone(),
                tenant.getContactEmail(),
                tenant.getContactPhone(),
                tenant.getOnboardedAt(),
                tenant.getSuspendedAt(),
                tenant.getSuspensionReason(),
                tenant.getCreatedAt(),
                subscription.map(Subscription::getStatus).orElse(null),
                plan.map(Plan::getCode).orElse(null),
                plan.map(Plan::getName).orElse(null),
                subscription.map(Subscription::getTrialEndsAt).orElse(null),
                subscription.map(Subscription::getCurrentPeriodEnd).orElse(null),
                subscription.map(Subscription::getGracePeriodEndsAt).orElse(null));
    }

    private Tenant requireTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .filter(t -> t.getDeletedAt() == null)
                .orElseThrow(() -> ApiException.notFound("Tenant", tenantId));
    }

    private static PosPrincipal requirePlatformAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof PosPrincipal principal)
                || !principal.platformAdmin()) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED, "Platform admin required");
        }
        return principal;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
