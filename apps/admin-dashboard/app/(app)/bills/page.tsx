'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ApiError,
  type Item,
  type PaymentMethod,
  type CheckoutRequest,
} from '@possaas/api-client';
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
import { Plus, Receipt, Trash2 } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

function statusVariant(status: string) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'VOIDED') return 'destructive' as const;
  if (status === 'PARTIALLY_PAID') return 'info' as const;
  if (status === 'REFUNDED' || status === 'PARTIALLY_REFUNDED') return 'secondary' as const;
  return 'secondary' as const;
}

type NewLine = {
  key: string;
  itemId: string;
  name: string;
  quantity: number;
  unitPrice: number;
};

const METHODS: PaymentMethod[] = ['CASH', 'CARD', 'BANK_TRANSFER', 'CHEQUE'];

export default function BillsPage() {
  const queryClient = useQueryClient();
  const billsQuery = useQuery({
    queryKey: ['bills'],
    queryFn: () => api.bills.list({ size: 100 }),
  });
  const bills = asList(billsQuery.data);

  const [open, setOpen] = useState(false);
  const [customerName, setCustomerName] = useState('Walk-in Customer');
  const [customerPhone, setCustomerPhone] = useState('');
  const [lines, setLines] = useState<NewLine[]>([]);
  const [itemSearch, setItemSearch] = useState('');
  const [itemResults, setItemResults] = useState<Item[]>([]);
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [payNow, setPayNow] = useState<number | null>(null);

  const subtotal = lines.reduce((sum, l) => sum + l.quantity * l.unitPrice, 0);

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

  function resetForm() {
    setCustomerName('Walk-in Customer');
    setCustomerPhone('');
    setLines([]);
    setItemSearch('');
    setItemResults([]);
    setMethod('CASH');
    setPayNow(null);
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
          unitPrice: Number(item.retailPrice),
        },
      ];
    });
    setItemSearch('');
    setItemResults([]);
  }

  const createMutation = useMutation({
    mutationFn: () => {
      const amountNow = Math.min(payNow ?? subtotal, subtotal);
      const payload: CheckoutRequest = {
        customerName,
        customerPhone: customerPhone || undefined,
        channel: 'RETAIL',
        priceMode: 'RETAIL',
        lines: lines.map((l) => ({ itemId: l.itemId, quantity: l.quantity, unitPrice: l.unitPrice })),
        payments:
          amountNow > 0
            ? [{ method, amount: amountNow, tenderedAmount: method === 'CASH' ? amountNow : undefined }]
            : undefined,
      };
      return api.bills.checkout(payload);
    },
    onSuccess: (bill) => {
      toast({ title: `Bill ${bill.billNumber} created`, variant: 'success' });
      setOpen(false);
      resetForm();
      void queryClient.invalidateQueries({ queryKey: ['bills'] });
    },
    onError: (err) => {
      toast({
        title: 'Could not create bill',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  return (
    <div>
      <PageHeader
        title="Billing / Bills"
        description="Checkout history and invoice status across outlets."
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus className="h-4 w-4" />
            New Bill
          </Button>
        }
      />

      {billsQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : bills.length === 0 ? (
        <EmptyState
          title="No bills yet"
          description="Create one here, or complete a checkout from the POS terminal."
          icon={<Receipt className="h-6 w-6" />}
          action={
            <Button onClick={() => setOpen(true)}>
              <Plus className="h-4 w-4" />
              New Bill
            </Button>
          }
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Bill #</TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Billed at</TableHead>
                <TableHead>Total</TableHead>
                <TableHead>Paid</TableHead>
                <TableHead>Status</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {bills.map((bill) => (
                <TableRow key={bill.id}>
                  <TableCell className="text-navy font-semibold">{bill.billNumber}</TableCell>
                  <TableCell>{bill.customerName || 'Walk-in'}</TableCell>
                  <TableCell>{new Date(bill.billedAt).toLocaleString()}</TableCell>
                  <TableCell>{money(bill.grandTotal)}</TableCell>
                  <TableCell>{money(bill.amountPaid)}</TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(bill.status)}>{bill.status}</Badge>
                  </TableCell>
                  <TableCell>
                    <Button asChild variant="outline" size="sm">
                      <Link href={`/bills/${bill.id}`}>View</Link>
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={open} onOpenChange={(o) => (o ? setOpen(true) : (setOpen(false), resetForm()))}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>New Bill</DialogTitle>
            <DialogDescription>
              Create a retail bill manually - search stock, set quantities, and take a full or
              partial payment.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-2">
                <Label htmlFor="customerName">Customer name</Label>
                <Input
                  id="customerName"
                  value={customerName}
                  onChange={(e) => setCustomerName(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="customerPhone">Phone (optional)</Label>
                <Input
                  id="customerPhone"
                  value={customerPhone}
                  onChange={(e) => setCustomerPhone(e.target.value)}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label>Add item</Label>
              <Input
                placeholder="Search stock by name or SKU…"
                value={itemSearch}
                onChange={(e) => setItemSearch(e.target.value)}
              />
              {itemResults.length > 0 && (
                <ul className="space-y-1 rounded-md border p-1">
                  {itemResults.map((item) => (
                    <li key={item.id}>
                      <button
                        type="button"
                        className="flex w-full items-center justify-between rounded-md px-2 py-1.5 text-left text-sm hover:bg-slate-50"
                        onClick={() => addLine(item)}
                      >
                        <span>{item.name}</span>
                        <span className="font-semibold">{money(item.retailPrice)}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>

            {lines.length > 0 && (
              <div className="space-y-2 rounded-md border p-3">
                {lines.map((line) => (
                  <div key={line.key} className="flex items-center gap-2 text-sm">
                    <span className="flex-1">{line.name}</span>
                    <Input
                      type="number"
                      min="0.001"
                      step="0.001"
                      className="w-16"
                      value={line.quantity}
                      onChange={(e) =>
                        setLines((prev) =>
                          prev.map((l) =>
                            l.key === line.key
                              ? { ...l, quantity: Number(e.target.value) || 0 }
                              : l,
                          ),
                        )
                      }
                    />
                    <Input
                      type="number"
                      min="0"
                      step="0.01"
                      className="w-24"
                      value={line.unitPrice}
                      onChange={(e) =>
                        setLines((prev) =>
                          prev.map((l) =>
                            l.key === line.key
                              ? { ...l, unitPrice: Number(e.target.value) || 0 }
                              : l,
                          ),
                        )
                      }
                    />
                    <span className="w-20 text-right font-semibold">
                      {money(line.quantity * line.unitPrice)}
                    </span>
                    <Button
                      type="button"
                      size="icon"
                      variant="ghost"
                      onClick={() => setLines((prev) => prev.filter((l) => l.key !== line.key))}
                      aria-label="Remove line"
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
                <div className="flex justify-between border-t pt-2 font-bold">
                  <span>Subtotal</span>
                  <span>{money(subtotal)}</span>
                </div>
              </div>
            )}

            <div className="grid grid-cols-2 gap-2">
              <div className="space-y-2">
                <Label>Payment method</Label>
                <Select value={method} onValueChange={(v) => setMethod(v as PaymentMethod)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {METHODS.map((m) => (
                      <SelectItem key={m} value={m}>
                        {m.replace('_', ' ')}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="payNow">Amount to collect now</Label>
                <Input
                  id="payNow"
                  type="number"
                  min="0"
                  max={subtotal}
                  step="0.01"
                  value={payNow ?? subtotal}
                  onChange={(e) => setPayNow(Math.min(Number(e.target.value) || 0, subtotal))}
                />
              </div>
            </div>
            {payNow != null && payNow < subtotal && subtotal > 0 && (
              <p className="text-xs font-medium text-amber-600">
                Partial payment - remaining {money(subtotal - payNow)} stays as balance due.
              </p>
            )}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setOpen(false);
                resetForm();
              }}
            >
              Cancel
            </Button>
            <Button
              disabled={createMutation.isPending || lines.length === 0}
              onClick={() => createMutation.mutate()}
            >
              {createMutation.isPending ? 'Creating…' : `Create Bill (${money(subtotal)})`}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
