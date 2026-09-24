package com.possaas.common.jdbc;

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Binds query arguments in types pgjdbc can actually infer.
 *
 * <p>pgjdbc has no mapping for {@link Instant}: passing one straight to a
 * {@code JdbcTemplate} fails the whole statement with "Can't infer the SQL type to
 * use for an instance of java.time.Instant". It is a runtime failure, so nothing
 * catches it until the endpoint 500s - which is how the dashboard shipped broken
 * for every user while the reports, which convert, were fine. Route every JDBC
 * argument list through here instead of remembering the conversion per call site.
 */
public final class SqlArgs {

    private SqlArgs() {
    }

    /** Returns {@code args} with every {@link Instant} replaced by a {@link Timestamp}. */
    public static Object[] of(Object... args) {
        if (args == null) {
            return new Object[0];
        }
        Object[] converted = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            converted[i] = args[i] instanceof Instant instant ? Timestamp.from(instant) : args[i];
        }
        return converted;
    }
}
