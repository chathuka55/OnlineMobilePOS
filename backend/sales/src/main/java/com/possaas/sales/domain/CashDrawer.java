package com.possaas.sales.domain;

import com.possaas.common.money.Money;
import java.math.BigDecimal;

/**
 * The drawer reconciliation for one shift.
 *
 * <p>Deliberately a pure value: every component is supplied, nothing is read
 * from a repository. The arithmetic behind "we are Rs 488.66 short" is the part
 * that has to be right, so it is kept where it can be tested on its own.
 */
public record CashDrawer(
        BigDecimal openingFloat,
        BigDecimal cashSales,
        BigDecimal cashRepairs,
        BigDecimal cashWholesale,
        BigDecimal cashRefunds,
        BigDecimal payIns,
        BigDecimal payouts,
        BigDecimal drops
) {

    public static CashDrawer of(BigDecimal openingFloat,
                                BigDecimal cashSales,
                                BigDecimal cashRepairs,
                                BigDecimal cashWholesale,
                                BigDecimal cashRefunds,
                                BigDecimal payIns,
                                BigDecimal payouts,
                                BigDecimal drops) {
        return new CashDrawer(
                Money.of(openingFloat), Money.of(cashSales), Money.of(cashRepairs),
                Money.of(cashWholesale), Money.of(cashRefunds), Money.of(payIns),
                Money.of(payouts), Money.of(drops));
    }

    /** Everything that should have gone into the drawer. */
    public BigDecimal cashIn() {
        return Money.add(Money.add(Money.add(openingFloat, cashSales), cashRepairs),
                Money.add(cashWholesale, payIns));
    }

    /** Everything that should have come back out of it. */
    public BigDecimal cashOut() {
        return Money.add(Money.add(cashRefunds, payouts), drops);
    }

    /** What the cashier should be able to count. */
    public BigDecimal expectedCash() {
        return Money.subtract(cashIn(), cashOut());
    }

    /**
     * Counted minus expected. Negative is short (money missing), positive is over.
     */
    public BigDecimal varianceAgainst(BigDecimal countedCash) {
        return Money.subtract(Money.of(countedCash), expectedCash());
    }
}
