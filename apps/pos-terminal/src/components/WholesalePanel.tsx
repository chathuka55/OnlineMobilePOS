import { useEffect, useState } from 'react';
import { ApiError, type Customer, type Item, type Page } from '@possaas/api-client';
import { Button, Input, Spinner, toast } from '@possaas/ui';
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

type NewLine = {
  key: string;
  itemId: string;
  name: string;
  quantity: number;
  unitPrice: number;
};

export function WholesalePanel() {
  const [customer, setCustomer] = useState<Customer | null>(null);
  const [invoices, setInvoices] = useState<InvoiceRow[]>([]);
  const [selected, setSelected] = useState<InvoiceRow | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [creating, setCreating] = useState(false);
  const [lines, setLines] = useState<NewLine[]>([]);
  const [itemSearch, setItemSearch] = useState('');
  const [itemResults, setItemResults] = useState<Item[]>([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (itemSearch.trim().length < 2) {
      setItemResults([]);
      return;
    }
    const handle = setTimeout(() => {
      api.items
        .list({ q: itemSearch.trim(), size: 8 })
        .then((data) => setItemResults(Array.isArray(data) ? data : data.content))
        .catch(() => setItemResults([]));
    }, 250);
    return () => clearTimeout(handle);
  }, [itemSearch]);

  function loadInvoices(c: Customer) {
    setCustomer(c);
    setSelected(null);
    setCreating(false);
    setLines([]);
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

  function addLine(item: Item) {
    setLines((prev) => {
      const existing = prev.find((l) => l.itemId === item.id);
      if (existing) {
        return prev.map((l) => (l.itemId === item.id ? { ...l, quantity: l.quantity + 1 } : l));
      }
      return [
        ...prev,
        {
          key: `${item.id}-${Date.now()}`,
          itemId: item.id,
          name: item.name,
          quantity: 1,
          unitPrice: Number(item.wholesalePrice) || Number(item.retailPrice),
        },
      ];
    });
    setItemSearch('');
    setItemResults([]);
  }

  const cartTotal = lines.reduce((sum, l) => sum + l.quantity * l.unitPrice, 0);

  async function createAndPost() {
    if (!customer || lines.length === 0) return;
    setSaving(true);
    try {
      const invoice = await api.post<InvoiceRow>('/api/v1/wholesale/invoices', {
        customerId: customer.id,
        customerName: customer.displayName,
        customerPhone: customer.phonePrimary ?? undefined,
        lines: lines.map((l) => ({
          itemId: l.itemId,
          quantity: l.quantity,
          unitPrice: l.unitPrice,
        })),
      });
      const posted = await api.post<InvoiceRow>(`/api/v1/wholesale/invoices/${invoice.id}/post`);
      toast({
        title: `Invoice ${posted.invoiceNumber} posted`,
        description: money(posted.grandTotal),
        variant: 'success',
      });
      setLines([]);
      setCreating(false);
      if (posted.outstandingAmount > 0) {
        setSelected(posted);
      } else {
        loadInvoices(customer);
      }
    } catch (err) {
      toast({
        title: 'Could not create invoice',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setSaving(false);
    }
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

      {customer && !loading && !selected && !creating && (
        <>
          <Button onClick={() => setCreating(true)}>New Wholesale Invoice</Button>
          <p className="text-navy text-sm font-semibold">
            {customer.displayName}&apos;s open invoices
          </p>
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
                      <span className="text-muted-foreground text-xs font-medium uppercase">
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

      {creating && customer && (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-navy font-semibold">New invoice for {customer.displayName}</p>
            <Button size="sm" variant="outline" onClick={() => setCreating(false)}>
              Cancel
            </Button>
          </div>

          <Input
            placeholder="Search stock item to add…"
            value={itemSearch}
            onChange={(e) => setItemSearch(e.target.value)}
          />
          {itemResults.length > 0 && (
            <ul className="space-y-1">
              {itemResults.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className="flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left text-sm hover:bg-slate-50"
                    onClick={() => addLine(item)}
                  >
                    <span>{item.name}</span>
                    <span className="font-semibold">{money(item.wholesalePrice)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}

          {lines.length > 0 && (
            <div className="rounded-xl border bg-white p-3 shadow-sm">
              <ul className="space-y-2">
                {lines.map((l) => (
                  <li key={l.key} className="flex items-center justify-between gap-2 text-sm">
                    <span className="flex-1">{l.name}</span>
                    <Input
                      type="number"
                      min="0.001"
                      step="0.001"
                      className="w-16"
                      value={l.quantity}
                      onChange={(e) =>
                        setLines((prev) =>
                          prev.map((x) =>
                            x.key === l.key ? { ...x, quantity: Number(e.target.value) || 0 } : x,
                          ),
                        )
                      }
                    />
                    <Input
                      type="number"
                      min="0"
                      step="0.01"
                      className="w-24"
                      value={l.unitPrice}
                      onChange={(e) =>
                        setLines((prev) =>
                          prev.map((x) =>
                            x.key === l.key ? { ...x, unitPrice: Number(e.target.value) || 0 } : x,
                          ),
                        )
                      }
                    />
                    <span className="w-20 text-right font-semibold">
                      {money(l.quantity * l.unitPrice)}
                    </span>
                  </li>
                ))}
              </ul>
              <div className="mt-3 flex items-center justify-between border-t pt-2">
                <span className="font-bold">Total</span>
                <span className="text-navy text-lg font-bold">{money(cartTotal)}</span>
              </div>
            </div>
          )}

          <Button
            className="w-full"
            size="lg"
            disabled={saving || lines.length === 0}
            onClick={() => void createAndPost()}
          >
            {saving ? 'Posting…' : `Create & Post Invoice (${money(cartTotal)})`}
          </Button>
        </div>
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
