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
  Spinner,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  toast,
} from '@possaas/ui';
import { Plus, Trash2, CheckCircle2 } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, money } from '@/lib/api';

export type QuotationStatus = 'DRAFT' | 'SENT' | 'ACCEPTED' | 'EXPIRED';

export interface QuotationLine {
  id?: string;
  itemName: string;
  quantity: number;
  unitPrice: number;
  total?: number;
}

export interface Quotation {
  id: string;
  quotationNumber: string;
  customerName: string;
  date: string;
  validUntil: string;
  total: number;
  status: QuotationStatus;
  note?: string;
  lines: QuotationLine[];
}

export interface QuotationRequest {
  customerName: string;
  validDays: number;
  note?: string;
  lines: Omit<QuotationLine, 'id' | 'total'>[];
}

const emptyForm: QuotationRequest = {
  customerName: '',
  validDays: 30,
  note: '',
  lines: [{ itemName: '', quantity: 1, unitPrice: 0 }],
};

export default function QuotationsPage() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<QuotationRequest>(emptyForm);

  const quotesQuery = useQuery({
    queryKey: ['quotations'],
    queryFn: () => api.get<any>('/api/v1/quotations?size=100'),
  });

  const quotes = useMemo(() => {
    const data = quotesQuery.data;
    return Array.isArray(data) ? data : data?.content || [];
  }, [quotesQuery.data]);

  const createMutation = useMutation({
    mutationFn: async () => {
      return api.post<Quotation>('/api/v1/quotations', form);
    },
    onSuccess: () => {
      toast({ title: 'Quotation created', variant: 'success' });
      setOpen(false);
      setForm(emptyForm);
      void queryClient.invalidateQueries({ queryKey: ['quotations'] });
    },
    onError: (err) => {
      toast({
        title: 'Create failed',
        description: err instanceof ApiError ? err.message : 'Could not create quotation',
        variant: 'destructive',
      });
    },
  });

  const convertMutation = useMutation({
    mutationFn: async (id: string) => {
      return api.post(`/api/v1/quotations/${id}/convert-to-bill`, {});
    },
    onSuccess: () => {
      toast({ title: 'Converted to bill successfully', variant: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['quotations'] });
    },
    onError: (err) => {
      toast({
        title: 'Conversion failed',
        description: err instanceof ApiError ? err.message : 'Could not convert to bill',
        variant: 'destructive',
      });
    },
  });

  function openCreate() {
    setForm(emptyForm);
    setOpen(true);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (form.lines.length === 0) {
      toast({
        title: 'Validation error',
        description: 'At least one line item is required',
        variant: 'destructive',
      });
      return;
    }
    createMutation.mutate();
  }

  function addLine() {
    setForm((f) => ({
      ...f,
      lines: [...f.lines, { itemName: '', quantity: 1, unitPrice: 0 }],
    }));
  }

  function removeLine(index: number) {
    setForm((f) => ({
      ...f,
      lines: f.lines.filter((_, i) => i !== index),
    }));
  }

  function updateLine(index: number, field: keyof Omit<QuotationLine, 'id' | 'total'>, value: any) {
    setForm((f) => {
      const newLines = [...f.lines];
      newLines[index] = { ...newLines[index], [field]: value } as Omit<
        QuotationLine,
        'id' | 'total'
      >;
      return { ...f, lines: newLines };
    });
  }

  function getStatusBadgeVariant(status: QuotationStatus) {
    switch (status) {
      case 'DRAFT':
        return 'secondary';
      case 'SENT':
        return 'info';
      case 'ACCEPTED':
        return 'success';
      case 'EXPIRED':
        return 'destructive';
      default:
        return 'default';
    }
  }

  return (
    <div>
      <PageHeader
        title="Quotations"
        description="Create and manage price estimates for customers."
        actions={
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4" />
            New Quotation
          </Button>
        }
      />

      {quotesQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : quotes.length === 0 ? (
        <EmptyState
          title="No quotations"
          description="Create a quotation to give pricing estimates to customers."
          action={
            <Button onClick={openCreate}>
              <Plus className="h-4 w-4" />
              New Quotation
            </Button>
          }
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Quotation #</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Date</TableHead>
                <TableHead>Valid Until</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-[120px] text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {quotes.map((q: Quotation) => (
                <TableRow key={q.id}>
                  <TableCell className="text-navy font-medium">{q.quotationNumber}</TableCell>
                  <TableCell>{q.customerName}</TableCell>
                  <TableCell>{new Date(q.date).toLocaleDateString()}</TableCell>
                  <TableCell>{new Date(q.validUntil).toLocaleDateString()}</TableCell>
                  <TableCell>{money(q.total)}</TableCell>
                  <TableCell>
                    <Badge variant={getStatusBadgeVariant(q.status) as any}>{q.status}</Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    {q.status === 'ACCEPTED' && (
                      <Button
                        variant="ghost"
                        size="sm"
                        title="Convert to Bill"
                        onClick={() => convertMutation.mutate(q.id)}
                        disabled={convertMutation.isPending}
                        className="text-success hover:text-success hover:bg-success/10"
                      >
                        <CheckCircle2 className="mr-2 h-4 w-4" />
                        Bill
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[90vh] max-w-3xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>New Quotation</DialogTitle>
            <DialogDescription>Create a new price quotation.</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4" onSubmit={onSubmit}>
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
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
                <Label htmlFor="validDays">Valid For (Days)</Label>
                <Input
                  id="validDays"
                  type="number"
                  min="1"
                  required
                  value={form.validDays}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, validDays: parseInt(e.target.value) || 30 }))
                  }
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="note">Note / Terms</Label>
              <textarea
                id="note"
                className="border-input placeholder:text-muted-foreground focus-visible:ring-ring flex min-h-[60px] w-full rounded-md border bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 disabled:cursor-not-allowed disabled:opacity-50"
                value={form.note}
                onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
              />
            </div>

            <div className="bg-muted/30 mt-4 rounded-md border p-4">
              <div className="mb-4 flex items-center justify-between">
                <Label className="text-base font-semibold">Line Items</Label>
                <Button type="button" variant="outline" size="sm" onClick={addLine}>
                  <Plus className="mr-1 h-4 w-4" /> Add Row
                </Button>
              </div>

              <div className="space-y-3">
                {form.lines.map((line, idx) => (
                  <div key={idx} className="flex items-start gap-2">
                    <div className="flex-1 space-y-1">
                      <Input
                        placeholder="Item Description"
                        required
                        value={line.itemName}
                        onChange={(e) => updateLine(idx, 'itemName', e.target.value)}
                      />
                    </div>
                    <div className="w-24 space-y-1">
                      <Input
                        type="number"
                        min="0.01"
                        step="0.01"
                        placeholder="Qty"
                        required
                        value={line.quantity || ''}
                        onChange={(e) =>
                          updateLine(idx, 'quantity', parseFloat(e.target.value) || 0)
                        }
                      />
                    </div>
                    <div className="w-32 space-y-1">
                      <Input
                        type="number"
                        min="0"
                        step="0.01"
                        placeholder="Unit Price"
                        required
                        value={line.unitPrice || ''}
                        onChange={(e) =>
                          updateLine(idx, 'unitPrice', parseFloat(e.target.value) || 0)
                        }
                      />
                    </div>
                    <div className="w-32 pt-2 text-right font-medium">
                      {money((line.quantity || 0) * (line.unitPrice || 0))}
                    </div>
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      className="text-destructive"
                      onClick={() => removeLine(idx)}
                      disabled={form.lines.length === 1}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>
              <div className="mt-4 flex justify-end border-t pt-4">
                <div className="text-right">
                  <p className="text-muted-foreground text-sm">Total</p>
                  <p className="text-navy text-xl font-bold">
                    {money(
                      form.lines.reduce(
                        (sum, line) => sum + (line.quantity || 0) * (line.unitPrice || 0),
                        0,
                      ),
                    )}
                  </p>
                </div>
              </div>
            </div>

            <DialogFooter className="mt-4">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={createMutation.isPending}>
                {createMutation.isPending ? 'Saving...' : 'Create Quotation'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
