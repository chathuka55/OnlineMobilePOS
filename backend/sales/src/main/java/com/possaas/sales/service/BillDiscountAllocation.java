package com.possaas.sales.service;

import com.possaas.common.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spreads a bill-level discount across the lines it applies to, then restates
 * each line's tax on the discounted amount.
 *
 * <h2>Why this exists</h2>
 * A bill discount reduces what the customer pays, so it must also reduce the tax
 * they are charged. Computing line tax before the discount and then subtracting
 * the discount from the total leaves the printed VAT describing an amount nobody
 * paid.
 *
 * <h2>Largest remainder</h2>
 * Pro-rata shares rarely divide into whole cents. Rounding each share
 * independently can leave the parts summing to a cent more or less than the
 * discount given. Shares are therefore floored, and the leftover cents handed out
 * one at a time to the lines with the largest discarded fractions - so the
 * allocation always sums to exactly the discount, and the cents land where they
 * were most nearly earned.
 *
 * <h2>Tax reconciliation</h2>
 * Tax is computed per line and also once at invoice level. Because each line
 * rounds to a cent, the sum of line taxes can differ from the invoice figure by a
 * cent or two. The difference is pushed onto the largest line, which keeps the
 * lines adding up to the invoice total without visibly distorting any line.
 */
public final class BillDiscountAllocation {

    /** Intermediate precision. Rounding to cents happens only at the defined points. */
    private static final int WORKING_SCALE = 6;

    private BillDiscountAllocation() {
    }

    /** One line's inputs: what it is worth, and how it is taxed. */
    public record LineInput(BigDecimal netAmount, BigDecimal taxRatePercent, boolean taxInclusive) {
    }

    /** One line's outputs after the bill discount has been applied to it. */
    public record LineResult(
            BigDecimal allocatedDiscount,
            BigDecimal netAfterDiscount,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {
    }

    /**
     * @param lineNets      each line's net after its own line-level discount
     * @param billDiscount  the bill-level discount to spread across them
     */
    public static List<LineResult> allocate(List<LineInput> lines, BigDecimal billDiscount) {
        BigDecimal discount = Money.of(billDiscount);
        List<BigDecimal> nets = lines.stream().map(l -> Money.of(l.netAmount())).toList();
        List<BigDecimal> shares = split(nets, discount);

        List<LineResult> results = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            LineInput line = lines.get(i);
            BigDecimal share = shares.get(i);
            BigDecimal net = Money.subtract(nets.get(i), share);
            BigDecimal rate = line.taxRatePercent() == null ? Money.ZERO : line.taxRatePercent();

            BigDecimal tax;
            BigDecimal total;
            if (line.taxInclusive()) {
                // The net already contains the tax, so the discounted net is the total.
                tax = Money.taxFromInclusive(net, rate);
                total = net;
            } else {
                tax = Money.percentOf(net, rate);
                total = Money.add(net, tax);
            }
            results.add(new LineResult(share, net, tax, total));
        }
        return results;
    }

    /**
     * Splits an amount across weights so the parts sum exactly to the whole.
     * A zero total weight spreads nothing rather than dividing by zero.
     */
    static List<BigDecimal> split(List<BigDecimal> weights, BigDecimal amount) {
        int n = weights.size();
        List<BigDecimal> shares = new ArrayList<>(n);
        BigDecimal totalWeight = Money.ZERO;
        for (BigDecimal w : weights) {
            totalWeight = Money.add(totalWeight, w);
        }

        if (n == 0 || !Money.isPositive(amount) || !Money.isPositive(totalWeight)) {
            for (int i = 0; i < n; i++) {
                shares.add(Money.ZERO);
            }
            return shares;
        }

        // Floor each share to the cent, tracking what was discarded.
        record Remainder(int index, BigDecimal fraction) {
        }
        List<Remainder> remainders = new ArrayList<>(n);
        BigDecimal distributed = Money.ZERO;

        for (int i = 0; i < n; i++) {
            BigDecimal exact = weights.get(i)
                    .multiply(amount)
                    .divide(totalWeight, WORKING_SCALE, RoundingMode.HALF_UP);
            BigDecimal floored = exact.setScale(Money.SCALE, RoundingMode.DOWN);
            shares.add(floored);
            distributed = Money.add(distributed, floored);
            remainders.add(new Remainder(i, exact.subtract(floored)));
        }

        // Hand the leftover cents to the largest discarded fractions first.
        BigDecimal leftover = Money.subtract(amount, distributed);
        BigDecimal cent = BigDecimal.valueOf(1, Money.SCALE);
        remainders.sort(Comparator.comparing(Remainder::fraction).reversed());

        int i = 0;
        while (Money.isPositive(leftover) && i < remainders.size()) {
            int index = remainders.get(i).index();
            shares.set(index, Money.add(shares.get(index), cent));
            leftover = Money.subtract(leftover, cent);
            i++;
        }
        return shares;
    }

    /**
     * Reconciles per-line tax against the tax computed once on the invoice total,
     * pushing any rounding difference onto the largest line.
     *
     * @return the per-line tax amounts, which now sum to {@code invoiceTax}
     */
    public static List<BigDecimal> reconcileTax(List<BigDecimal> lineTaxes,
                                                List<BigDecimal> lineTotals,
                                                BigDecimal invoiceTax) {
        List<BigDecimal> adjusted = new ArrayList<>(lineTaxes.stream().map(Money::of).toList());
        if (adjusted.isEmpty()) {
            return adjusted;
        }

        BigDecimal sum = Money.ZERO;
        for (BigDecimal t : adjusted) {
            sum = Money.add(sum, t);
        }
        BigDecimal difference = Money.subtract(Money.of(invoiceTax), sum);
        if (Money.isZero(difference)) {
            return adjusted;
        }

        int largest = 0;
        for (int i = 1; i < lineTotals.size() && i < adjusted.size(); i++) {
            if (Money.gt(Money.of(lineTotals.get(i)), Money.of(lineTotals.get(largest)))) {
                largest = i;
            }
        }
        adjusted.set(largest, Money.add(adjusted.get(largest), difference));
        return adjusted;
    }
}
