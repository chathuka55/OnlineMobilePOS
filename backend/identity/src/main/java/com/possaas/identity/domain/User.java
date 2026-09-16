package com.possaas.identity.domain;

import com.possaas.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A staff account, or a platform operator when {@code tenantId} is null.
 *
 * <p>Not a {@link com.possaas.common.jpa.TenantEntity} precisely because of that null:
 * platform users live in the same table and the RLS policy on {@code users} treats a null
 * tenant as the platform scope.
 *
 * <p>Passwords are Argon2id hashes. The desktop application compared plaintext in
 * {@code UserDAO.getUserByCredentials}, so no legacy hash can be carried over — every
 * migrated account must reset its password.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /** Null for platform operators. */
    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "email", nullable = false)
    private String email;

    @Column(name = "username")
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "avatar_object_key")
    private String avatarObjectKey;

    @Column(name = "is_platform_admin", nullable = false)
    private boolean platformAdmin;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status = UserStatus.INVITED;

    /** Hashed till-unlock PIN, replacing {@code passcode_config.properties}. */
    @Column(name = "pin_hash")
    private String pinHash;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    public static User forTenant(UUID tenantId, String email, String fullName) {
        User user = new User();
        user.tenantId = tenantId;
        user.email = email.toLowerCase();
        user.fullName = fullName;
        return user;
    }

    public static User platformOperator(String email, String fullName) {
        User user = new User();
        user.email = email.toLowerCase();
        user.fullName = fullName;
        user.platformAdmin = true;
        return user;
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }

    public boolean canAuthenticate() {
        return deletedAt == null
                && status == UserStatus.ACTIVE
                && passwordHash != null
                && !isLocked();
    }

    /** Flattens role grants into the permission codes used for authorisation checks. */
    public Set<String> permissionCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (Role role : roles) {
            codes.addAll(role.getPermissionCodes());
        }
        return codes;
    }

    public Set<String> roleCodes() {
        Set<String> codes = new LinkedHashSet<>();
        for (Role role : roles) {
            codes.add(role.getCode());
        }
        return codes;
    }

    public void recordSuccessfulLogin() {
        this.lastLoginAt = Instant.now();
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    /**
     * Applies progressive lockout. Ten consecutive failures buys a fifteen-minute
     * cooldown, which stops online password guessing without letting one angry customer
     * lock a shop's only till out for the day.
     */
    public void recordFailedLogin(int threshold, java.time.Duration lockDuration) {
        this.failedLoginCount = (short) Math.min(failedLoginCount + 1, Short.MAX_VALUE);
        if (failedLoginCount >= threshold) {
            this.lockedUntil = Instant.now().plus(lockDuration);
            this.failedLoginCount = 0;
        }
    }

    public void activateWithPassword(String hash) {
        this.passwordHash = hash;
        this.passwordChangedAt = Instant.now();
        this.status = UserStatus.ACTIVE;
    }
}
