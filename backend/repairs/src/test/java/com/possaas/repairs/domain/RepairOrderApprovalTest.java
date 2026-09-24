package com.possaas.repairs.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RepairOrderApprovalTest {

    private static RepairOrder orderWithTotal(String total) {
        RepairOrder order = new RepairOrder();
        order.setGrandTotal(new BigDecimal(total));
        return order;
    }

    @Test
    void noEstimateMeansNothingToExceed() {
        RepairOrder order = orderWithTotal("25000.00");
        assertThat(order.approvalCeiling()).isNull();
        assertThat(order.exceedsApprovedAmount()).isFalse();
    }

    @Test
    void withinEstimateIsFine() {
        RepairOrder order = orderWithTotal("8000.00");
        order.setEstimatedCost(new BigDecimal("10000.00"));
        assertThat(order.exceedsApprovedAmount()).isFalse();
    }

    @Test
    void exactlyOnEstimateIsFine() {
        RepairOrder order = orderWithTotal("10000.00");
        order.setEstimatedCost(new BigDecimal("10000.00"));
        assertThat(order.exceedsApprovedAmount()).isFalse();
    }

    @Test
    void overEstimateNeedsApproval() {
        RepairOrder order = orderWithTotal("14500.00");
        order.setEstimatedCost(new BigDecimal("10000.00"));
        assertThat(order.exceedsApprovedAmount()).isTrue();
    }

    @Test
    void approvalRaisesTheCeiling() {
        RepairOrder order = orderWithTotal("14500.00");
        order.setEstimatedCost(new BigDecimal("10000.00"));
        order.recordApproval(new BigDecimal("15000.00"), UUID.randomUUID());

        assertThat(order.approvalCeiling()).isEqualByComparingTo("15000.00");
        assertThat(order.exceedsApprovedAmount()).isFalse();
        assertThat(order.getApprovedAt()).isNotNull();
    }

    @Test
    void jobCanOutgrowEvenItsApprovedAmount() {
        RepairOrder order = orderWithTotal("21000.00");
        order.setEstimatedCost(new BigDecimal("10000.00"));
        order.recordApproval(new BigDecimal("15000.00"), UUID.randomUUID());

        assertThat(order.exceedsApprovedAmount()).isTrue();
    }

    @Test
    void scaleDifferencesDoNotCountAsExceeding() {
        // 10000.00 vs 10000 must compare equal, not "greater".
        RepairOrder order = orderWithTotal("10000.00");
        order.setEstimatedCost(new BigDecimal("10000"));
        assertThat(order.exceedsApprovedAmount()).isFalse();
    }

    @Test
    void partsAreOnlyDeductedOnceConsumptionIsRecorded() {
        RepairOrder order = orderWithTotal("5000.00");
        assertThat(order.partsWereDeducted()).isFalse();

        order.setPartsConsumedAt(Instant.now());
        assertThat(order.partsWereDeducted()).isTrue();
    }
}
