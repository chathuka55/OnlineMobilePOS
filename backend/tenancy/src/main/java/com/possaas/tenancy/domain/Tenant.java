package com.possaas.tenancy.domain;

import com.possaas.common.jpa.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A subscribing business. The root of the tenant isolation tree.
 *
 * <p>This table is intentionally not under Row-Level Security: the login flow has to
 * resolve a slug to a tenant before any tenant scope exists.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
public class Tenant extends BaseEntity {

    @Column(name = "slug", nullable = false)
    private String slug;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    @Column(name = "legal_name")
    private String legalName;

    @Column(name = "tax_identifier")
    private String taxIdentifier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TenantStatus status = TenantStatus.TRIAL;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "LKR";

    @Column(name = "default_locale", nullable = false)
    private String defaultLocale = "en-LK";

    @Column(name = "time_zone", nullable = false)
    private String timeZone = "Asia/Colombo";

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "onboarded_at")
    private Instant onboardedAt;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason")
    private String suspensionReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Tenant create(String slug, String businessName, String contactEmail) {
        Tenant tenant = new Tenant();
        tenant.slug = slug;
        tenant.businessName = businessName;
        tenant.contactEmail = contactEmail;
        tenant.status = TenantStatus.TRIAL;
        return tenant;
    }

    /** Whether staff may sign in at all. */
    public boolean canAuthenticate() {
        return deletedAt == null && status != TenantStatus.CANCELLED;
    }

    /**
     * Whether the tenant may still write. A lapsed subscription degrades to read-only
     * rather than locking the shop out of its own history.
     */
    public boolean canWrite() {
        return deletedAt == null
                && (status == TenantStatus.TRIAL || status == TenantStatus.ACTIVE);
    }

    public void suspend(String reason) {
        this.status = TenantStatus.SUSPENDED;
        this.suspendedAt = Instant.now();
        this.suspensionReason = reason;
    }

    public void reinstate() {
        this.status = TenantStatus.ACTIVE;
        this.suspendedAt = null;
        this.suspensionReason = null;
    }
}
