'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useMemo, useState } from 'react';
import { ApiError } from '@possaas/api-client';
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  EmptyState,
  Input,
  Label,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  Spinner,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  toast,
} from '@possaas/ui';
import { Eye, Plus, PenTool, Printer, Undo2 } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

// Matches backend RepairOrderStatus (backend/repairs/domain/RepairOrderStatus.java) exactly.
export type RepairStatus =
  | 'RECEIVED'
  | 'DIAGNOSING'
  | 'AWAITING_APPROVAL'
  | 'AWAITING_PARTS'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'IRREPARABLE';

const STATUS_LABELS: Record<RepairStatus, string> = {
  RECEIVED: 'Received',
  DIAGNOSING: 'Diagnosing',
  AWAITING_APPROVAL: 'Awaiting Approval',
  AWAITING_PARTS: 'Awaiting Parts',
  IN_PROGRESS: 'In Progress',
  COMPLETED: 'Completed',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
  IRREPARABLE: 'Irreparable',
};

// The natural "next step" for the primary action button. Any other transition
// (e.g. skipping straight to CANCELLED/IRREPARABLE) is still possible via the
// backend's /transition endpoint, just not offered as the one-click default here.
const NEXT_STATUS: Partial<Record<RepairStatus, RepairStatus>> = {
  RECEIVED: 'DIAGNOSING',
  DIAGNOSING: 'IN_PROGRESS',
  AWAITING_APPROVAL: 'IN_PROGRESS',
  AWAITING_PARTS: 'IN_PROGRESS',
  IN_PROGRESS: 'COMPLETED',
  COMPLETED: 'DELIVERED',
};

const TERMINAL_STATUSES: RepairStatus[] = ['DELIVERED', 'CANCELLED', 'IRREPARABLE'];

export interface RepairHistory {
  id: string;
  fromStatus: RepairStatus | null;
  toStatus: RepairStatus;
  note?: string;
  changedAt: string;
}

export interface RepairLine {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Repair {
  id: string;
  repairNumber: string;
  status: RepairStatus;
  customerName: string;
  customerPhone?: string;
  deviceType?: string;
  deviceBrand?: string;
  deviceModel?: string;
  deviceSerial?: string;
  reportedFault?: string;
  diagnosis?: string;
  estimatedCost?: number;
  grandTotal?: number;
  amountPaid?: number;
  balanceDue?: number;
  advancePaid?: number;
  technicianId?: string;
  note?: string;
  receivedAt: string;
  lines?: RepairLine[];
  history?: RepairHistory[];
}

type RepairForm = {
  customerName: string;
  customerPhone: string;
  deviceType: string;
  deviceBrand: string;
  deviceModel: string;
  deviceSerial: string;
  reportedFault: string;
  estimatedCost: number;
  advancePaid: number;
  note: string;
};

const emptyForm: RepairForm = {
  customerName: '',
  customerPhone: '',
  deviceType: '',
  deviceBrand: '',
  deviceModel: '',
  deviceSerial: '',
  reportedFault: '',
  estimatedCost: 0,
  advancePaid: 0,
  note: '',
};

function deviceSummary(r: Pick<Repair, 'deviceType' | 'deviceBrand' | 'deviceModel'>) {
  return [r.deviceType, r.deviceBrand, r.deviceModel].filter(Boolean).join(' ') || '—';
}

export default function RepairsPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [createOpen, setCreateOpen] = useState(false);
  const [viewOpen, setViewOpen] = useState(false);
  const [selectedRepair, setSelectedRepair] = useState<Repair | null>(null);
  const [form, setForm] = useState<RepairForm>(emptyForm);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [paymentAmount, setPaymentAmount] = useState(0);
  const [lineOpen, setLineOpen] = useState(false);
  const [lineForm, setLineForm] = useState({
    lineType: 'PART' as 'PART' | 'LABOUR' | 'SERVICE',
    itemId: '',
    description: '',
    quantity: 1,
    unitPrice: 0,
  });
  const [refundOpen, setRefundOpen] = useState(false);
  const [refundForm, setRefundForm] = useState({ amount: 0, reason: '' });

  const itemsQuery = useQuery({
    queryKey: ['items', 'for-repairs'],
    queryFn: () => api.items.list({ size: 500 }),
  });
  const items = useMemo(() => asList(itemsQuery.data), [itemsQuery.data]);

  const repairsQuery = useQuery({
    queryKey: ['repairs', q, statusFilter],
    queryFn: () =>
      api.get<any>('/api/v1/repairs', {
        size: 100,
        q: q || undefined,
        status: statusFilter !== 'ALL' ? statusFilter : undefined,
      }),
  });

