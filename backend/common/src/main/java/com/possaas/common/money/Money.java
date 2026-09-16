package com.possaas.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money arithmetic for the whole application.
 *
 * <p>Every monetary value is a {@link BigDecimal} scaled to {@link #SCALE} using
 * {@link RoundingMode#HALF_UP}, matching the {@code numeric(14, 2)} columns and the
 * convention the desktop application already used in {@code BillItem.calculateTotals}.
 * Rounding once per line and again on the document total keeps the printed receipt
 * arithmetically consistent, which is what customers and auditors check.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);
    public static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Quantities allow three decimals for shops selling by weight or length. */
    public static final int QUANTITY_SCALE = 3;

    private Money() {
    }

    /** Normalises any value to money scale. Null becomes zero. */
    public static BigDecimal of(BigDecimal value) {
        return value == null ? ZERO : value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal of(long value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal quantity(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(QUANTITY_SCALE)
                : value.setScale(QUANTITY_SCALE, ROUNDING);
    }

    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return of(nz(a).add(nz(b)));
    }

    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return of(nz(a).subtract(nz(b)));
    }

    /**
     * Multiplies without rounding the intermediate result, then rounds once. Rounding
     * the factors first would drift by a cent on large quantities.
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return of(nz(a).multiply(nz(b)));
    }

    public static BigDecimal negate(BigDecimal value) {
        return of(nz(value).negate());
    }

    /** {@code value * percent / 100}. */
    public static BigDecimal percentOf(BigDecimal value, BigDecimal percent) {
        if (isZero(value) || isZero(percent)) {
            return ZERO;
        }
        return nz(value).multiply(nz(percent))
                .divide(HUNDRED, SCALE, ROUNDING);
    }

    /**
     * Extracts the tax already contained in a tax-inclusive price:
     * {@code gross - gross / (1 + rate/100)}.
     */
    public static BigDecimal taxFromInclusive(BigDecimal grossAmount, BigDecimal ratePercent) {
        if (isZero(grossAmount) || isZero(ratePercent)) {
            return ZERO;
        }
        BigDecimal divisor = BigDecimal.ONE.add(nz(ratePercent).divide(HUNDRED, 10, ROUNDING));
        BigDecimal net = nz(grossAmount).divide(divisor, SCALE, ROUNDING);
        return of(nz(grossAmount).subtract(net));
    }

    /** Clamps to {@code [0, max]}. Used to stop a discount exceeding its line. */
    public static BigDecimal clamp(BigDecimal value, BigDecimal max) {
        BigDecimal v = of(value);
        if (v.signum() < 0) {
            return ZERO;
        }
        BigDecimal ceiling = of(max);
        return v.compareTo(ceiling) > 0 ? ceiling : v;
    }

    public static BigDecimal max(BigDecimal a, BigDecimal b) {
        return of(nz(a).compareTo(nz(b)) >= 0 ? a : b);
    }

    public static BigDecimal min(BigDecimal a, BigDecimal b) {
        return of(nz(a).compareTo(nz(b)) <= 0 ? a : b);
    }

    public static boolean isZero(BigDecimal value) {
        return value == null || value.signum() == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }

    public static boolean eq(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) == 0;
    }

    public static boolean gt(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) > 0;
    }

    public static boolean gte(BigDecimal a, BigDecimal b) {
        return nz(a).compareTo(nz(b)) >= 0;
    }

    /**
     * Rounds a cash total to the nearest physical denomination and returns the
     * adjustment. Sri Lanka has withdrawn coins below one rupee, so a
     * {@code nearest} of 1.00 produces receipts that can actually be settled.
     */
    public static BigDecimal roundingAdjustment(BigDecimal total, BigDecimal nearest) {
        if (isZero(nearest)) {
            return ZERO;
        }
        BigDecimal rounded = nz(total)
                .divide(nearest, 0, ROUNDING)
                .multiply(nearest);
        return of(rounded.subtract(nz(total)));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
