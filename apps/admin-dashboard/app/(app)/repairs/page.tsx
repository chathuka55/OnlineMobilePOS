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
import { Eye, Plus, PenTool } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, money } from '@/lib/api';

export type RepairStatus =
  'RECEIVED' | 'DIAGNOSING' | 'IN_PROGRESS' | 'READY' | 'DELIVERED' | 'CANCELLED';

export interface RepairHistory {
  id: string;
  status: RepairStatus;
  notes?: string;
  createdAt: string;
}

export interface Repair {
  id: string;
  repairNumber: string;
  customerName: string;
  deviceDescription: string;
  deviceBrand?: string;
  deviceModel?: string;
  serialNumber?: string;
  complaint?: string;
  estimatedCost?: number;
  totalAmount?: number;
  technicianNote?: string;
  status: RepairStatus;
  technicianName?: string;
  createdAt: string;
  history?: RepairHistory[];
}

export interface RepairRequest {
  customerName: string;
  deviceDescription: string;
  deviceBrand: string;
  deviceModel: string;
  serialNumber: string;
  complaint: string;
  estimatedCost: number;
  technicianNote: string;
}

const emptyForm: RepairRequest = {
  customerName: '',
  deviceDescription: '',
  deviceBrand: '',
  deviceModel: '',
  serialNumber: '',
  complaint: '',
  estimatedCost: 0,
  technicianNote: '',
};

