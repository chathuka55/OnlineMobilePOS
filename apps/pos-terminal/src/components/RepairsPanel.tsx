import { useEffect, useState } from 'react';
import { ApiError, type Item, type Page } from '@possaas/api-client';
import { Button, Input, Spinner, toast } from '@possaas/ui';
import { api, money } from '../lib/api';
import { PaymentCollector, type CollectedPayment } from './PaymentCollector';

type RepairRow = {
  id: string;
  repairNumber: string;
  status: string;
  customerName: string;
  customerPhone?: string;
  deviceType?: string;
  deviceBrand?: string;
  deviceModel?: string;
  deviceSerial?: string;
  reportedFault?: string;
  serviceCharge: number;
  partsSubtotal: number;
  grandTotal: number;
  amountPaid: number;
  balanceDue: number;
};

type NewRepairLine = {
  key: string;
  itemId?: string;
  description: string;
  quantity: number;
  unitPrice: number;
  lineType: 'PART' | 'LABOUR';
};

const emptyIntake = {
  customerName: '',
  customerPhone: '',
  deviceType: '',
  deviceBrand: '',
  deviceModel: '',
  deviceSerial: '',
  reportedFault: '',
  serviceCharge: 0,
  advancePaid: 0,
};

export function RepairsPanel() {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<RepairRow[]>([]);
  const [selected, setSelected] = useState<RepairRow | null>(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [creating, setCreating] = useState(false);
  const [intake, setIntake] = useState(emptyIntake);
  const [saving, setSaving] = useState(false);

  const [lineSearch, setLineSearch] = useState('');
  const [lineResults, setLineResults] = useState<Item[]>([]);
  const [manualLine, setManualLine] = useState<NewRepairLine>({
    key: '',
    description: '',
    quantity: 1,
    unitPrice: 0,
    lineType: 'LABOUR',
  });
  const [addingLine, setAddingLine] = useState(false);

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

  useEffect(() => {
    if (lineSearch.trim().length < 2) {
      setLineResults([]);
      return;
    }
    const handle = setTimeout(() => {
      api.items
        .list({ q: lineSearch.trim(), size: 8 })
        .then((data) => setLineResults(Array.isArray(data) ? data : data.content))
        .catch(() => setLineResults([]));
    }, 250);
    return () => clearTimeout(handle);
  }, [lineSearch]);

  async function createRepair() {
    if (!intake.customerName.trim()) {
      toast({ title: 'Customer name is required', variant: 'destructive' });
      return;
    }
    setSaving(true);
    try {
      const repair = await api.post<RepairRow>('/api/v1/repairs', {
        customerName: intake.customerName,
        customerPhone: intake.customerPhone || undefined,
        deviceType: intake.deviceType || undefined,
        deviceBrand: intake.deviceBrand || undefined,
        deviceModel: intake.deviceModel || undefined,
        deviceSerial: intake.deviceSerial || undefined,
        reportedFault: intake.reportedFault || undefined,
        serviceCharge: intake.serviceCharge || undefined,
        advancePaid: intake.advancePaid || undefined,
      });
      toast({ title: `Repair ${repair.repairNumber} created`, variant: 'success' });
      setSelected(repair);
      setCreating(false);
      setIntake(emptyIntake);
      setQuery('');
      setResults([]);
    } catch (err) {
      toast({
        title: 'Could not create repair',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setSaving(false);
    }
  }

  async function addItemLine(item: Item) {
    if (!selected) return;
    setAddingLine(true);
    try {
      const updated = await api.post<RepairRow>(`/api/v1/repairs/${selected.id}/lines`, {
        lineType: 'PART',
        itemId: item.id,
        description: item.name,
        quantity: 1,
        unitPrice: item.retailPrice,
      });
      setSelected(updated);
      setLineSearch('');
      setLineResults([]);
      toast({ title: `${item.name} added`, variant: 'success' });
    } catch (err) {
      toast({
        title: 'Could not add part',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setAddingLine(false);
    }
  }

  async function addManualLine() {
    if (!selected || !manualLine.description.trim() || manualLine.unitPrice <= 0) return;
    setAddingLine(true);
    try {
      const updated = await api.post<RepairRow>(`/api/v1/repairs/${selected.id}/lines`, {
        lineType: manualLine.lineType,
        description: manualLine.description,
        quantity: manualLine.quantity,
        unitPrice: manualLine.unitPrice,
      });
      setSelected(updated);
      setManualLine({ key: '', description: '', quantity: 1, unitPrice: 0, lineType: 'LABOUR' });
      toast({ title: 'Line added', variant: 'success' });
    } catch (err) {
      toast({
        title: 'Could not add line',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    } finally {
      setAddingLine(false);
    }
  }

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
      setSelected(updated);
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
      {!creating && !selected && (
        <>
          <div className="flex items-center justify-between">
            <label className="text-muted-foreground text-xs font-semibold uppercase tracking-wide">
              Search repair (number, customer, phone)
            </label>
            <Button size="sm" onClick={() => setCreating(true)}>
              New Repair
            </Button>
          </div>
          <Input
            autoFocus
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="R-000123 or customer name…"
          />
        </>
      )}

      {loading && (
        <div className="flex justify-center py-4">
          <Spinner size="sm" />
        </div>
      )}

      {!selected && !creating && results.length > 0 && (
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

      {creating && (
        <div className="rounded-xl border bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center justify-between">
            <p className="text-navy text-lg font-bold">New Repair Intake</p>
            <Button size="sm" variant="outline" onClick={() => setCreating(false)}>
              Cancel
            </Button>
          </div>
          <div className="grid grid-cols-2 gap-2">
            <Input
              className="col-span-2"
              placeholder="Customer name *"
              value={intake.customerName}
              onChange={(e) => setIntake((f) => ({ ...f, customerName: e.target.value }))}
              autoFocus
            />
            <Input
              placeholder="Phone"
              value={intake.customerPhone}
              onChange={(e) => setIntake((f) => ({ ...f, customerPhone: e.target.value }))}
            />
            <Input
              placeholder="Device type (Phone, Laptop…)"
              value={intake.deviceType}
              onChange={(e) => setIntake((f) => ({ ...f, deviceType: e.target.value }))}
            />
            <Input
              placeholder="Brand"
              value={intake.deviceBrand}
              onChange={(e) => setIntake((f) => ({ ...f, deviceBrand: e.target.value }))}
            />
            <Input
              placeholder="Model"
              value={intake.deviceModel}
              onChange={(e) => setIntake((f) => ({ ...f, deviceModel: e.target.value }))}
            />
            <Input
              className="col-span-2"
              placeholder="Serial / IMEI"
              value={intake.deviceSerial}
              onChange={(e) => setIntake((f) => ({ ...f, deviceSerial: e.target.value }))}
            />
            <Input
              className="col-span-2"
              placeholder="Reported fault"
              value={intake.reportedFault}
              onChange={(e) => setIntake((f) => ({ ...f, reportedFault: e.target.value }))}
            />
            <div>
              <label className="text-muted-foreground text-xs">Service charge</label>
              <Input
                type="number"
                min="0"
                value={intake.serviceCharge}
                onChange={(e) =>
                  setIntake((f) => ({ ...f, serviceCharge: Number(e.target.value) || 0 }))
                }
              />
            </div>
            <div>
              <label className="text-muted-foreground text-xs">Advance paid (cash)</label>
              <Input
                type="number"
                min="0"
                value={intake.advancePaid}
                onChange={(e) =>
                  setIntake((f) => ({ ...f, advancePaid: Number(e.target.value) || 0 }))
                }
              />
            </div>
          </div>
          <Button className="mt-4 w-full" disabled={saving} onClick={() => void createRepair()}>
            {saving ? 'Creating…' : 'Create Repair'}
          </Button>
        </div>
      )}

      {selected && (
        <div className="space-y-3">
          <div className="flex justify-end">
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                setSelected(null);
                setQuery('');
              }}
            >
              Back to search
            </Button>
          </div>

          <div className="rounded-xl border bg-white p-4 shadow-sm">
            <p className="text-navy text-lg font-bold">{selected.repairNumber}</p>
            <p className="text-muted-foreground text-sm">
              {selected.customerName}
              {selected.customerPhone ? ` · ${selected.customerPhone}` : ''}
            </p>
            <p className="text-muted-foreground text-sm">
              {[selected.deviceBrand, selected.deviceModel].filter(Boolean).join(' ')}
              {selected.reportedFault ? ` — ${selected.reportedFault}` : ''}
            </p>
            <div className="mt-2 flex justify-between text-sm">
              <span>Service + parts</span>
              <span>
                {money(Number(selected.serviceCharge) + Number(selected.partsSubtotal))}
              </span>
            </div>
            <div className="flex justify-between text-sm">
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

          <div className="rounded-xl border bg-white p-4 shadow-sm">
            <p className="mb-2 text-sm font-semibold text-navy">Add a part</p>
            <Input
              placeholder="Search stock item…"
              value={lineSearch}
              onChange={(e) => setLineSearch(e.target.value)}
            />
            {lineResults.length > 0 && (
              <ul className="mt-2 space-y-1">
                {lineResults.map((item) => (
                  <li key={item.id}>
                    <button
                      type="button"
                      disabled={addingLine}
                      className="flex w-full items-center justify-between rounded-lg border px-3 py-2 text-left text-sm hover:bg-slate-50"
                      onClick={() => void addItemLine(item)}
                    >
                      <span>{item.name}</span>
                      <span className="font-semibold">{money(item.retailPrice)}</span>
                    </button>
                  </li>
                ))}
              </ul>
            )}

            <p className="mb-2 mt-4 text-sm font-semibold text-navy">Or add a labour/service charge</p>
            <div className="grid grid-cols-3 gap-2">
              <Input
                className="col-span-2"
                placeholder="Description"
                value={manualLine.description}
                onChange={(e) => setManualLine((f) => ({ ...f, description: e.target.value }))}
              />
              <Input
                type="number"
                min="0"
                placeholder="Price"
                value={manualLine.unitPrice || ''}
                onChange={(e) =>
                  setManualLine((f) => ({ ...f, unitPrice: Number(e.target.value) || 0 }))
                }
              />
            </div>
            <Button
              className="mt-2 w-full"
              size="sm"
              variant="outline"
              disabled={addingLine || !manualLine.description.trim() || manualLine.unitPrice <= 0}
              onClick={() => void addManualLine()}
            >
              Add Charge
            </Button>
          </div>

          {selected.balanceDue > 0 ? (
            <div className="rounded-xl border bg-white p-4 shadow-sm">
              <p className="mb-2 text-sm font-semibold text-navy">Collect payment</p>
              <PaymentCollector
                balanceDue={selected.balanceDue}
                submitting={submitting}
                submitLabel="Collect Payment"
                onSubmit={collect}
              />
            </div>
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
