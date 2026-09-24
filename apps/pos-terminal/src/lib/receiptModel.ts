import type { Bill, Outlet, Tenant } from '@possaas/api-client';
import type { ReceiptModel } from './escpos';

const sum = (values: (number | string | null | undefined)[]) =>
  values.reduce<number>((total, value) => {
    const n = typeof value === 'string' ? Number(value) : (value ?? 0);
    return total + (Number.isFinite(n) ? n : 0);
  }, 0);

/**
 * Flattens a bill into what a thermal receipt needs.
 *
 * <p>Kept apart from the encoder so that the encoder never has to know about the
 * API shapes, and apart from the React component so that the same numbers reach
 * paper whether the receipt is rasterised by the browser or sent as ESC/POS.
 *
 * <p>The two discount figures are added together the way the on-screen receipt
 * adds them: lineDiscountTotal is the per-line discount and billDiscountAmount is
 * the whole-bill discount that step 4 allocates across the lines.
 */
export function toReceiptModel(
  bill: Bill,
  tenant: Tenant | null,
  outlet: Outlet | null,
  cashierName?: string | null,
): ReceiptModel {
  return {
    businessName: tenant?.businessName ?? 'Receipt',
    addressLines: [outlet?.addressLine1, outlet?.city].filter(
      (line): line is string => !!line && line.trim().length > 0,
    ),
    phone: outlet?.phonePrimary ?? null,
    // The TIN prints only for a registered shop, matching the A4 invoice, so an
    // unregistered shop never shows a number it is not entitled to quote.
    vatTin: tenant?.vatRegistered ? (tenant.taxIdentifier ?? null) : null,
    billNumber: bill.billNumber,
    billedAt: bill.billedAt,
    cashierName: cashierName ?? null,
    customerName: bill.customerName ?? null,
    currency: bill.currency ?? tenant?.defaultCurrency ?? 'LKR',
    lines: bill.lines.map((line) => ({
      name: line.itemName,
      quantity: line.quantity,
      unitPrice: line.unitPrice,
      amount: line.netAmount,
    })),
    subtotal: bill.subtotal,
    discountTotal: sum([bill.lineDiscountTotal, bill.billDiscountAmount]),
    taxTotal: bill.taxTotal,
    grandTotal: bill.grandTotal,
    balanceDue: bill.balanceDue,
    payments: bill.payments.map((payment) => ({
      method: payment.method,
      amount: payment.amount,
    })),
    changeAmount: sum(bill.payments.map((payment) => payment.changeAmount)),
    footer: outlet?.receiptFooter ?? null,
  };
}
