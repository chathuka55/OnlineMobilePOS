package com.possaas.identity.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.tenant.TenantContext;
import com.possaas.identity.api.dto.AuthDtos.AcceptInviteRequest;
import com.possaas.identity.api.dto.AuthDtos.AccessTokenResponse;
import com.possaas.identity.api.dto.AuthDtos.BootstrapPlatformAdminRequest;
import com.possaas.identity.api.dto.AuthDtos.ChangePasswordRequest;
import com.possaas.identity.api.dto.AuthDtos.ForgotPasswordRequest;
import com.possaas.identity.api.dto.AuthDtos.LoginRequest;
import com.possaas.identity.api.dto.AuthDtos.LogoutRequest;
import com.possaas.identity.api.dto.AuthDtos.MessageResponse;
import com.possaas.identity.api.dto.AuthDtos.RefreshRequest;
import com.possaas.identity.api.dto.AuthDtos.ResetPasswordRequest;
import com.possaas.identity.api.dto.AuthDtos.SetPinRequest;
import com.possaas.identity.api.dto.AuthDtos.SignupRequest;
import com.possaas.identity.api.dto.AuthDtos.StepUpRequest;
import com.possaas.identity.api.dto.AuthDtos.TokenResponse;
import com.possaas.identity.api.dto.AuthDtos.UserResponse;
import com.possaas.identity.api.dto.AuthDtos.VerifyPinRequest;
import com.possaas.identity.domain.OneTimeToken;
import com.possaas.identity.domain.Role;
import com.possaas.identity.domain.SystemRole;
import com.possaas.identity.domain.User;
import com.possaas.identity.domain.UserStatus;
import com.possaas.identity.repository.OneTimeTokenRepository;
import com.possaas.identity.repository.RoleRepository;
import com.possaas.identity.repository.UserRepository;
import com.possaas.identity.security.JwtService;
import com.possaas.identity.security.PosPrincipal;
import com.possaas.identity.security.SecurityProperties;
import com.possaas.subscription.service.SubscriptionService;
import com.possaas.tenancy.domain.Outlet;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.OutletRepository;
import com.possaas.tenancy.repository.TenantRepository;
import com.possaas.tenancy.service.TenantProvisioningService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    private static final Duration PASSWORD_RESET_TTL = Duration.ofHours(1);
    private static final Duration INVITE_TTL = Duration.ofDays(7);
    private static final int ONE_TIME_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final OneTimeTokenRepository oneTimeTokenRepository;
    private final TenantRepository tenantRepository;
    private final OutletRepository outletRepository;
    private final TenantProvisioningService tenantProvisioningService;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final SubscriptionService subscriptionService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       OneTimeTokenRepository oneTimeTokenRepository,
                       TenantRepository tenantRepository,
                       OutletRepository outletRepository,
                       TenantProvisioningService tenantProvisioningService,
                       RefreshTokenService refreshTokenService,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       SecurityProperties securityProperties,
                       SubscriptionService subscriptionService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.oneTimeTokenRepository = oneTimeTokenRepository;
        this.tenantRepository = tenantRepository;
        this.outletRepository = outletRepository;
        this.tenantProvisioningService = tenantProvisioningService;
        this.refreshTokenService = refreshTokenService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
        this.subscriptionService = subscriptionService;
    }

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.contactEmail());
        Tenant tenant = tenantProvisioningService.provision(
                request.businessName().trim(), email, request.slug());

        AtomicReference<User> created = new AtomicReference<>();
        TenantContext.runAs(
                TenantContext.Scope.forTenant(tenant.getId(), tenant.getSlug(), null, null, email),
                () -> created.set(createOwner(tenant, request, email)));

        subscriptionService.startTrial(tenant.getId());

        User owner = created.get();
        return issueSession(owner, tenant, null,
                new RefreshTokenService.DeviceInfo(null, "Owner signup", null));
    }

    private User createOwner(Tenant tenant, SignupRequest request, String email) {
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw ApiException.conflict("An account with this email already exists in the workspace");
        }

        Role ownerRole = roleRepository.findByCodeAndTenantIdIsNull(SystemRole.OWNER)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "System role OWNER is not seeded"));

        User user = User.forTenant(tenant.getId(), email, request.fullName().trim());
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
            tenant.setContactPhone(request.phone().trim());
            tenantRepository.save(tenant);
        }
        user.activateWithPassword(passwordEncoder.encode(request.password()));
        user.getRoles().add(ownerRole);
        // saveAndFlush: see the comment on persistUser - this must execute while
        // the caller's runAs scope is still bound, not after it unwinds.
        return userRepository.saveAndFlush(user);
    }

    /** Shop portal sign-in. A shop code is mandatory, so platform operators can never authenticate here. */
    @Transactional
    public TokenResponse login(LoginRequest request, String userAgent) {
        String identity = request.emailOrUsername().trim();
        String slug = blankToNull(request.tenantSlug());
        if (slug == null) {
            throw ApiException.validation("Shop code is required to sign in");
        }

        Tenant tenant = requireAuthenticatableTenant(slug);
        AtomicReference<User> found = new AtomicReference<>();
        TenantContext.runAs(
                TenantContext.Scope.forTenant(tenant.getId(), tenant.getSlug(), null, null, null),
                () -> found.set(findUserForLogin(identity).orElse(null)));
        return completeLogin(found.get(), tenant, request, userAgent);
    }

    /** Service-provider portal sign-in. Only platform operators (no tenant) are visible and accepted. */
    @Transactional
    public TokenResponse platformLogin(LoginRequest request, String userAgent) {
        User user = findUserForLogin(request.emailOrUsername().trim()).orElse(null);
        if (user != null && !user.isPlatformAdmin()) {
            user = null;
        }
        return completeLogin(user, null, request, userAgent);
    }

    /** Public, non-sensitive branding for a shop's own sign-in page. */
    @Transactional(readOnly = true)
    public PublicShopInfo publicShopInfo(String slug) {
        Tenant tenant = tenantRepository.findBySlugIgnoreCaseAndDeletedAtIsNull(slug)
                .filter(Tenant::canAuthenticate)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND, "Shop not found"));
        return new PublicShopInfo(tenant.getSlug(), tenant.getBusinessName());
    }

    public record PublicShopInfo(String slug, String businessName) {
    }

    private TokenResponse completeLogin(User user, Tenant tenant, LoginRequest request, String userAgent) {
        if (user == null) {
            throw invalidCredentials();
        }

        assertNotDisabled(user);
        if (user.isLocked() || user.getStatus() == UserStatus.LOCKED) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    "This account is temporarily locked. Try again later.");
        }

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            recordFailedLogin(user, tenant);
            throw invalidCredentials();
        }

        if (!user.canAuthenticate()) {
            throw invalidCredentials();
        }

        if (tenant != null && !tenant.canAuthenticate()) {
            throw new ApiException(ErrorCode.TENANT_SUSPENDED,
                    "This workspace is not available for sign-in");
        }

        user.recordSuccessfulLogin();
        persistUser(user, tenant);

        RefreshTokenService.DeviceInfo device = new RefreshTokenService.DeviceInfo(
                blankToNull(request.deviceId()),
                blankToNull(request.deviceLabel()),
                truncate(userAgent, 320));

        return issueSession(user, tenant, blankToNull(request.deviceId()), device);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(request.refreshToken(), null);

        AtomicReference<User> userRef = new AtomicReference<>();
        AtomicReference<Tenant> tenantRef = new AtomicReference<>();

        if (rotated.tenantId() != null) {
            Tenant tenant = tenantRepository.findById(rotated.tenantId())
                    .filter(t -> t.getDeletedAt() == null)
                    .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND,
                            "Workspace for this session no longer exists"));
            tenantRef.set(tenant);
            TenantContext.runAs(
                    TenantContext.Scope.forTenant(tenant.getId(), tenant.getSlug(), null, null, null),
                    () -> userRef.set(requireUser(rotated.userId())));
        } else {
            userRef.set(requireUser(rotated.userId()));
        }

        User user = userRef.get();
        assertNotDisabled(user);
        if (!user.canAuthenticate()) {
            refreshTokenService.revokeAllForUser(user.getId(), "USER_INACTIVE");
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account is no longer active");
        }

        return toTokenResponse(user, tenantRef.get(), rotated.token());
    }

    @Transactional
    public void logout(LogoutRequest request) {
        if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
            refreshTokenService.revoke(request.refreshToken(), "LOGOUT");
            return;
        }
        currentPrincipalOptional().ifPresent(principal ->
                refreshTokenService.revokeAllForUser(principal.userId(), "LOGOUT"));
    }

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.email());
        String slug = blankToNull(request.tenantSlug());

        Optional<User> user = Optional.empty();
        if (slug != null) {
            Tenant tenant = tenantRepository.findBySlugIgnoreCaseAndDeletedAtIsNull(slug).orElse(null);
            if (tenant != null) {
                AtomicReference<Optional<User>> found = new AtomicReference<>(Optional.empty());
                TenantContext.runAs(
                        TenantContext.Scope.forTenant(tenant.getId(), tenant.getSlug(), null, null, null),
                        () -> found.set(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)));
                user = found.get();
            }
        } else {
            user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                    .filter(User::isPlatformAdmin);
        }

        user.filter(u -> u.getStatus() != UserStatus.DISABLED && u.getDeletedAt() == null)
                .ifPresent(this::issuePasswordResetToken);

        // Same response whether or not the account exists — do not leak membership.
        return new MessageResponse(
                "If an account exists for that email, password reset instructions have been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        OneTimeToken token = requireUsableToken(request.token(), OneTimeToken.Purpose.PASSWORD_RESET);

        AtomicReference<User> userRef = new AtomicReference<>();
        runWithTokenTenant(token, () -> {
            User user = requireUser(token.getUserId());
            assertNotDisabled(user);
            user.activateWithPassword(passwordEncoder.encode(request.newPassword()));
            userRepository.save(user);
            userRef.set(user);
        });

        token.consume();
        oneTimeTokenRepository.save(token);
        refreshTokenService.revokeAllForUser(userRef.get().getId(), "PASSWORD_RESET");
        return new MessageResponse("Your password has been updated. You can sign in now.");
    }

    @Transactional
    public TokenResponse acceptInvite(AcceptInviteRequest request) {
        OneTimeToken token = requireUsableToken(request.token(), OneTimeToken.Purpose.INVITE);

        AtomicReference<User> userRef = new AtomicReference<>();
        AtomicReference<Tenant> tenantRef = new AtomicReference<>();

        runWithTokenTenant(token, () -> {
            User user = requireUser(token.getUserId());
            if (user.getStatus() == UserStatus.DISABLED) {
                throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This invitation is no longer valid");
            }
            if (request.fullName() != null && !request.fullName().isBlank()) {
                user.setFullName(request.fullName().trim());
            }
            user.activateWithPassword(passwordEncoder.encode(request.password()));
            userRepository.save(user);
            userRef.set(user);

            if (token.getTenantId() != null) {
                tenantRef.set(tenantRepository.findById(token.getTenantId())
                        .filter(t -> t.getDeletedAt() == null)
                        .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND,
                                "Workspace for this invitation no longer exists")));
            }
        });

        token.consume();
        oneTimeTokenRepository.save(token);

        User user = userRef.get();
        return issueSession(user, tenantRef.get(), null,
                new RefreshTokenService.DeviceInfo(null, "Invite accepted", null));
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        PosPrincipal principal = requirePrincipal();
        AtomicReference<User> userRef = new AtomicReference<>();
        if (principal.tenantId() != null) {
            TenantContext.runAs(principal.toScope(),
                    () -> userRef.set(requireUser(principal.userId())));
        } else {
            userRef.set(requireUser(principal.userId()));
        }
        return toUserResponse(userRef.get(), principal.tenantSlug());
    }

    @Transactional
    public AccessTokenResponse stepUp(StepUpRequest request) {
        PosPrincipal principal = requirePrincipal();
        AtomicReference<User> userRef = new AtomicReference<>();
        if (principal.tenantId() != null) {
            TenantContext.runAs(principal.toScope(),
                    () -> userRef.set(requireUser(principal.userId())));
        } else {
            userRef.set(requireUser(principal.userId()));
        }

        User user = userRef.get();
        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        PosPrincipal steppedUp = new PosPrincipal(
                principal.userId(),
                principal.email(),
                principal.fullName(),
                principal.tenantId(),
                principal.tenantSlug(),
                principal.outletId(),
                principal.roles(),
                principal.permissions(),
                principal.platformAdmin(),
                principal.impersonatorId(),
                Instant.now(),
                principal.deviceId());

        JwtService.IssuedAccessToken access = jwtService.issueAccessToken(steppedUp);
        return new AccessTokenResponse(access.token(), access.expiresInSeconds(), access.expiresAt());
    }

    /**
     * Creates the first (and only) platform operator account, for whoever runs this
     * deployment to administer tenants - suspend/reinstate a shop, view platform-wide
     * metrics. Guarded by "no platform admin exists yet" rather than a permission
     * check, since bootstrapping is exactly the problem: there is nobody with
     * platform.tenant.manage to invite the first one.
     */
    @Transactional
    public TokenResponse bootstrapPlatformAdmin(BootstrapPlatformAdminRequest request) {
        if (userRepository.existsByPlatformAdminTrue()) {
            throw ApiException.conflict("A platform administrator already exists");
        }
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }

        Role adminRole = roleRepository.findByCodeAndTenantIdIsNull(SystemRole.PLATFORM_ADMIN)
                .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR,
                        "System role PLATFORM_ADMIN is not seeded"));

        User user = User.platformOperator(email, request.fullName().trim());
        user.activateWithPassword(passwordEncoder.encode(request.password()));
        user.getRoles().add(adminRole);
        user = userRepository.save(user);

        return issueSession(user, null, null,
                new RefreshTokenService.DeviceInfo(null, "Platform admin bootstrap", null));
    }

    @Transactional
    public MessageResponse changePassword(ChangePasswordRequest request) {
        PosPrincipal principal = requirePrincipal();
        User user = loadPrincipalUser(principal);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        persistUser(user, null);
        refreshTokenService.revokeAllForUser(user.getId(), "PASSWORD_CHANGED");
        return new MessageResponse("Password changed");
    }

    /**
     * Sets or replaces the current user's till-unlock PIN. Requires the account password,
     * the same as {@link #stepUp}, so a PIN can't be silently swapped from a hijacked but
     * still-live session.
     */
    @Transactional
    public MessageResponse setPin(SetPinRequest request) {
        PosPrincipal principal = requirePrincipal();
        User user = loadPrincipalUser(principal);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        boolean clearing = request.pin() == null || request.pin().isBlank();
        user.setPinHash(clearing ? null : passwordEncoder.encode(request.pin()));
        persistUser(user, null);
        return new MessageResponse(clearing ? "PIN removed" : "PIN updated");
    }

    /**
     * Verifies the till-unlock PIN for the already-authenticated caller. This is a
     * second factor for resuming a locked screen, not a login: the caller must already
     * hold a valid access token, so a wrong PIN never grants anything by itself.
     */
    @Transactional(readOnly = true)
    public MessageResponse verifyPin(VerifyPinRequest request) {
        PosPrincipal principal = requirePrincipal();
        User user = loadPrincipalUser(principal);

        if (user.getPinHash() == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "No PIN has been set for this account");
        }
        if (!passwordEncoder.matches(request.pin(), user.getPinHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS, "Incorrect PIN");
        }
        return new MessageResponse("PIN verified");
    }

    private User loadPrincipalUser(PosPrincipal principal) {
        AtomicReference<User> userRef = new AtomicReference<>();
        if (principal.tenantId() != null) {
            TenantContext.runAs(principal.toScope(), () -> userRef.set(requireUser(principal.userId())));
        } else {
            userRef.set(requireUser(principal.userId()));
        }
        return userRef.get();
    }

    // --- token issuance helpers ---------------------------------------------------

    public IssuedInvite issueInviteToken(User user) {
        oneTimeTokenRepository.invalidateOutstanding(
                user.getId(), OneTimeToken.Purpose.INVITE, Instant.now());
        String raw = generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(INVITE_TTL);
        OneTimeToken token = OneTimeToken.create(
                user.getId(), user.getTenantId(), OneTimeToken.Purpose.INVITE,
                jwtService.hashRefreshToken(raw), expiresAt);
        oneTimeTokenRepository.save(token);
        return new IssuedInvite(raw, expiresAt);
    }

    private void issuePasswordResetToken(User user) {
        oneTimeTokenRepository.invalidateOutstanding(
                user.getId(), OneTimeToken.Purpose.PASSWORD_RESET, Instant.now());
        String raw = generateOpaqueToken();
        Instant expiresAt = Instant.now().plus(PASSWORD_RESET_TTL);
        OneTimeToken token = OneTimeToken.create(
                user.getId(), user.getTenantId(), OneTimeToken.Purpose.PASSWORD_RESET,
                jwtService.hashRefreshToken(raw), expiresAt);
        oneTimeTokenRepository.save(token);
        // Email delivery is not wired yet; log so local/dev flows remain usable.
        log.info("Password reset token issued for user {} (expires {})", user.getId(), expiresAt);
        log.debug("Password reset raw token for {}: {}", user.getEmail(), raw);
    }

    private TokenResponse issueSession(User user, Tenant tenant, String deviceId,
                                       RefreshTokenService.DeviceInfo device) {
        UUID tenantId = tenant == null ? user.getTenantId() : tenant.getId();
        String tenantSlug = tenant == null ? null : tenant.getSlug();
        UUID outletId = null;

        if (tenantId != null) {
            Tenant resolved = tenant != null ? tenant : tenantRepository.findById(tenantId)
                    .orElseThrow(() -> ApiException.notFound("Tenant", tenantId));
            tenantSlug = resolved.getSlug();

            AtomicReference<UUID> outletRef = new AtomicReference<>();
            TenantContext.runAs(
                    TenantContext.Scope.forTenant(tenantId, tenantSlug, null, user.getId(), user.getEmail()),
                    () -> outletRef.set(outletRepository.findByDefaultOutletIsTrue()
                            .map(Outlet::getId)
                            .orElse(null)));
            outletId = outletRef.get();
        }

        PosPrincipal principal = new PosPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                tenantId,
                tenantSlug,
                outletId,
                user.roleCodes(),
                user.permissionCodes(),
                user.isPlatformAdmin(),
                null,
                null,
                deviceId);

        JwtService.IssuedAccessToken access = jwtService.issueAccessToken(principal);
        RefreshTokenService.Issued refresh = refreshTokenService.issueNewFamily(
                user.getId(), tenantId, device);

        return new TokenResponse(
                access.token(),
                refresh.rawToken(),
                access.expiresInSeconds(),
                access.expiresAt(),
                toUserResponse(user, tenantSlug));
    }

    private TokenResponse toTokenResponse(User user, Tenant tenant, RefreshTokenService.Issued refresh) {
        UUID tenantId = tenant == null ? user.getTenantId() : tenant.getId();
        String tenantSlug = tenant == null ? null : tenant.getSlug();
        UUID outletId = null;

        if (tenantId != null) {
            String slug = tenantSlug;
            if (slug == null) {
                slug = tenantRepository.findById(tenantId).map(Tenant::getSlug).orElse(null);
                tenantSlug = slug;
            }
            AtomicReference<UUID> outletRef = new AtomicReference<>();
            String boundSlug = slug;
            TenantContext.runAs(
                    TenantContext.Scope.forTenant(tenantId, boundSlug, null, user.getId(), user.getEmail()),
                    () -> outletRef.set(outletRepository.findByDefaultOutletIsTrue()
                            .map(Outlet::getId)
                            .orElse(null)));
            outletId = outletRef.get();
        }

        PosPrincipal principal = new PosPrincipal(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                tenantId,
                tenantSlug,
                outletId,
                user.roleCodes(),
                user.permissionCodes(),
                user.isPlatformAdmin(),
                null,
                null,
                null);

        JwtService.IssuedAccessToken access = jwtService.issueAccessToken(principal);
        return new TokenResponse(
                access.token(),
                refresh.rawToken(),
                access.expiresInSeconds(),
                access.expiresAt(),
                toUserResponse(user, tenantSlug));
    }

    public UserResponse toUserResponse(User user, String tenantSlug) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getTenantId(),
                tenantSlug,
                user.roleCodes(),
                user.permissionCodes(),
                user.isPlatformAdmin(),
                user.getPinHash() != null);
    }

    // --- lookups / guards ---------------------------------------------------------

    private Optional<User> findUserForLogin(String identity) {
        Optional<User> byEmail = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(identity);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return userRepository.findByUsernameIgnoreCaseAndDeletedAtIsNull(identity);
    }

    private Tenant requireAuthenticatableTenant(String slug) {
        Tenant tenant = tenantRepository.findBySlugIgnoreCaseAndDeletedAtIsNull(slug)
                .orElseThrow(() -> new ApiException(ErrorCode.TENANT_NOT_FOUND,
                        "No workspace found at '" + slug + "'"));
        if (!tenant.canAuthenticate()) {
            throw new ApiException(ErrorCode.TENANT_SUSPENDED,
                    "This workspace is not available for sign-in");
        }
        return tenant;
    }

    private User requireUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("User", id));
    }

    private OneTimeToken requireUsableToken(String rawToken, OneTimeToken.Purpose purpose) {
        String hash = jwtService.hashRefreshToken(rawToken);
        OneTimeToken token = oneTimeTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(ErrorCode.TOKEN_INVALID,
                        "This link is invalid or has already been used"));
        if (token.getPurpose() != purpose || !token.isUsable()) {
            throw new ApiException(ErrorCode.TOKEN_INVALID,
                    "This link is invalid or has expired");
        }
        return token;
    }

    private void runWithTokenTenant(OneTimeToken token, Runnable action) {
        if (token.getTenantId() != null) {
            String slug = tenantRepository.findById(token.getTenantId())
                    .map(Tenant::getSlug)
                    .orElse(null);
            TenantContext.runAs(
                    TenantContext.Scope.forTenant(token.getTenantId(), slug, null, null, null),
                    action);
        } else {
            action.run();
        }
    }

    private void recordFailedLogin(User user, Tenant tenant) {
        int threshold = securityProperties.rateLimit().loginAttempts();
        user.recordFailedLogin(threshold, LOCKOUT_DURATION);
        persistUser(user, tenant);
    }

    private void persistUser(User user, Tenant tenant) {
        // saveAndFlush, not save: the RLS scope below is bound only for the
        // duration of this call, but Hibernate defers a dirty-checked UPDATE to
        // the transaction's commit-time flush by default. If that flush happened
        // after runAs restored the outer (unscoped) TenantContext, the UPDATE
        // would run with no app.tenant_id, RLS would block it, and Hibernate
        // would misreport the resulting zero-rows-affected as an optimistic
        // lock conflict. Flushing here keeps the statement inside the scope.
        if (tenant != null) {
            TenantContext.runAs(
                    TenantContext.Scope.forTenant(tenant.getId(), tenant.getSlug(),
                            null, null, null),
                    () -> userRepository.saveAndFlush(user));
        } else if (user.getTenantId() != null) {
            TenantContext.runAs(
                    TenantContext.Scope.forTenant(user.getTenantId(), null, null, null, null),
                    () -> userRepository.saveAndFlush(user));
        } else {
            userRepository.saveAndFlush(user);
        }
    }

    private static void assertNotDisabled(User user) {
        if (user.getStatus() == UserStatus.DISABLED || user.getDeletedAt() != null) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED, "This account has been disabled");
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }

    static PosPrincipal requirePrincipal() {
        return currentPrincipalOptional()
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED,
                        "Please sign in to continue"));
    }

    private static Optional<PosPrincipal> currentPrincipalOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof PosPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[ONE_TIME_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record IssuedInvite(String rawToken, Instant expiresAt) {
    }
}
