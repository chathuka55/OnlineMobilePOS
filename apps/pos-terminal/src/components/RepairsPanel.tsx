import { useEffect, useState } from 'react';
import { ApiError, type Page } from '@possaas/api-client';
import { Input, Spinner, toast } from '@possaas/ui';
import { api, money } from '../lib/api';
import { PaymentCollector, type CollectedPayment } from './PaymentCollector';

type RepairRow = {
  id: string;
  repairNumber: string;
  status: string;
  customerName: string;
  deviceType?: string;
  deviceBrand?: string;
  deviceModel?: string;
  grandTotal: number;
  amountPaid: number;
  balanceDue: number;
};

export function RepairsPanel() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<RepairRow[]>([]);
  const [selected, setSelected] = useState<RepairRow | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (query.trim().length < 2) {
      setResults([]);
      return;
    }
    setLoading(true);
    const handle = setTimeout(() => {
      api
        .get<Page<RepairRow> | RepairRow[]>('/api/v1/repairs', { q: query.trim(), size: 10 })
        .then((data) => setResults(Array.isArray(data) ? data : data.content))
        .catch(() => setResults([]))
        .finally(() => setLoading(false));
    }, 250);
    return () => clearTimeout(handle);
  }, [query]);

  async function collect(payment: CollectedPayment) {
    if (!selected) return;
    setSubmitting(true);
    try {
      const updated = await api.post<RepairRow>(`/api/v1/repairs/${selected.id}/payments`, {
        method: payment.method,
        amount: payment.amount,
        reference: payment.reference,
        bankName: payment.bankName,
        chequeNumber: payment.chequeNumber,
        chequeDate: payment.chequeDate,
      });
      toast({
        title: 'Payment collected',
        description: `${updated.repairNumber} · ${money(payment.amount)}`,
        variant: 'success',
      });
      printRepairReceipt(updated, payment.amount);
      setSelected(updated.balanceDue > 0 ? updated : null);
      setQuery('');
      setResults([]);
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
          Search repair (number, customer, phone)
        </label>
        <Input
          autoFocus
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setSelected(null);
          }}
          placeholder="R-000123 or customer name…"
          className="mt-1"
        />
      </div>

      {loading && (
        <div className="flex justify-center py-4">
          <Spinner size="sm" />
        </div>
      )}

      {!selected && results.length > 0 && (
        <ul className="space-y-2">
          {results.map((r) => (
            <li key={r.id}>
              <button
                type="button"
                className="w-full rounded-xl border bg-white px-4 py-3 text-left shadow-sm hover:bg-slate-50"
                onClick={() => setSelected(r)}
              >
                <div className="flex items-center justify-between">
                  <span className="text-navy font-semibold">{r.repairNumber}</span>
                  <span className="text-xs font-medium uppercase text-muted-foreground">
                    {r.status}
                  </span>
                </div>
                <div className="text-muted-foreground text-sm">
                  {r.customerName} · {[r.deviceBrand, r.deviceModel].filter(Boolean).join(' ')}
                </div>
                <div className="mt-1 text-sm font-semibold">
                  Balance due: {money(r.balanceDue)}
                </div>
              </button>
            </li>
          ))}
        </ul>
      )}

      {selected && (
        <div className="rounded-xl border bg-white p-4 shadow-sm">
          <div className="mb-3">
            <p className="text-navy text-lg font-bold">{selected.repairNumber}</p>
            <p className="text-muted-foreground text-sm">{selected.customerName}</p>
            <div className="mt-2 flex justify-between text-sm">
              <span>Total</span>
              <span>{money(selected.grandTotal)}</span>
            </div>
            <div className="flex justify-between text-sm">
              <span>Paid</span>
              <span>{money(selected.amountPaid)}</span>
            </div>
            <div className="flex justify-between font-bold">
              <span>Balance due</span>
              <span>{money(selected.balanceDue)}</span>
            </div>
          </div>
          {selected.balanceDue > 0 ? (
            <PaymentCollector
              balanceDue={selected.balanceDue}
              submitting={submitting}
              submitLabel="Collect Payment"
              onSubmit={collect}
            />
          ) : (
            <p className="text-center text-sm font-medium text-green-600">Fully paid.</p>
          )}
        </div>
      )}
    </div>
  );
}

function printRepairReceipt(repair: RepairRow, amount: number) {
  const win = window.open('', '_blank', 'width=380,height=600');
  if (!win) return;
  win.document.write(`
    <html><head><title>${repair.repairNumber}</title></head>
    <body style="font-family: monospace; padding: 16px;">
      <h2 style="text-align:center;">Repair Receipt</h2>
      <p><strong>${repair.repairNumber}</strong></p>
      <p>Customer: ${repair.customerName}</p>
      <hr />
      <p>Payment received: ${amount.toFixed(2)}</p>
      <p>Total paid: ${repair.amountPaid.toFixed(2)}</p>
      <p>Balance due: ${repair.balanceDue.toFixed(2)}</p>
      <hr />
      <p style="text-align:center;">Thank you!</p>
    </body></html>
  `);
  win.document.close();
  win.focus();
  win.print();
}
