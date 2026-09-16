package com.possaas.identity.service;

import com.possaas.common.api.PageResponse;
import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.tenant.TenantContext;
import com.possaas.identity.api.dto.AuthDtos.InviteUserRequest;
import com.possaas.identity.api.dto.AuthDtos.InviteUserResponse;
import com.possaas.identity.api.dto.AuthDtos.UpdateUserRequest;
import com.possaas.identity.api.dto.AuthDtos.UserResponse;
import com.possaas.identity.domain.Role;
import com.possaas.identity.domain.SystemRole;
import com.possaas.identity.domain.User;
import com.possaas.identity.domain.UserStatus;
import com.possaas.identity.repository.RoleRepository;
import com.possaas.identity.repository.UserRepository;
import com.possaas.identity.security.PosPrincipal;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.TenantRepository;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final AuthService authService;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       TenantRepository tenantRepository,
                       AuthService authService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tenantRepository = tenantRepository;
        this.authService = authService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search, UserStatus status, Pageable pageable) {
        PosPrincipal principal = AuthService.requirePrincipal();
        String tenantSlug = resolveTenantSlug(principal);

        return PageResponse.of(
                userRepository.search(blankToNull(search), status, pageable),
                user -> authService.toUserResponse(user, tenantSlug));
    }

    @Transactional
    public InviteUserResponse invite(InviteUserRequest request) {
        PosPrincipal principal = AuthService.requirePrincipal();
        UUID tenantId = TenantContext.requireTenantId();
        String tenantSlug = resolveTenantSlug(principal);

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw ApiException.conflict("A user with this email already exists");
        }

        Role role = resolveAssignableRole(request.roleCode(), tenantId);
        String fullName = request.fullName() == null || request.fullName().isBlank()
                ? email
                : request.fullName().trim();

        User user = User.forTenant(tenantId, email, fullName);
        user.setStatus(UserStatus.INVITED);
        user.getRoles().add(role);
        userRepository.save(user);

        AuthService.IssuedInvite invite = authService.issueInviteToken(user);
        return new InviteUserResponse(
                authService.toUserResponse(user, tenantSlug),
                invite.rawToken(),
                invite.expiresAt());
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request) {
        PosPrincipal principal = AuthService.requirePrincipal();
        String tenantSlug = resolveTenantSlug(principal);

        User user = requireTenantUser(id);
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.phone() != null) {
            user.setPhone(request.phone().isBlank() ? null : request.phone().trim());
        }
        if (request.roleCode() != null && !request.roleCode().isBlank()) {
            Role role = resolveAssignableRole(request.roleCode(), user.getTenantId());
            user.getRoles().clear();
            user.getRoles().add(role);
        }
        userRepository.save(user);
        return authService.toUserResponse(user, tenantSlug);
    }

    @Transactional
    public UserResponse disable(UUID id) {
        PosPrincipal principal = AuthService.requirePrincipal();
        String tenantSlug = resolveTenantSlug(principal);

        User user = requireTenantUser(id);
        if (user.getId().equals(principal.userId())) {
            throw ApiException.validation("You cannot disable your own account");
        }
        user.setStatus(UserStatus.DISABLED);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId(), "USER_DISABLED");
        return authService.toUserResponse(user, tenantSlug);
    }

    private User requireTenantUser(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("User", id));
    }

    private Role resolveAssignableRole(String roleCode, UUID tenantId) {
        String code = roleCode.trim().toUpperCase(Locale.ROOT);
        if (!SystemRole.ASSIGNABLE.contains(code)) {
            throw ApiException.validation("Role '" + roleCode + "' cannot be assigned");
        }
        return roleRepository.findByCodePreferringTenant(code, tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "Role '" + code + "' was not found"));
    }

    private String resolveTenantSlug(PosPrincipal principal) {
        if (principal.tenantSlug() != null) {
            return principal.tenantSlug();
        }
        if (principal.tenantId() == null) {
            return null;
        }
        return tenantRepository.findById(principal.tenantId())
                .map(Tenant::getSlug)
                .orElse(null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
