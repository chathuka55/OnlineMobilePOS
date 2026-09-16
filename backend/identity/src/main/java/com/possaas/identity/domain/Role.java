package com.possaas.identity.domain;

import com.possaas.common.jpa.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A named bundle of permissions.
 *
 * <p>System roles ({@code tenantId == null}) are seeded once and shared by every tenant;
 * a shop may also define its own. Permissions are mapped as an element collection of
 * codes rather than as entities: they are a fixed catalogue, and this avoids a second
 * join on the hot authorisation path.
 */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission_code", nullable = false)
    private Set<String> permissionCodes = new LinkedHashSet<>();

    public static Role forTenant(UUID tenantId, String code, String name) {
        Role role = new Role();
        role.tenantId = tenantId;
        role.code = code;
        role.name = name;
        return role;
    }
}
