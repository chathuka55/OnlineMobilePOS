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
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@possaas/ui';
import { Plus } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

interface WholesaleInvoice {
  id: string;
  invoiceNumber: string;
  customerId: string;
  customerName: string;
  date: string;
  dueDate: string;
  totalAmount: number;
  paidAmount: number;
  balance: number;
  status: 'DRAFT' | 'ISSUED' | 'PARTIAL' | 'PAID' | 'OVERDUE' | 'VOID';
}

interface WholesaleInvoiceLine {
  itemName: string;
  quantity: number;
  unitPrice: number;
}

interface CreditLedgerEntry {
  id: string;
  type: 'SALE' | 'PAYMENT' | 'ADJUSTMENT';
  amount: number;
  balance: number;
  date: string;
  reference: string;
}

export default function WholesalePage() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  const [form, setForm] = useState<{
    customerId: string;
    dueDate: string;
    reference: string;
    lines: WholesaleInvoiceLine[];
  }>({
    customerId: '',
    dueDate: new Date().toISOString().split('T')[0] ?? '',
    reference: '',
    lines: [],
  });

  const [ledgerCustomerId, setLedgerCustomerId] = useState<string>('all');

  const customersQuery = useQuery({
    queryKey: ['customers-wholesale'],
    queryFn: () => api.customers.list({ size: 100 }),
  });
  const customers = useMemo(
    () => asList(customersQuery.data).filter((c) => c.customerType === 'WHOLESALE'),
    [customersQuery.data],
  );

  const invoicesQuery = useQuery({
    queryKey: ['wholesale-invoices'],
    queryFn: () => api.get<WholesaleInvoice[]>('/api/v1/wholesale/invoices', { size: 100 }),
  });
  const invoices = useMemo(() => asList(invoicesQuery.data), [invoicesQuery.data]);

  const ledgerQuery = useQuery({
    queryKey: ['wholesale-ledger', ledgerCustomerId],
    queryFn: () =>
      api.get<CreditLedgerEntry[]>('/api/v1/wholesale/credit-ledger', {
        customerId: ledgerCustomerId === 'all' ? undefined : ledgerCustomerId,
      }),
  });
  const ledgerEntries = useMemo(() => asList(ledgerQuery.data), [ledgerQuery.data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      return api.post('/api/v1/wholesale/invoices', form);
    },
    onSuccess: () => {
      toast({ title: 'Invoice created', variant: 'success' });
      setOpen(false);
      setForm({
        customerId: '',
        dueDate: new Date().toISOString().split('T')[0] ?? '',
        reference: '',
        lines: [],
      });
      void queryClient.invalidateQueries({ queryKey: ['wholesale-invoices'] });
    },
    onError: (err) => {
      toast({
        title: 'Save failed',
        description: err instanceof ApiError ? err.message : 'Could not create invoice',
        variant: 'destructive',
      });
    },
  });

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    saveMutation.mutate();
  }

  function getStatusVariant(status: string) {
    switch (status) {
      case 'PAID':
        return 'success';
      case 'OVERDUE':
        return 'destructive';
      case 'PARTIAL':
        return 'info';
      case 'DRAFT':
        return 'secondary';
      case 'VOID':
        return 'secondary';
      default:
        return 'default';
    }
  }

  return (
    <div>
      <PageHeader
        title="Wholesale Invoices"
        description="Manage wholesale billing and customer credit ledgers."
      />

      <Tabs defaultValue="invoices" className="mt-6">
        <TabsList>
          <TabsTrigger value="invoices">Invoices</TabsTrigger>
          <TabsTrigger value="ledger">Credit Ledger</TabsTrigger>
        </TabsList>

        <TabsContent value="invoices" className="mt-4 space-y-4">
          <div className="flex justify-end">
            <Button onClick={() => setOpen(true)}>
              <Plus className="mr-2 h-4 w-4" />
              New Invoice
            </Button>
          </div>

          {invoicesQuery.isLoading ? (
            <div className="flex justify-center py-16">
              <Spinner size="lg" />
            </div>
          ) : invoices.length === 0 ? (
            <EmptyState
              title="No wholesale invoices"
              description="Create your first wholesale invoice."
              action={
                <Button onClick={() => setOpen(true)}>
                  <Plus className="mr-2 h-4 w-4" />
                  New Invoice
                </Button>
              }
            />
          ) : (
            <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Invoice #</TableHead>
                    <TableHead>Customer</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Due Date</TableHead>
                    <TableHead className="text-right">Total</TableHead>
                    <TableHead className="text-right">Paid</TableHead>
                    <TableHead className="text-right">Balance</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="w-[80px]" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {invoices.map((inv) => (
                    <TableRow key={inv.id}>
                      <TableCell className="text-navy font-medium">{inv.invoiceNumber}</TableCell>
                      <TableCell>{inv.customerName}</TableCell>
                      <TableCell>{new Date(inv.date).toLocaleDateString()}</TableCell>
                      <TableCell>{new Date(inv.dueDate).toLocaleDateString()}</TableCell>
                      <TableCell className="text-right">{money(inv.totalAmount)}</TableCell>
                      <TableCell className="text-right">{money(inv.paidAmount)}</TableCell>
                      <TableCell className="text-right font-medium">{money(inv.balance)}</TableCell>
                      <TableCell>
                        <Badge variant={getStatusVariant(inv.status)}>{inv.status}</Badge>
                      </TableCell>
                      <TableCell>
                        <Button variant="ghost" size="sm">
                          View
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </TabsContent>

        <TabsContent value="ledger" className="mt-4 space-y-4">
          <div className="flex max-w-sm">
            <Select value={ledgerCustomerId} onValueChange={setLedgerCustomerId}>
              <SelectTrigger>
                <SelectValue placeholder="All Customers" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Customers</SelectItem>
                {customers.map((c) => (
                  <SelectItem key={c.id} value={c.id}>
                    {c.displayName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {ledgerQuery.isLoading ? (
            <div className="flex justify-center py-16">
              <Spinner size="lg" />
            </div>
          ) : ledgerEntries.length === 0 ? (
            <EmptyState
              title="No ledger entries"
              description="No credit activity found for the selected customer."
            />
          ) : (
            <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Date</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead>Reference</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead className="text-right">Running Balance</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {ledgerEntries.map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell>{new Date(entry.date).toLocaleDateString()}</TableCell>
                      <TableCell>
                        <Badge variant="secondary">{entry.type}</Badge>
                      </TableCell>
                      <TableCell>{entry.reference}</TableCell>
                      <TableCell
                        className={`text-right ${entry.amount < 0 ? 'text-green-600' : 'text-red-600'}`}
                      >
                        {money(entry.amount)}
                      </TableCell>
                      <TableCell className="text-right font-medium">
                        {money(entry.balance)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </TabsContent>
      </Tabs>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>New Wholesale Invoice</DialogTitle>
            <DialogDescription>Create a new invoice for a wholesale customer.</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4" onSubmit={onSubmit}>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Customer</Label>
                <Select
                  value={form.customerId}
                  onValueChange={(v) => setForm((f) => ({ ...f, customerId: v }))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select wholesale customer" />
                  </SelectTrigger>
                  <SelectContent>
                    {customers.map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.displayName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Due Date</Label>
                <Input
                  type="date"
                  required
                  value={form.dueDate}
                  onChange={(e) => setForm((f) => ({ ...f, dueDate: e.target.value }))}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label>Payment Reference</Label>
              <Input
                value={form.reference}
                onChange={(e) => setForm((f) => ({ ...f, reference: e.target.value }))}
                placeholder="Optional PO or reference number"
              />
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Line Items</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setForm((f) => ({
                      ...f,
                      lines: [...f.lines, { itemName: '', quantity: 1, unitPrice: 0 }],
                    }))
                  }
                >
                  Add Item
                </Button>
              </div>
              {form.lines.map((line, i) => (
                <div key={i} className="flex items-center gap-2">
                  <Input
                    placeholder="Item Name"
                    className="flex-1"
                    required
                    value={line.itemName}
                    onChange={(e) => {
                      const newLines = [...form.lines];
                      newLines[i]!.itemName = e.target.value;
                      setForm((f) => ({ ...f, lines: newLines }));
                    }}
                  />
                  <Input
                    type="number"
                    placeholder="Qty"
                    className="w-20"
                    min="1"
                    required
                    value={line.quantity || ''}
                    onChange={(e) => {
                      const newLines = [...form.lines];
                      newLines[i]!.quantity = Number(e.target.value);
                      setForm((f) => ({ ...f, lines: newLines }));
                    }}
                  />
                  <Input
                    type="number"
                    placeholder="Price"
                    className="w-28"
                    step="0.01"
                    required
                    value={line.unitPrice || ''}
                    onChange={(e) => {
                      const newLines = [...form.lines];
                      newLines[i]!.unitPrice = Number(e.target.value);
                      setForm((f) => ({ ...f, lines: newLines }));
                    }}
                  />
                  <Button
                    type="button"
                    variant="ghost"
                    size="icon"
                    onClick={() => {
                      const newLines = form.lines.filter((_, idx) => idx !== i);
                      setForm((f) => ({ ...f, lines: newLines }));
                    }}
                  >
                    &times;
                  </Button>
                </div>
              ))}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={saveMutation.isPending || !form.customerId || form.lines.length === 0}
              >
                {saveMutation.isPending ? 'Saving…' : 'Create Invoice'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
