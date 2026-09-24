package com.possaas.common.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SqlArgsTest {

    @Test
    void convertsInstantToTimestampPreservingTheInstant() {
        Instant moment = Instant.parse("2026-09-24T12:34:45.171Z");

        Object[] args = SqlArgs.of(moment);

        assertThat(args[0]).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) args[0]).toInstant()).isEqualTo(moment);
    }

    @Test
    void leavesOtherArgumentsAlone() {
        UUID tenantId = UUID.randomUUID();

        Object[] args = SqlArgs.of(tenantId, "VOIDED", 7, null);

        assertThat(args).containsExactly(tenantId, "VOIDED", 7, null);
    }

    @Test
    void convertsEveryInstantInAMixedList() {
        UUID tenantId = UUID.randomUUID();
        Instant from = Instant.parse("2026-09-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-01T00:00:00Z");

        Object[] args = SqlArgs.of(tenantId, from, to);

        assertThat(args[0]).isEqualTo(tenantId);
        assertThat(args[1]).isEqualTo(Timestamp.from(from));
        assertThat(args[2]).isEqualTo(Timestamp.from(to));
    }

    @Test
    void handlesNoArgumentsAndNullArray() {
        assertThat(SqlArgs.of()).isEmpty();
        assertThat(SqlArgs.of((Object[]) null)).isEmpty();
    }
}