export default function RepairsPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [createOpen, setCreateOpen] = useState(false);
  const [viewOpen, setViewOpen] = useState(false);
  const [selectedRepair, setSelectedRepair] = useState<Repair | null>(null);
  const [form, setForm] = useState<RepairRequest>(emptyForm);

  const repairsQuery = useQuery({
    queryKey: ['repairs', q, statusFilter],
    queryFn: () => api.get<any>(`/api/v1/repairs?size=100&q=${encodeURIComponent(q)}`),
  });

  const allRepairs = useMemo(() => {
    const data = repairsQuery.data;
    const items: Repair[] = Array.isArray(data) ? data : data?.content || [];
    if (statusFilter !== 'ALL') {
      return items.filter((r) => r.status === statusFilter);
    }
    return items;
  }, [repairsQuery.data, statusFilter]);

  const createMutation = useMutation({
    mutationFn: async () => {
      return api.post<Repair>('/api/v1/repairs', form);
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

  const updateStatusMutation = useMutation({
    mutationFn: async ({ id, status }: { id: string; status: RepairStatus }) => {
      return api.patch<Repair>(`/api/v1/repairs/${id}`, { status });
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
        return 'secondary';
      case 'IN_PROGRESS':
        return 'secondary';
      case 'READY':
        return 'success';
      case 'DELIVERED':
        return 'navy';
      case 'CANCELLED':
        return 'destructive';
      default:
        return 'default';
    }
  }

  const statusProgression: Record<string, RepairStatus | null> = {
    RECEIVED: 'DIAGNOSING',
    DIAGNOSING: 'IN_PROGRESS',
    IN_PROGRESS: 'READY',
    READY: 'DELIVERED',
    DELIVERED: null,
    CANCELLED: null,
  };

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
        <div className="w-full sm:w-48">
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger>
              <SelectValue placeholder="All Statuses" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Statuses</SelectItem>
              <SelectItem value="RECEIVED">Received</SelectItem>
              <SelectItem value="DIAGNOSING">Diagnosing</SelectItem>
              <SelectItem value="IN_PROGRESS">In Progress</SelectItem>
              <SelectItem value="READY">Ready</SelectItem>
              <SelectItem value="DELIVERED">Delivered</SelectItem>
              <SelectItem value="CANCELLED">Cancelled</SelectItem>
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
                <TableHead>Device/Description</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Technician</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Total</TableHead>
                <TableHead className="w-[80px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {allRepairs.map((r) => (
                <TableRow key={r.id} className="cursor-pointer" onClick={() => openView(r)}>
                  <TableCell className="text-navy font-medium">{r.repairNumber}</TableCell>
                  <TableCell>{r.customerName}</TableCell>
                  <TableCell>
                    <div className="line-clamp-1 max-w-[200px]" title={r.deviceDescription}>
                      {r.deviceDescription}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={getStatusBadgeVariant(r.status) as any}>{r.status}</Badge>
                  </TableCell>
                  <TableCell>{r.technicianName || '—'}</TableCell>
                  <TableCell>{new Date(r.createdAt).toLocaleDateString()}</TableCell>
                  <TableCell>{r.totalAmount ? money(r.totalAmount) : '—'}</TableCell>
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
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="deviceDescription">Device Description</Label>
              <Input
                id="deviceDescription"
                required
                value={form.deviceDescription}
                onChange={(e) => setForm((f) => ({ ...f, deviceDescription: e.target.value }))}
                placeholder="e.g. iPhone 13 Pro Max - Black"
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
              <Label htmlFor="serialNumber">Serial Number / IMEI</Label>
              <Input
                id="serialNumber"
                value={form.serialNumber}
                onChange={(e) => setForm((f) => ({ ...f, serialNumber: e.target.value }))}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="complaint">Complaint / Issue</Label>
              <textarea
                id="complaint"
                required
                className="border-input placeholder:text-muted-foreground focus-visible:ring-ring flex min-h-[80px] w-full rounded-md border bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 disabled:cursor-not-allowed disabled:opacity-50"
                value={form.complaint}
                onChange={(e) => setForm((f) => ({ ...f, complaint: e.target.value }))}
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
              <Label htmlFor="technicianNote">Technician Note</Label>
              <Input
                id="technicianNote"
                value={form.technicianNote}
                onChange={(e) => setForm((f) => ({ ...f, technicianNote: e.target.value }))}
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
                      Created on {new Date(selectedRepair.createdAt).toLocaleDateString()}
                    </DialogDescription>
                  </div>
                  <Badge
                    variant={getStatusBadgeVariant(selectedRepair.status) as any}
                    className="text-sm"
                  >
                    {selectedRepair.status}
                  </Badge>
                </div>
              </DialogHeader>

              <div className="grid gap-6 py-4">
                <div className="bg-muted grid gap-4 rounded-lg p-4 text-sm sm:grid-cols-2">
                  <div>
                    <p className="text-muted-foreground mb-1 font-semibold">Customer</p>
                    <p>{selectedRepair.customerName}</p>
                  </div>
                  <div>
                    <p className="text-muted-foreground mb-1 font-semibold">Device</p>
                    <p>{selectedRepair.deviceDescription}</p>
                  </div>
                  {(selectedRepair.deviceBrand || selectedRepair.deviceModel) && (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Brand / Model</p>
                      <p>
                        {selectedRepair.deviceBrand} {selectedRepair.deviceModel}
                      </p>
                    </div>
                  )}
                  {selectedRepair.serialNumber && (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Serial / IMEI</p>
                      <p>{selectedRepair.serialNumber}</p>
                    </div>
                  )}
                  <div className="sm:col-span-2">
                    <p className="text-muted-foreground mb-1 font-semibold">Complaint</p>
                    <p className="whitespace-pre-wrap">{selectedRepair.complaint}</p>
                  </div>
                  {selectedRepair.estimatedCost && (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Estimated Cost</p>
                      <p>{money(selectedRepair.estimatedCost)}</p>
                    </div>
                  )}
                  {selectedRepair.totalAmount && (
                    <div>
                      <p className="text-muted-foreground mb-1 font-semibold">Total Amount</p>
                      <p className="text-navy font-medium">{money(selectedRepair.totalAmount)}</p>
                    </div>
                  )}
                </div>

                {selectedRepair.history && selectedRepair.history.length > 0 && (
                  <div>
                    <h3 className="mb-3 font-semibold">Status History</h3>
                    <div className="space-y-3">
                      {selectedRepair.history.map((h) => (
                        <div key={h.id} className="flex gap-4 text-sm">
                          <div className="text-muted-foreground whitespace-nowrap">
                            {new Date(h.createdAt).toLocaleDateString()}
                          </div>
                          <div>
                            <Badge variant={getStatusBadgeVariant(h.status) as any}>
                              {h.status}
                            </Badge>
                            {h.notes && <p className="text-muted-foreground mt-1">{h.notes}</p>}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>

              <DialogFooter className="sm:justify-between">
                {selectedRepair.status !== 'CANCELLED' && selectedRepair.status !== 'DELIVERED' ? (
                  <Button
                    variant="destructive"
                    onClick={() =>
                      updateStatusMutation.mutate({ id: selectedRepair.id, status: 'CANCELLED' })
                    }
                    disabled={updateStatusMutation.isPending}
                  >
                    Cancel Repair
                  </Button>
                ) : (
                  <div></div>
                )}
                <div className="flex gap-2">
                  <Button type="button" variant="outline" onClick={() => setViewOpen(false)}>
                    Close
                  </Button>
                  {statusProgression[selectedRepair.status] && (
                    <Button
                      onClick={() =>
                        updateStatusMutation.mutate({
                          id: selectedRepair.id,
                          status: statusProgression[selectedRepair.status] as RepairStatus,
                        })
                      }
                      disabled={updateStatusMutation.isPending}
                    >
                      {updateStatusMutation.isPending
                        ? 'Updating...'
                        : `Mark as ${statusProgression[selectedRepair.status]}`}
                    </Button>
                  )}
                </div>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