  const allRepairs = useMemo(() => {
    const data = repairsQuery.data;
    const items: Repair[] = Array.isArray(data) ? data : data?.content || [];
    return items;
  }, [repairsQuery.data]);

  const createMutation = useMutation({
    mutationFn: async () => {
      return api.post<Repair>('/api/v1/repairs', {
        customerName: form.customerName,
        customerPhone: form.customerPhone || undefined,
        deviceType: form.deviceType || undefined,
        deviceBrand: form.deviceBrand || undefined,
        deviceModel: form.deviceModel || undefined,
        deviceSerial: form.deviceSerial || undefined,
        reportedFault: form.reportedFault || undefined,
        estimatedCost: form.estimatedCost || undefined,
        advancePaid: form.advancePaid || undefined,
        note: form.note || undefined,
      });
    },
    onSuccess: () => {
      toast({ title: 'Repair order created', variant: 'success' });
      setCreateOpen(false);
      setForm(emptyForm);
      void queryClient.invalidateQueries({ queryKey: ['repairs'] });
    },
    onError: (err) => {
      toast({
        title: 'Create failed',
        description: err instanceof ApiError ? err.message : 'Could not create repair',
        variant: 'destructive',
      });
    },
  });

  const transitionMutation = useMutation({
    mutationFn: async ({ id, status }: { id: string; status: RepairStatus }) => {
      return api.post<Repair>(`/api/v1/repairs/${id}/transition`, { status });
    },
    onSuccess: (updated) => {
      toast({ title: 'Status updated', variant: 'success' });
      if (selectedRepair && selectedRepair.id === updated.id) {
        setSelectedRepair(updated);
      }
      void queryClient.invalidateQueries({ queryKey: ['repairs'] });
    },
    onError: (err) => {
      toast({
        title: 'Update failed',
        description: err instanceof ApiError ? err.message : 'Could not update status',
        variant: 'destructive',
      });
    },
  });

  const paymentMutation = useMutation({
    mutationFn: async ({ id, amount }: { id: string; amount: number }) => {
      return api.post<Repair>(`/api/v1/repairs/${id}/payments`, {
        method: 'CASH',
        amount,
      });
    },
    onSuccess: (updated) => {
      toast({ title: 'Payment recorded', variant: 'success' });
      setSelectedRepair(updated);
      setPaymentOpen(false);
      setPaymentAmount(0);
      void queryClient.invalidateQueries({ queryKey: ['repairs'] });
    },
    onError: (err) => {
      toast({
        title: 'Payment failed',
        description: err instanceof ApiError ? err.message : 'Could not record payment',
        variant: 'destructive',
      });
    },
  });

