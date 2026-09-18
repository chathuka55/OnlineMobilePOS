import type { Bill, Outlet, Tenant } from '@possaas/api-client';
import { money } from '../lib/api';

export type LogoLayout = 'SIDE' | 'CENTERED';

type Props = {
  bill: Bill;
  tenant: Tenant | null;
  outlet: Outlet | null;
  logoLayout?: LogoLayout;
};

/**
 * The printable receipt. Rendered off-screen at all times and made visible only
 * by the @media print rules in styles.css, which also pick the physical page size
 * to match whichever format the cashier selected in lib/print.ts.
 */
export function Receipt({ bill, tenant, outlet, logoLayout = 'SIDE' }: Props) {
  const currency = bill.currency ?? tenant?.defaultCurrency ?? 'LKR';
  const hasLogo = !!outlet?.logoDataUrl;
  // No logo means there's nothing to sit "beside", so business details are always
  // centered in that case - SIDE only makes sense once there's actually a logo.
  const headerMode: LogoLayout = hasLogo ? logoLayout : 'CENTERED';

  const businessDetails = (
    <div className="receipt-business-details">
      <div className="receipt-business-name">{tenant?.businessName ?? 'Receipt'}</div>
      {outlet?.addressLine1 ? <div className="receipt-line">{outlet.addressLine1}</div> : null}
      {outlet?.city ? <div className="receipt-line">{outlet.city}</div> : null}
      {outlet?.phonePrimary ? (
        <div className="receipt-line">Tel: {outlet.phonePrimary}</div>
      ) : null}
    </div>
  );

  return (
    <div id="receipt-print">
      <div className={`receipt-header receipt-header-${headerMode.toLowerCase()}`}>
        {hasLogo ? <img src={outlet!.logoDataUrl!} alt="" className="receipt-logo" /> : null}
        {businessDetails}
      </div>

      <div className="receipt-divider" />
      <div className="receipt-line">Bill No: {bill.billNumber}</div>
      <div className="receipt-line">Date: {new Date(bill.billedAt).toLocaleString()}</div>
      {bill.customerName ? (
        <div className="receipt-line">Customer: {bill.customerName}</div>
      ) : null}
      <div className="receipt-divider" />

      <table className="receipt-lines">
        <thead>
          <tr>
            <th className="receipt-col-item">Item</th>
            <th className="receipt-col-qty">Qty</th>
            <th className="receipt-col-amt">Amount</th>
          </tr>
        </thead>
        <tbody>
          {bill.lines.map((line) => (
            <tr key={line.id}>
              <td className="receipt-col-item">{line.itemName}</td>
              <td className="receipt-col-qty">{line.quantity}</td>
              <td className="receipt-col-amt">{money(line.netAmount, currency)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <div className="receipt-divider" />
      <div className="receipt-totals">
        <div className="receipt-row">
          <span>Subtotal</span>
          <span>{money(bill.subtotal, currency)}</span>
        </div>
        {Number(bill.lineDiscountTotal) > 0 || Number(bill.billDiscountAmount) > 0 ? (
          <div className="receipt-row">
            <span>Discount</span>
            <span>
              -{money(Number(bill.lineDiscountTotal) + Number(bill.billDiscountAmount), currency)}
            </span>
          </div>
        ) : null}
        {Number(bill.taxTotal) > 0 ? (
          <div className="receipt-row">
            <span>Tax</span>
            <span>{money(bill.taxTotal, currency)}</span>
          </div>
        ) : null}
        <div className="receipt-row receipt-row-total">
          <span>Total</span>
          <span>{money(bill.grandTotal, currency)}</span>
        </div>
      </div>

      <div className="receipt-divider" />
      {bill.payments.map((payment) => (
        <div className="receipt-row" key={payment.id}>
          <span>{payment.method}</span>
          <span>{money(payment.amount, currency)}</span>
        </div>
      ))}
      {Number(bill.balanceDue) > 0 ? (
        <div className="receipt-row">
          <span>Balance Due</span>
          <span>{money(bill.balanceDue, currency)}</span>
        </div>
      ) : null}
      {bill.payments.some((p) => Number(p.changeAmount) > 0) ? (
        <div className="receipt-row">
          <span>Change</span>
          <span>
            {money(
              bill.payments.reduce((sum, p) => sum + Number(p.changeAmount ?? 0), 0),
              currency,
            )}
          </span>
        </div>
      ) : null}

      {outlet?.receiptFooter ? (
        <>
          <div className="receipt-divider" />
          <div className="receipt-footer">{outlet.receiptFooter}</div>
        </>
      ) : null}
    </div>
  );
}
