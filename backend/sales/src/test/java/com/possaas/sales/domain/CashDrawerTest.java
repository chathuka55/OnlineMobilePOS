package com.possaas.sales.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CashDrawerTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    /**
     * The worked example from the design research:
     * 10,000 + 125,500 + 8,000 + 15,000 − 2,211.34 − 1,500 − 100,000 = 54,788.66,
     * counted 54,300.00, so the drawer is 488.66 short.
     */
    @Test
    void reconcilesTheWorkedExample() {
        CashDrawer drawer = CashDrawer.of(
                bd("10000"),      // opening float
                bd("125500"),     // cash sales
                bd("8000"),       // cash repair receipts
                bd("15000"),      // cash collections
                bd("2211.34"),    // cash refunds
                bd("0"),          // pay-ins
                bd("1500"),       // payouts / expenses
                bd("100000"));    // safe drops

        assertThat(drawer.expectedCash()).isEqualByComparingTo("54788.66");
        assertThat(drawer.varianceAgainst(bd("54300.00"))).isEqualByComparingTo("-488.66");
    }

    @Test
    void balancedDrawerHasZeroVariance() {
        CashDrawer drawer = CashDrawer.of(
                bd("5000"), bd("20000"), bd("0"), bd("0"),
                bd("0"), bd("0"), bd("0"), bd("0"));

        assertThat(drawer.expectedCash()).isEqualByComparingTo("25000");
        assertThat(drawer.varianceAgainst(bd("25000"))).isEqualByComparingTo("0");
    }

    @Test
    void overCountIsPositiveVariance() {
        CashDrawer drawer = CashDrawer.of(
                bd("1000"), bd("500"), bd("0"), bd("0"),
                bd("0"), bd("0"), bd("0"), bd("0"));

        assertThat(drawer.varianceAgainst(bd("1600"))).isEqualByComparingTo("100");
    }

    @Test
    void payInsAddAndPayoutsAndDropsSubtract() {
        CashDrawer drawer = CashDrawer.of(
                bd("0"), bd("0"), bd("0"), bd("0"),
                bd("0"),        // no refunds
                bd("7000"),     // pay-in
                bd("2000"),     // payout
                bd("1000"));    // drop

        assertThat(drawer.expectedCash()).isEqualByComparingTo("4000");
    }

    @Test
    void refundsComeOutOfTheDrawer() {
        CashDrawer drawer = CashDrawer.of(
                bd("0"), bd("10000"), bd("0"), bd("0"),
                bd("2500"), bd("0"), bd("0"), bd("0"));

        assertThat(drawer.expectedCash()).isEqualByComparingTo("7500");
    }

    @Test
    void repairAndWholesaleCashCountToo() {
        // A repair deposit and a dealer settling an invoice are both cash in the till.
        CashDrawer drawer = CashDrawer.of(
                bd("0"), bd("0"), bd("3500"), bd("6500"),
                bd("0"), bd("0"), bd("0"), bd("0"));

        assertThat(drawer.expectedCash()).isEqualByComparingTo("10000");
    }

    @Test
    void nullComponentsAreTreatedAsZero() {
        // An untouched till still has to reconcile rather than blow up.
        CashDrawer drawer = CashDrawer.of(
                bd("2500"), null, null, null, null, null, null, null);

        assertThat(drawer.expectedCash()).isEqualByComparingTo("2500");
        assertThat(drawer.varianceAgainst(bd("2500"))).isEqualByComparingTo("0");
    }

    @Test
    void drawerCanGoNegativeWhenPayoutsExceedTakings() {
        // Worth surfacing rather than clamping: it means the float was raided.
        CashDrawer drawer = CashDrawer.of(
                bd("1000"), bd("0"), bd("0"), bd("0"),
                bd("0"), bd("0"), bd("2500"), bd("0"));

        assertThat(drawer.expectedCash()).isEqualByComparingTo("-1500");
    }
}