  const addLineMutation = useMutation({
    mutationFn: async (id: string) => {
      return api.post<Repair>(`/api/v1/repairs/${id}/lines`, {
        lineType: lineForm.lineType,
        itemId: lineForm.lineType === 'PART' ? lineForm.itemId || undefined : undefined,
        description: lineForm.description,
        quantity: lineForm.quantity,
        unitPrice: lineForm.unitPrice,
      });
    },
    onSuccess: (updated) => {
      toast({ title: 'Line added', variant: 'success' });
      setSelectedRepair(updated);
      setLineOpen(false);
      setLineForm({ lineType: 'PART', itemId: '', description: '', quantity: 1, unitPrice: 0 });
      void queryClient.invalidateQueries({ queryKey: ['repairs'] });
    },
    onError: (err) => {
      toast({
        title: 'Could not add line',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const refundMutation = useMutation({
    mutationFn: async (id: string) => {
      return api.post<Repair>(`/api/v1/repairs/${id}/refund`, {
        amount: refundForm.amount,
        method: 'CASH',
        reason: refundForm.reason || undefined,
        restoreParts: false,
      });
    },
    onSuccess: (updated) => {
      toast({ title: 'Refund issued', variant: 'success' });
      setSelectedRepair(updated);
      setRefundOpen(false);
      setRefundForm({ amount: 0, reason: '' });
      void queryClient.invalidateQueries({ queryKey: ['repairs'] });
    },
    onError: (err) => {
      toast({
        title: 'Refund failed',
        description: err instanceof ApiError ? err.message : 'Could not process refund',
        variant: 'destructive',
      });
    },
  });

  function printRepairSlip(repair: Repair) {
    const win = window.open('', '_blank', 'width=420,height=640');
    if (!win) return;
    const row = (label: string, value: string) =>
      `<div style="display:flex;justify-content:space-between;gap:12px;"><span>${label}</span><span>${value}</span></div>`;
    win.document.write(`
      <html>
        <head>
          <title>Repair ${repair.repairNumber}</title>
          <style>
            body { font-family: 'Courier New', monospace; padding: 16px; font-size: 13px; }
            h2 { text-align: center; margin-bottom: 4px; }
            hr { border: none; border-top: 1px dashed #000; margin: 10px 0; }
          </style>
        </head>
        <body>
          <h2>Repair Slip</h2>
          ${row('Repair #', repair.repairNumber)}
          ${row('Date', new Date(repair.receivedAt).toLocaleDateString())}
          <hr />
          ${row('Customer', repair.customerName)}
          ${repair.customerPhone ? row('Phone', repair.customerPhone) : ''}
          ${row('Device', deviceSummary(repair))}
          ${repair.deviceSerial ? row('Serial', repair.deviceSerial) : ''}
          <hr />
          <p><b>Complaint:</b><br/>${repair.reportedFault ?? ''}</p>
          <hr />
          ${row('Status', STATUS_LABELS[repair.status] ?? repair.status)}
          ${repair.estimatedCost ? row('Estimated Cost', money(repair.estimatedCost)) : ''}
          ${repair.grandTotal ? row('Total', money(repair.grandTotal)) : ''}
          ${repair.amountPaid ? row('Paid', money(repair.amountPaid)) : ''}
          ${repair.balanceDue ? row('Balance Due', money(repair.balanceDue)) : ''}
        </body>
      </html>
    `);
    win.document.close();
    win.focus();
    win.print();
  }

  function openCreate() {
    setForm(emptyForm);
    setCreateOpen(true);
  }

  function openView(repair: Repair) {
    setSelectedRepair(repair);
    setViewOpen(true);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    createMutation.mutate();
  }

  function getStatusBadgeVariant(status: RepairStatus) {
    switch (status) {
      case 'RECEIVED':
        return 'info';
      case 'DIAGNOSING':
      case 'AWAITING_APPROVAL':
      case 'AWAITING_PARTS':
      case 'IN_PROGRESS':
        return 'secondary';
      case 'COMPLETED':
        return 'success';
      case 'DELIVERED':
        return 'navy';
      case 'CANCELLED':
      case 'IRREPARABLE':
        return 'destructive';
      default:
        return 'default';
    }
  }

  return (
    <div>
      <PageHeader
        title="Repairs"
        description={`Manage device repairs and service orders. Total: ${allRepairs.length}`}
        actions={
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4" />
            New Repair
          </Button>
        }
      />

      <div className="mb-4 flex max-w-2xl flex-col gap-4 sm:flex-row">
        <Input
          placeholder="Search repairs..."
          value={q}
          onChange={(e) => setQ(e.target.value)}
          className="flex-1"
        />
        <div className="w-full sm:w-56">
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger>
              <SelectValue placeholder="All Statuses" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Statuses</SelectItem>
              {(Object.keys(STATUS_LABELS) as RepairStatus[]).map((s) => (
                <SelectItem key={s} value={s}>
                  {STATUS_LABELS[s]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {repairsQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : allRepairs.length === 0 ? (
        <EmptyState
          title="No repairs found"
          description="Create a new repair order to get started."
          action={
            <Button onClick={openCreate}>
              <Plus className="h-4 w-4" />
              New Repair
            </Button>
          }
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Repair #</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Device</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Balance Due</TableHead>
                <TableHead className="w-[80px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {allRepairs.map((r) => (
                <TableRow key={r.id} className="cursor-pointer" onClick={() => openView(r)}>
                  <TableCell className="text-navy font-medium">{r.repairNumber}</TableCell>
                  <TableCell>{r.customerName}</TableCell>
                  <TableCell>
                    <div className="line-clamp-1 max-w-[200px]" title={deviceSummary(r)}>
                      {deviceSummary(r)}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={getStatusBadgeVariant(r.status) as any}>
                      {STATUS_LABELS[r.status] ?? r.status}
                    </Badge>
                  </TableCell>
                  <TableCell>{new Date(r.receivedAt).toLocaleDateString()}</TableCell>
                  <TableCell>{r.grandTotal ? money(r.grandTotal) : '—'}</TableCell>
                  <TableCell>
                    {r.balanceDue ? (
                      <span className="font-medium text-amber-600">{money(r.balanceDue)}</span>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" aria-label="View repair">
                      <Eye className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {/* CREATE DIALOG */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>New Repair Order</DialogTitle>
            <DialogDescription>Create a new device repair ticket.</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="customerName">Customer Name</Label>
              <Input
                id="customerName"
                required
                autoFocus
                value={form.customerName}
                onChange={(e) => setForm((f) => ({ ...f, customerName: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="customerPhone">Customer Phone</Label>
              <Input
                id="customerPhone"
                value={form.customerPhone}
                onChange={(e) => setForm((f) => ({ ...f, customerPhone: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="deviceType">Device Type</Label>
              <Input
                id="deviceType"
                placeholder="e.g. Phone, Laptop"
                value={form.deviceType}
                onChange={(e) => setForm((f) => ({ ...f, deviceType: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="deviceBrand">Brand</Label>
              <Input
                id="deviceBrand"
                value={form.deviceBrand}
                onChange={(e) => setForm((f) => ({ ...f, deviceBrand: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="deviceModel">Model</Label>
              <Input
                id="deviceModel"
                value={form.deviceModel}
                onChange={(e) => setForm((f) => ({ ...f, deviceModel: e.target.value }))}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="deviceSerial">Serial Number / IMEI</Label>
              <Input
                id="deviceSerial"
                value={form.deviceSerial}
                onChange={(e) => setForm((f) => ({ ...f, deviceSerial: e.target.value }))}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="reportedFault">Complaint / Issue</Label>
              <textarea
                id="reportedFault"
                required
                className="border-input placeholder:text-muted-foreground focus-visible:ring-ring flex min-h-[80px] w-full rounded-md border bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 disabled:cursor-not-allowed disabled:opacity-50"
                value={form.reportedFault}
                onChange={(e) => setForm((f) => ({ ...f, reportedFault: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="estimatedCost">Estimated Cost</Label>
              <Input
                id="estimatedCost"
                type="number"
                step="0.01"
                value={form.estimatedCost || ''}
                onChange={(e) =>
                  setForm((f) => ({ ...f, estimatedCost: parseFloat(e.target.value) || 0 }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="advancePaid">Advance Payment</Label>
              <Input
                id="advancePaid"
                type="number"
                step="0.01"
                value={form.advancePaid || ''}
                onChange={(e) =>
                  setForm((f) => ({ ...f, advancePaid: parseFloat(e.target.value) || 0 }))
                }
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="note">Note</Label>
              <Input
                id="note"
                value={form.note}
                onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
              />
            </div>
            <DialogFooter className="mt-4 sm:col-span-2">
              <Button type="button" variant="outline" onClick={() => setCreateOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Saving...' : 'Create Repair'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* PAYMENT DIALOG */}
      <Dialog open={paymentOpen} onOpenChange={setPaymentOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Collect Payment</DialogTitle>
            <DialogDescription>
              Balance due: {selectedRepair ? money(selectedRepair.balanceDue ?? 0) : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 py-4">
            <Label htmlFor="paymentAmount">Amount (Cash)</Label>
            <Input
              id="paymentAmount"
              type="number"
              step="0.01"
              autoFocus
              value={paymentAmount || ''}
              onChange={(e) => setPaymentAmount(parseFloat(e.target.value) || 0)}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPaymentOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={paymentMutation.isPending || paymentAmount <= 0}
              onClick={() => {
                if (selectedRepair) {
                  paymentMutation.mutate({ id: selectedRepair.id, amount: paymentAmount });
                }
              }}
            >
              {paymentMutation.isPending ? 'Recording...' : 'Record Payment'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* VIEW DIALOG */}
      <Dialog open={viewOpen} onOpenChange={setViewOpen}>
        <DialogContent className="max-h-[90vh] max-w-2xl overflow-y-auto">
          {selectedRepair && (
            <>
              <DialogHeader>
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <DialogTitle className="flex items-center gap-2">
                      <PenTool className="h-5 w-5" />
                      Repair {selectedRepair.repairNumber}
                    </DialogTitle>
                    <DialogDescription>
                      Received on {new Date(selectedRepair.receivedAt).toLocaleDateString()}
                    </DialogDescription>
                  </div>
                  <Badge
                    variant={getStatusBadgeVariant(selectedRepair.status) as any}
                    className="text-sm"
                  >
                    {STATUS_LABELS[selectedRepair.status] ?? selectedRepair.status}
                  </Badge>
                </div>
              </DialogHeader>

              <div className="grid gap-6 py-4">
                <div className="bg-muted grid gap-4 rounded-lg p-4 text-sm sm:grid-cols-2">
                  <div>
                    <p className="text-muted-foreground mb-1 font-semibold">Customer</p>
                    <p>
                      {selectedRepair.customerName}
                      {selectedRepair.customerPhone ? ` · ${selectedRepair.customerPhone}` : ''}
                    </p>
                  </div>
                  <div>
                    <p className="text-muted-foreground mb-1 font-semibold">Device</p>
                    <p>{deviceSummary(selectedRepair)}</p>
                  </div>
                  {selectedRepair.deviceSerial && (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Serial / IMEI</p>
                      <p>{selectedRepair.deviceSerial}</p>
                    </div>
                  )}
                  <div className="sm:col-span-2">
                    <p className="text-muted-foreground mb-1 font-semibold">Complaint</p>
                    <p className="whitespace-pre-wrap">{selectedRepair.reportedFault}</p>
                  </div>
                  {selectedRepair.diagnosis && (
                    <div className="sm:col-span-2">
                      <p className="text-muted-foreground mb-1 font-semibold">Diagnosis</p>
                      <p className="whitespace-pre-wrap">{selectedRepair.diagnosis}</p>
                    </div>
                  )}
                  {selectedRepair.estimatedCost ? (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Estimated Cost</p>
                      <p>{money(selectedRepair.estimatedCost)}</p>
                    </div>
                  ) : null}
                  {selectedRepair.grandTotal ? (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Total Amount</p>
                      <p className="text-navy font-medium">{money(selectedRepair.grandTotal)}</p>
                    </div>
                  ) : null}
                  {selectedRepair.amountPaid ? (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Amount Paid</p>
                      <p>{money(selectedRepair.amountPaid)}</p>
                    </div>
                  ) : null}
                  {selectedRepair.balanceDue ? (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Balance Due</p>
                      <p className="font-medium text-amber-600">
                        {money(selectedRepair.balanceDue)}
                      </p>
                    </div>
                  ) : null}
                </div>

                <div>
                  <div className="mb-3 flex items-center justify-between">
                    <h3 className="font-semibold">Parts / Charges</h3>
                    {!TERMINAL_STATUSES.includes(selectedRepair.status) && (
                      <Button type="button" variant="outline" size="sm" onClick={() => setLineOpen(true)}>
                        <Plus className="h-3 w-3" />
                        Add Line
                      </Button>
                    )}
                  </div>
                  {selectedRepair.lines && selectedRepair.lines.length > 0 ? (
                    <div className="space-y-1 text-sm">
                      {selectedRepair.lines.map((line) => (
                        <div key={line.id} className="flex justify-between">
                          <span>
                            {line.description} × {line.quantity}
                          </span>
                          <span>{money(line.lineTotal)}</span>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-muted-foreground text-sm">No parts or charges added yet.</p>
                  )}
                </div>

                {selectedRepair.history && selectedRepair.history.length > 0 && (
                  <div>
                    <h3 className="mb-3 font-semibold">Status History</h3>
                    <div className="space-y-3">
                      {selectedRepair.history.map((h) => (
                        <div key={h.id} className="flex gap-4 text-sm">
                          <div className="text-muted-foreground whitespace-nowrap">
                            {new Date(h.changedAt).toLocaleDateString()}
                          </div>
                          <div>
                            <Badge variant={getStatusBadgeVariant(h.toStatus) as any}>
                              {STATUS_LABELS[h.toStatus] ?? h.toStatus}
                            </Badge>
                            {h.note && <p className="text-muted-foreground mt-1">{h.note}</p>}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              <DialogFooter className="flex-wrap gap-2 sm:justify-between">
                {!TERMINAL_STATUSES.includes(selectedRepair.status) ? (
                  <Button
                    variant="destructive"
                    onClick={() =>
                      transitionMutation.mutate({ id: selectedRepair.id, status: 'CANCELLED' })
                    }
                    disabled={transitionMutation.isPending}
                  >
                    Cancel Repair
                  </Button>
                ) : (
                  <div></div>
                )}
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => printRepairSlip(selectedRepair)}
                  >
                    <Printer className="h-4 w-4" />
                    Print
                  </Button>
                  {(selectedRepair.amountPaid ?? 0) > 0 && (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => {
                        setRefundForm({ amount: selectedRepair.amountPaid ?? 0, reason: '' });
                        setRefundOpen(true);
                      }}
                    >
                      <Undo2 className="h-4 w-4" />
                      Refund
                    </Button>
                  )}
                  {(selectedRepair.balanceDue ?? 0) > 0 && (
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => {
                        setPaymentAmount(selectedRepair.balanceDue ?? 0);
                        setPaymentOpen(true);
                      }}
                    >
                      Collect Payment
                    </Button>
                  )}
                  <Button type="button" variant="outline" onClick={() => setViewOpen(false)}>
                    Close
                  </Button>
                  {NEXT_STATUS[selectedRepair.status] && (
                    <Button
                      onClick={() =>
                        transitionMutation.mutate({
                          id: selectedRepair.id,
                          status: NEXT_STATUS[selectedRepair.status] as RepairStatus,
                        })
                      }
                      disabled={transitionMutation.isPending}
                    >
                      {transitionMutation.isPending
                        ? 'Updating...'
                        : `Mark as ${STATUS_LABELS[NEXT_STATUS[selectedRepair.status] as RepairStatus]}`}
                    </Button>
                  )}
                </div>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* ADD LINE DIALOG */}
      <Dialog open={lineOpen} onOpenChange={setLineOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Add Part / Charge</DialogTitle>
            <DialogDescription>Add a part, labour, or service charge to this repair.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>Type</Label>
              <Select
                value={lineForm.lineType}
                onValueChange={(v) =>
                  setLineForm((f) => ({ ...f, lineType: v as typeof f.lineType, itemId: '' }))
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="PART">Part</SelectItem>
                  <SelectItem value="LABOUR">Labour</SelectItem>
                  <SelectItem value="SERVICE">Service</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {lineForm.lineType === 'PART' && (
              <div className="space-y-2">
                <Label>Item</Label>
                <Select
                  value={lineForm.itemId}
                  onValueChange={(v) => {
                    const item = items.find((i) => i.id === v);
                    setLineForm((f) => ({
                      ...f,
                      itemId: v,
                      description: item ? item.name : f.description,
                      unitPrice: item ? Number(item.retailPrice) : f.unitPrice,
                    }));
                  }}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select a stock item" />
                  </SelectTrigger>
                  <SelectContent>
                    {items.map((item) => (
                      <SelectItem key={item.id} value={item.id}>
                        {item.name} ({money(item.retailPrice)})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
            <div className="space-y-2">
              <Label htmlFor="lineDescription">Description</Label>
              <Input
                id="lineDescription"
                required
                value={lineForm.description}
                onChange={(e) => setLineForm((f) => ({ ...f, description: e.target.value }))}
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="lineQuantity">Quantity</Label>
                <Input
                  id="lineQuantity"
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={lineForm.quantity || ''}
                  onChange={(e) =>
                    setLineForm((f) => ({ ...f, quantity: Number(e.target.value) || 0 }))
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="lineUnitPrice">Unit Price</Label>
                <Input
                  id="lineUnitPrice"
                  type="number"
                  step="0.01"
                  value={lineForm.unitPrice || ''}
                  onChange={(e) =>
                    setLineForm((f) => ({ ...f, unitPrice: Number(e.target.value) || 0 }))
                  }
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setLineOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={
                addLineMutation.isPending ||
                !lineForm.description ||
                lineForm.quantity <= 0 ||
                (lineForm.lineType === 'PART' && !lineForm.itemId)
              }
              onClick={() => {
                if (selectedRepair) addLineMutation.mutate(selectedRepair.id);
              }}
            >
              {addLineMutation.isPending ? 'Adding…' : 'Add Line'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* REFUND DIALOG */}
      <Dialog open={refundOpen} onOpenChange={setRefundOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Refund Repair Payment</DialogTitle>
            <DialogDescription>
              Amount paid so far: {selectedRepair ? money(selectedRepair.amountPaid ?? 0) : ''}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label htmlFor="refundAmount">Refund Amount</Label>
              <Input
                id="refundAmount"
                type="number"
                step="0.01"
                autoFocus
                value={refundForm.amount || ''}
                onChange={(e) =>
                  setRefundForm((f) => ({ ...f, amount: Number(e.target.value) || 0 }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="refundReason">Reason</Label>
              <Input
                id="refundReason"
                value={refundForm.reason}
                onChange={(e) => setRefundForm((f) => ({ ...f, reason: e.target.value }))}
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setRefundOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={refundMutation.isPending || refundForm.amount <= 0}
              onClick={() => {
                if (selectedRepair) refundMutation.mutate(selectedRepair.id);
              }}
            >
              {refundMutation.isPending ? 'Processing…' : 'Issue Refund'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
