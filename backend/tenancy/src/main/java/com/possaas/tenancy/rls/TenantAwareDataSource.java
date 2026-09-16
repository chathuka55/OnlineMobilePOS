package com.possaas.tenancy.rls;

import com.possaas.common.tenant.TenantContext;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

/**
 * Binds every checked-out connection to the tenant of the current request.
 *
 * <h2>Why this works</h2>
 * The tenant is applied with {@code set_config('app.tenant_id', ?, true)}, the
 * parameterisable form of {@code SET LOCAL}. The {@code true} makes the setting
 * transaction-scoped, so PostgreSQL discards it at {@code COMMIT} or {@code ROLLBACK}
 * and a pooled connection can never carry one tenant's scope into another's request.
 *
 * <p>This depends on the pool handing out connections with autocommit disabled
 * ({@code spring.datasource.hikari.auto-commit: false}). Under autocommit the
 * {@code set_config} would be its own transaction and would be discarded immediately,
 * leaving every subsequent query unscoped — which RLS would then answer with zero rows.
 * {@link #assertManualCommit} fails fast rather than letting that misconfiguration turn
 * into a mysterious "all my data disappeared" bug report.
 *
 * <h2>Mid-transaction scope changes</h2>
 * Spring holds one connection for the life of a transaction, so {@code getConnection()}
 * is only called once. Scope is therefore re-applied immediately before each statement
 * is created, which is what lets {@link TenantContext#runAs} switch tenants (or bind one
 * for the first time) after the transaction has already started — as signup, login and
 * tenant provisioning all need to do.
 *
 * <h2>What happens with no tenant</h2>
 * An unauthenticated or platform-level request sets the empty string, which
 * {@code current_tenant_id()} maps to SQL NULL. Since {@code tenant_id = NULL} is never
 * true, tenant tables return nothing. Failing closed is the point.
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);

    private static final String APPLY_SCOPE_SQL = """
            SELECT set_config('app.tenant_id', ?, true),
                   set_config('app.user_id', ?, true)
            """;

    private static final Set<String> STATEMENT_FACTORY_METHODS = Set.of(
            "createStatement", "prepareStatement", "prepareCall");

    private boolean autoCommitChecked;

    public TenantAwareDataSource(DataSource delegate) {
        super(delegate);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return scopedProxy(super.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return scopedProxy(super.getConnection(username, password));
    }

    private Connection scopedProxy(Connection connection) throws SQLException {
        assertManualCommit(connection);
        try {
            applyScope(connection);
        } catch (SQLException ex) {
            // A connection we could not scope on checkout is more dangerous than none.
            connection.close();
            throw ex;
        }
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                new ScopeRebindingHandler(connection));
    }

    private void applyScope(Connection connection) throws SQLException {
        UUID tenantId = TenantContext.tenantIdOrNull();
        UUID userId = TenantContext.userIdOrNull();

        try (PreparedStatement statement = connection.prepareStatement(APPLY_SCOPE_SQL)) {
            statement.setString(1, tenantId == null ? "" : tenantId.toString());
            statement.setString(2, userId == null ? "" : userId.toString());
            statement.execute();
        }

        if (log.isTraceEnabled()) {
            log.trace("Bound connection to tenant {}", tenantId);
        }
    }

    private void assertManualCommit(Connection connection) throws SQLException {
        if (autoCommitChecked) {
            return;
        }
        autoCommitChecked = true;
        if (connection.getAutoCommit()) {
            throw new IllegalStateException(
                    "The connection pool is handing out autocommit connections. "
                            + "Transaction-scoped tenant binding cannot work, and every "
                            + "tenant-scoped query would silently return zero rows. "
                            + "Set spring.datasource.hikari.auto-commit=false.");
        }
    }

    private final class ScopeRebindingHandler implements InvocationHandler {

        private final Connection delegate;

        private ScopeRebindingHandler(Connection delegate) {
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (STATEMENT_FACTORY_METHODS.contains(method.getName())) {
                applyScope(delegate);
            }
            try {
                Object result = method.invoke(delegate, args);
                // Unwrap is commonly used by pools; keep identity consistent.
                if (result instanceof Statement && !(result instanceof Proxy)) {
                    return result;
                }
                return result;
            } catch (InvocationTargetException ex) {
                throw ex.getTargetException();
            }
        }
    }
}
