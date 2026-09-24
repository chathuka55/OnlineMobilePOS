package com.possaas.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.possaas.common.tenant.TenantContext;
import com.possaas.support.IntegrationTest;
import com.possaas.tenancy.domain.Outlet;
import com.possaas.tenancy.domain.Tenant;
import com.possaas.tenancy.repository.OutletRepository;
import com.possaas.tenancy.service.TenantProvisioningService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves tenant isolation is enforced by the database rather than by convention.
 *
 * <p>These are the tests that matter most in the whole suite. If they regress, one shop
 * can read another's sales.
 */
class RowLevelSecurityTest extends IntegrationTest {

    @Autowired
    private TenantProvisioningService provisioningService;

    @Autowired
    private OutletRepository outletRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static UUID alphaTenantId;
    private static UUID betaTenantId;

    @BeforeAll
    void provisionTwoTenants() {
        Tenant alpha = provisioningService.provision("Alpha Mobile", "owner@alpha.test", "alpha-mobile");
        Tenant beta = provisioningService.provision("Beta電子", "owner@beta.test", null);
        alphaTenantId = alpha.getId();
        betaTenantId = beta.getId();
    }

    @AfterEach
    void clearScope() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("a tenant sees only its own outlets")
    @Transactional(readOnly = true)
    void readsAreScopedToTheBoundTenant() {
        TenantContext.set(TenantContext.Scope.forTenant(
                alphaTenantId, "alpha-mobile", null, null, "owner@alpha.test"));

        List<Outlet> visible = outletRepository.findAll();

        assertThat(visible)
                .isNotEmpty()
                .allSatisfy(outlet -> assertThat(outlet.getTenantId()).isEqualTo(alphaTenantId));
    }

    @Test
    @DisplayName("fetching another tenant's row by primary key returns nothing")
    @Transactional(readOnly = true)
    void directPrimaryKeyLookupAcrossTenantsIsBlocked() {
        // Read Beta's outlet id while scoped to Beta.
        TenantContext.set(TenantContext.Scope.forTenant(
                betaTenantId, "beta", null, null, "owner@beta.test"));
        UUID targetId = outletRepository.findAll().getFirst().getId();
        assertThat(targetId).isNotNull();

        // Now ask for it as Alpha. A guessed or leaked id must still be useless.
        TenantContext.set(TenantContext.Scope.forTenant(
                alphaTenantId, "alpha-mobile", null, null, "owner@alpha.test"));

        assertThat(outletRepository.findById(targetId)).isEmpty();
    }

    @Test
    @DisplayName("an unscoped connection sees no tenant data at all")
    @Transactional(readOnly = true)
    void missingTenantScopeFailsClosed() {
        TenantContext.clear();

        // No tenant bound means current_tenant_id() is NULL, and `tenant_id = NULL` never
        // matches. Failing closed is the whole point: a forgotten scope leaks nothing.
        assertThat(outletRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("writing a row for another tenant is rejected by the policy")
    @Transactional
    void crossTenantWriteIsRejected() {
        TenantContext.set(TenantContext.Scope.forTenant(
                alphaTenantId, "alpha-mobile", null, null, "owner@alpha.test"));

        Outlet smuggled = Outlet.createDefault("Smuggled Branch");
        smuggled.setTenantId(betaTenantId);
        smuggled.setDefaultOutlet(false);
        smuggled.setCode("SMUG");

        // The WITH CHECK clause on the tenant_isolation policy rejects the insert.
        assertThatThrownBy(() -> {
            outletRepository.save(smuggled);
            outletRepository.flush();
        }).hasMessageContaining("row-level security");
    }

    @Test
    @DisplayName("the API database role cannot bypass Row-Level Security")
    void applicationRoleIsNotPrivileged() {
        // If the API ever connects as a superuser, BYPASSRLS holder, or the table owner,
        // every policy above becomes decorative. Assert that it does not.
        Boolean privileged = jdbcTemplate.queryForObject("""
                SELECT rolsuper OR rolbypassrls
                  FROM pg_roles
                 WHERE rolname = current_user
                """, Boolean.class);

        assertThat(privileged)
                .as("the runtime role must not be superuser or BYPASSRLS")
                .isFalse();

        String owner = jdbcTemplate.queryForObject("""
                SELECT pg_get_userbyid(relowner)
                  FROM pg_class
                 WHERE relname = 'outlets'
                """, String.class);
        String currentUser = jdbcTemplate.queryForObject("SELECT current_user", String.class);

        assertThat(currentUser)
                .as("the runtime role must not own the tables, since owners bypass RLS")
                .isNotEqualTo(owner);
    }

    @Test
    @DisplayName("every table with a tenant_id has an isolation policy")
    void allTenantTablesAreProtected() {
        List<String> unprotected = jdbcTemplate.queryForList("""
                SELECT c.relname
                  FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                  JOIN pg_attribute a ON a.attrelid = c.oid
                 WHERE n.nspname = 'public'
                   AND c.relkind = 'r'
                   AND a.attname = 'tenant_id'
                   AND a.attnum > 0
                   AND NOT a.attisdropped
                   AND NOT c.relrowsecurity
                   AND c.relname NOT IN (
                       'subscriptions', 'subscription_invoices', 'usage_counters',
                       'gateway_events', 'outbox_messages',
                       -- Both token tables are read before any tenant is known: the
                       -- token row is what says which tenant the caller belongs to,
                       -- so a policy keyed on current_tenant_id() would make sign-in
                       -- and invite acceptance impossible. V15 disables RLS on them
                       -- deliberately; see its header. one_time_tokens was missing
                       -- here, which failed this test against the real schema.
                       'refresh_tokens', 'one_time_tokens'
                   )
                 ORDER BY c.relname
                """, String.class);

        assertThat(unprotected)
                .as("a new table with tenant_id must enable RLS and add the tenant_isolation policy")
                .isEmpty();
    }
}
