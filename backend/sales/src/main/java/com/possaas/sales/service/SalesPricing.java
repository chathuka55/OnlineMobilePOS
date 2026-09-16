package com.possaas.sales.service;

import com.possaas.common.error.ApiException;
import com.possaas.common.error.ErrorCode;
import com.possaas.common.money.Money;
import com.possaas.sales.domain.DiscountType;
import java.math.BigDecimal;

/** Shared line/document discount and tax arithmetic for sales-adjacent modules. */
public final class SalesPricing {

    private SalesPricing() {
    }

    public static BigDecimal discountAmount(DiscountType type, BigDecimal input, BigDecimal gross) {
        BigDecimal g = Money.of(gross);
        if (type == null || type == DiscountType.NONE || Money.isZero(input)) {
            return Money.ZERO;
        }
        BigDecimal amount = switch (type) {
            case NONE -> Money.ZERO;
            case PERCENTAGE -> Money.percentOf(g, input);
            case FIXED -> Money.of(input);
        };
        if (Money.gt(amount, g)) {
            throw ApiException.of(ErrorCode.DISCOUNT_EXCEEDS_LINE, "Discount exceeds line gross")
                    .with("gross", g)
                    .with("discount", amount);
        }
        return Money.clamp(amount, g);
    }

    public record LineTotals(
            BigDecimal grossAmount,
            BigDecimal discountAmount,
            BigDecimal netAmount,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {
    }

    public static LineTotals computeLine(BigDecimal quantity,
                                  BigDecimal unitPrice,
                                  DiscountType discountType,
                                  BigDecimal discountInput,
                                  BigDecimal taxRatePercent,
                                  boolean taxInclusive) {
        BigDecimal qty = Money.quantity(quantity);
        BigDecimal price = Money.of(unitPrice);
        BigDecimal gross = Money.of(qty.multiply(price));
        BigDecimal discount = discountAmount(discountType, discountInput, gross);
        BigDecimal net = Money.subtract(gross, discount);
        BigDecimal rate = taxRatePercent == null ? Money.ZERO : taxRatePercent;
        BigDecimal tax;
        BigDecimal lineTotal;
        if (taxInclusive) {
            tax = Money.taxFromInclusive(net, rate);
            lineTotal = net;
        } else {
            tax = Money.percentOf(net, rate);
            lineTotal = Money.add(net, tax);
        }
        return new LineTotals(gross, discount, net, tax, lineTotal);
    }
}
