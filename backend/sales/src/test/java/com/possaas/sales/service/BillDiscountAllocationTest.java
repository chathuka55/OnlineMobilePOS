package com.possaas.sales.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.possaas.common.money.Money;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class BillDiscountAllocationTest {

    private static final BigDecimal VAT = new BigDecimal("18");

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        BigDecimal total = Money.ZERO;
        for (BigDecimal v : values) {
            total = Money.add(total, v);
        }
        return total;
    }

    /**
     * Worked example 1 from the design research: a VAT-inclusive retail bill with a
     * line discount and a Rs 5,000 bill discount spread pro rata.
     */
    @Test
    void reproducesTheWorkedExample() {
        List<BillDiscountAllocation.LineInput> lines = List.of(
                new BillDiscountAllocation.LineInput(bd("285000.00"), VAT, true), // iPhone
                new BillDiscountAllocation.LineInput(bd("4500.00"), VAT, true),   // 2 cases, 10% off
                new BillDiscountAllocation.LineInput(bd("1500.00"), VAT, true));  // glass

        List<BillDiscountAllocation.LineResult> results =
                BillDiscountAllocation.allocate(lines, bd("5000.00"));

        // Shares quoted in the research: 4,896.91 / 77.32 / 25.77
        assertThat(results.get(0).allocatedDiscount()).isEqualByComparingTo("4896.91");
        assertThat(results.get(1).allocatedDiscount()).isEqualByComparingTo("77.32");
        assertThat(results.get(2).allocatedDiscount()).isEqualByComparingTo("25.77");

        assertThat(results.get(0).netAfterDiscount()).isEqualByComparingTo("280103.09");
        assertThat(results.get(1).netAfterDiscount()).isEqualByComparingTo("4422.68");
        assertThat(results.get(2).netAfterDiscount()).isEqualByComparingTo("1474.23");

        // The allocation accounts for the whole discount, to the cent.
        assertThat(sum(results.stream().map(
                BillDiscountAllocation.LineResult::allocatedDiscount).toList()))
                .isEqualByComparingTo("5000.00");

        // And the lines still add to the bill total.
        assertThat(sum(results.stream().map(
                BillDiscountAllocation.LineResult::lineTotal).toList()))
                .isEqualByComparingTo("286000.00");

        // The iPhone's own VAT, as quoted in the research.
        assertThat(results.get(0).taxAmount()).isEqualByComparingTo("42727.59");
    }

    @Test
    void lineTaxesReconcileToTheInvoiceVat() {
        List<BillDiscountAllocation.LineInput> lines = List.of(
                new BillDiscountAllocation.LineInput(bd("285000.00"), VAT, true),
                new BillDiscountAllocation.LineInput(bd("4500.00"), VAT, true),
                new BillDiscountAllocation.LineInput(bd("1500.00"), VAT, true));

        List<BillDiscountAllocation.LineResult> results =
                BillDiscountAllocation.allocate(lines, bd("5000.00"));

        // VAT contained in 286,000 at 18/118, per the research.
        BigDecimal invoiceVat = Money.taxFromInclusive(bd("286000.00"), VAT);
        assertThat(invoiceVat).isEqualByComparingTo("43627.12");

        List<BigDecimal> reconciled = BillDiscountAllocation.reconcileTax(
                results.stream().map(BillDiscountAllocation.LineResult::taxAmount).toList(),
                results.stream().map(BillDiscountAllocation.LineResult::lineTotal).toList(),
                invoiceVat);

        assertThat(sum(reconciled)).isEqualByComparingTo(invoiceVat);
    }

    /** Worked example 2: VAT-exclusive wholesale. 10 × 1,000 + 18% = 11,800. */
    @Test
    void vatExclusiveAddsTaxOnTop() {
        List<BillDiscountAllocation.LineResult> results = BillDiscountAllocation.allocate(
                List.of(new BillDiscountAllocation.LineInput(bd("10000.00"), VAT, false)),
                Money.ZERO);

        assertThat(results.get(0).taxAmount()).isEqualByComparingTo("1800.00");
        assertThat(results.get(0).lineTotal()).isEqualByComparingTo("11800.00");
    }

    @Test
    void aThirdWaySplitStillSumsExactly() {
        // 10.00 across three equal lines is 3.333... each - the classic case where
        // independent rounding loses or gains a cent.
        List<BigDecimal> shares = BillDiscountAllocation.split(
                List.of(bd("100.00"), bd("100.00"), bd("100.00")), bd("10.00"));

        assertThat(sum(shares)).isEqualByComparingTo("10.00");
        assertThat(shares).containsExactly(
                bd("3.34"), bd("3.33"), bd("3.33"));
    }

    @Test
    void discountLandsOnTheLargerLineWhenWeightsAreUneven() {
        List<BigDecimal> shares = BillDiscountAllocation.split(
                List.of(bd("0.01"), bd("999.99")), bd("1.00"));

        assertThat(sum(shares)).isEqualByComparingTo("1.00");
        assertThat(shares.get(1)).isGreaterThan(shares.get(0));
    }

    @Test
    void noDiscountAllocatesNothing() {
        List<BillDiscountAllocation.LineResult> results = BillDiscountAllocation.allocate(
                List.of(new BillDiscountAllocation.LineInput(bd("500.00"), VAT, true)),
                Money.ZERO);

        assertThat(results.get(0).allocatedDiscount()).isEqualByComparingTo("0");
        assertThat(results.get(0).netAfterDiscount()).isEqualByComparingTo("500.00");
    }

    @Test
    void zeroValueLinesDoNotBreakTheSplit() {
        // A free item alongside a paid one: nothing to weight it by, no divide by zero.
        List<BigDecimal> shares = BillDiscountAllocation.split(
                List.of(bd("0.00"), bd("100.00")), bd("10.00"));

        assertThat(sum(shares)).isEqualByComparingTo("10.00");
        assertThat(shares.get(0)).isEqualByComparingTo("0");
        assertThat(shares.get(1)).isEqualByComparingTo("10.00");
    }

    @Test
    void anAllZeroBillSpreadsNothingRatherThanDividingByZero() {
        List<BigDecimal> shares = BillDiscountAllocation.split(
                List.of(bd("0.00"), bd("0.00")), bd("10.00"));

        assertThat(shares).allMatch(s -> s.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void zeroRatedLinesGetNoTax() {
        List<BillDiscountAllocation.LineResult> results = BillDiscountAllocation.allocate(
                List.of(new BillDiscountAllocation.LineInput(bd("1000.00"), Money.ZERO, false)),
                Money.ZERO);

        assertThat(results.get(0).taxAmount()).isEqualByComparingTo("0");
        assertThat(results.get(0).lineTotal()).isEqualByComparingTo("1000.00");
    }

    @Test
    void nullTaxRateIsTreatedAsZeroRated() {
        List<BillDiscountAllocation.LineResult> results = BillDiscountAllocation.allocate(
                List.of(new BillDiscountAllocation.LineInput(bd("1000.00"), null, false)),
                Money.ZERO);

        assertThat(results.get(0).taxAmount()).isEqualByComparingTo("0");
    }

    @Test
    void reconcileIsANoOpWhenLinesAlreadyAgree() {
        List<BigDecimal> taxes = List.of(bd("100.00"), bd("50.00"));
        List<BigDecimal> adjusted = BillDiscountAllocation.reconcileTax(
                taxes, List.of(bd("1000.00"), bd("500.00")), bd("150.00"));

        assertThat(adjusted).containsExactly(bd("100.00"), bd("50.00"));
    }

    @Test
    void reconcilePushesTheCentOntoTheLargestLine() {
        List<BigDecimal> adjusted = BillDiscountAllocation.reconcileTax(
                List.of(bd("100.00"), bd("50.00")),
                List.of(bd("1000.00"), bd("500.00")),
                bd("150.01"));

        assertThat(sum(adjusted)).isEqualByComparingTo("150.01");
        assertThat(adjusted.get(0)).isEqualByComparingTo("100.01");
        assertThat(adjusted.get(1)).isEqualByComparingTo("50.00");
    }
}
