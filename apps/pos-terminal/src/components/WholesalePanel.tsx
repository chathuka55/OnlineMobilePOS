import { useState } from 'react';
import { ApiError, type Customer, type Page } from '@possaas/api-client';
import { Button, Spinner, toast } from '@possaas/ui';
import { api, money } from '../lib/api';
import { CustomerSearch } from './CustomerSearch';
import { PaymentCollector, type CollectedPayment } from './PaymentCollector';

type InvoiceRow = {
  id: string;
  invoiceNumber: string;
  status: string;
  grandTotal: number;
  amountPaid: number;
  outstandingAmount: number;
};

export function WholesalePanel() {
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [invoices, setInvoices] = useState<InvoiceRow[]>([]);
  const [selected, setSelected] = useState<InvoiceRow | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  function loadInvoices(c: Customer) {
    setCustomer(c);
    setSelected(null);
    setLoading(true);
    api
      .get<Page<InvoiceRow> | InvoiceRow[]>('/api/v1/wholesale/invoices', {
        customerId: c.id,
        size: 20,
      })
      .then((data) => {
        const rows = Array.isArray(data) ? data : data.content;
        setInvoices(rows.filter((r) => r.outstandingAmount > 0));
      })
      .catch(() => setInvoices([]))
      .finally(() => setLoading(false));
  }

  async function collect(payment: CollectedPayment) {
    if (!selected) return;
    setSubmitting(true);
    try {
      const updated = await api.post<InvoiceRow>(
        `/api/v1/wholesale/invoices/${selected.id}/collections`,
        {
          method: payment.method,
          amount: payment.amount,
          reference: payment.reference,
          bankName: payment.bankName,
          chequeNumber: payment.chequeNumber,
          chequeDate: payment.chequeDate,
        },
      );
      toast({
        title: 'Payment collected',
        description: `${updated.invoiceNumber} · ${money(payment.amount)}`,
        variant: 'success',
      });
      setInvoices((prev) =>
        prev
          .map((inv) => (inv.id === updated.id ? updated : inv))
          .filter((inv) => inv.outstandingAmount > 0),
      );
      setSelected(null);
    } catch (err) {
      toast({
        title: 'Could not collect payment',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="mx-auto flex h-full w-full max-w-lg flex-col gap-4 overflow-y-auto p-4">
      <div>
        <label className="text-muted-foreground text-xs font-semibold uppercase tracking-wide">
          Wholesale customer
        </label>
        <div className="mt-1">
          <CustomerSearch type="WHOLESALE" onSelect={loadInvoices} />
        </div>
      </div>

      {loading && (
        <div className="flex justify-center py-4">
          <Spinner size="sm" />
        </div>
      )}

      {customer && !loading && !selected && (
        <>
          <p className="text-sm font-semibold text-navy">{customer.displayName}'s open invoices</p>
          {invoices.length === 0 ? (
            <p className="text-muted-foreground text-sm">No outstanding invoices.</p>
          ) : (
            <ul className="space-y-2">
              {invoices.map((inv) => (
                <li key={inv.id}>
                  <button
                    type="button"
                    className="w-full rounded-xl border bg-white px-4 py-3 text-left shadow-sm hover:bg-slate-50"
                    onClick={() => setSelected(inv)}
                  >
                    <div className="flex items-center justify-between">
                      <span className="text-navy font-semibold">{inv.invoiceNumber}</span>
                      <span className="text-xs font-medium uppercase text-muted-foreground">
                        {inv.status}
                      </span>
                    </div>
                    <div className="mt-1 flex justify-between text-sm">
                      <span>Total {money(inv.grandTotal)}</span>
                      <span className="font-bold">Due {money(inv.outstandingAmount)}</span>
                    </div>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </>
      )}

      {selected && (
        <div className="rounded-xl border bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center justify-between">
            <div>
              <p className="text-navy text-lg font-bold">{selected.invoiceNumber}</p>
              <p className="text-muted-foreground text-sm">{customer?.displayName}</p>
            </div>
            <Button size="sm" variant="outline" onClick={() => setSelected(null)}>
              Back
            </Button>
          </div>
          <div className="mb-3 flex justify-between font-bold">
            <span>Balance due</span>
            <span>{money(selected.outstandingAmount)}</span>
          </div>
          <PaymentCollector
            balanceDue={selected.outstandingAmount}
            submitting={submitting}
            submitLabel="Collect Payment"
            onSubmit={collect}
          />
        </div>
      )}
    </div>
  );
}
