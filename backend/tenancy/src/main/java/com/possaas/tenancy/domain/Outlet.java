package com.possaas.tenancy.domain;

import com.possaas.common.jpa.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A physical shop or branch. Version 1 provisions exactly one per tenant, but every
 * transactional table carries {@code outlet_id} so multi-branch needs no migration.
 *
 * <p>These fields are what the desktop app kept in {@code ShopDetails} and printed at the
 * head of every invoice and thermal receipt.
 */
@Entity
@Table(name = "outlets")
@Getter
@Setter
@NoArgsConstructor
public class Outlet extends TenantEntity {

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultOutlet;

    @Column(name = "address_line1")
    private String addressLine1;

    @Column(name = "address_line2")
    private String addressLine2;

    @Column(name = "city")
    private String city;

    @Column(name = "phone_primary")
    private String phonePrimary;

    @Column(name = "phone_secondary")
    private String phoneSecondary;

    @Column(name = "email")
    private String email;

    @Column(name = "website")
    private String website;

    /** Object-storage key; the logo itself lives in S3/MinIO, not in the database. */
    @Column(name = "logo_object_key")
    private String logoObjectKey;

    @Column(name = "receipt_footer")
    private String receiptFooter;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public static Outlet createDefault(String name) {
        Outlet outlet = new Outlet();
        outlet.code = "MAIN";
        outlet.name = name;
        outlet.defaultOutlet = true;
        return outlet;
    }

    /** Address formatted for a receipt header, skipping blank lines. */
    public String formattedAddress() {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, addressLine1);
        appendLine(builder, addressLine2);
        appendLine(builder, city);
        return builder.toString().trim();
    }

    private static void appendLine(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(value.trim()).append('\n');
        }
    }
}
