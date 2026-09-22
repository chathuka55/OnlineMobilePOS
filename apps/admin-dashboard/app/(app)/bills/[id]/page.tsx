'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError, type CreateRefundRequest, type RefundSettlement } from '@possaas/api-client';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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
import { ArrowLeft, Undo2, Ban, Download, Wallet } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, money } from '@/lib/api';

const VOIDABLE = ['UNPAID', 'PARTIALLY_PAID', 'COMPLETED'];
const REFUNDABLE = ['COMPLETED', 'PARTIALLY_REFUNDED'];
const PAYABLE = ['UNPAID', 'PARTIALLY_PAID'];

export default function BillDetailPage() {
  const params = useParams<{ id: string }>();
  const billId = params.id;
  const queryClient = useQueryClient();

  const [voidOpen, setVoidOpen] = useState(false);
  const [voidReason, setVoidReason] = useState('');
  const [downloadingSize, setDownloadingSize] = useState<'A4' | 'HALF_A4' | null>(null);

  const [payOpen, setPayOpen] = useState(false);
  const [payAmount, setPayAmount] = useState(0);
  const [payMethod, setPayMethod] = useState<'CASH' | 'CARD' | 'BANK_TRANSFER' | 'CHEQUE'>('CASH');

  const [refundOpen, setRefundOpen] = useState(false);
  const [refundScope, setRefundScope] = useState<'FULL' | 'PARTIAL'>('FULL');
  const [settlement, setSettlement] = useState<RefundSettlement>('CASH');
  const [restock, setRestock] = useState(true);
  const [reason, setReason] = useState('');
  const [selectedLines, setSelectedLines] = useState<Record<string, number>>({});

  const billQuery = useQuery({
    queryKey: ['bills', billId],
    queryFn: () => api.bills.get(billId),
    enabled: Boolean(billId),
  });

  const voidMutation = useMutation({
    mutationFn: () => api.bills.void(billId, voidReason || undefined),
    onSuccess: () => {
      toast({ title: 'Bill voided', variant: 'success' });
      setVoidOpen(false);
      setVoidReason('');
      void queryClient.invalidateQueries({ queryKey: ['bills', billId] });
    },
    onError: (err) => {
      toast({
        title: 'Could not void bill',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const payMutation = useMutation({
    mutationFn: () =>
      api.post(`/api/v1/bills/${billId}/payments`, { method: payMethod, amount: payAmount }),
    onSuccess: () => {
      toast({ title: 'Payment collected', variant: 'success' });
      setPayOpen(false);
      setPayAmount(0);
      void queryClient.invalidateQueries({ queryKey: ['bills', billId] });
    },
    onError: (err) => {
      toast({
        title: 'Could not collect payment',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const refundMutation = useMutation({
    mutationFn: () => {
      const payload: CreateRefundRequest = {
        billId,
        refundType: refundScope,
        settlement,
        restock,
        reason: reason || undefined,
        lines:
          refundScope === 'PARTIAL'
            ? Object.entries(selectedLines)
                .filter(([, qty]) => qty > 0)
                .map(([billLineId, qty]) => ({ billLineId, quantity: qty }))
            : undefined,
      };
      return api.refunds.create(payload);
    },
    onSuccess: () => {
      toast({ title: 'Refund processed', variant: 'success' });
      setRefundOpen(false);
      setReason('');
      setSelectedLines({});
      void queryClient.invalidateQueries({ queryKey: ['bills', billId] });
    },
    onError: (err) => {
      toast({
        title: 'Refund failed',
        description: err instanceof ApiError ? err.message : 'Could not process refund',
        variant: 'destructive',
      });
    },
  });

  if (billQuery.isLoading) {
    return (
      <div className="flex justify-center py-20">
        <Spinner size="lg" />
      </div>
    );
  }

  const bill = billQuery.data;
  if (!bill) {
    return (
      <div>
        <PageHeader title="Bill not found" />
        <Button asChild variant="outline">
          <Link href="/bills">Back to bills</Link>
        </Button>
      </div>
    );
  }

  const canVoid = VOIDABLE.includes(bill.status);
  const canRefund = REFUNDABLE.includes(bill.status);
  const canPay = PAYABLE.includes(bill.status) && Number(bill.balanceDue) > 0;

  async function downloadInvoice(size: 'A4' | 'HALF_A4') {
    setDownloadingSize(size);
    try {
      const res = await fetch(
        `${api.baseUrl}/api/v1/reports/bills/${billId}/invoice.pdf?size=${size}`,
        { headers: { Authorization: `Bearer ${api.tokens.getAccessToken()}` } },
      );
      if (!res.ok) throw new Error('Download failed');
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `invoice-${bill!.billNumber}-${size.toLowerCase()}.pdf`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      toast({ title: 'Could not download invoice', variant: 'destructive' });
    } finally {
      setDownloadingSize(null);
    }
  }

  return (
    <div>
      <PageHeader
        title={bill.billNumber}
        description={`${bill.customerName || 'Walk-in'} · ${new Date(bill.billedAt).toLocaleString()}`}
        actions={
          <div className="flex flex-wrap gap-2">
            <Button
              variant="outline"
              disabled={downloadingSize === 'A4'}
              onClick={() => downloadInvoice('A4')}
            >
              {downloadingSize === 'A4' ? <Spinner size="sm" /> : <Download className="h-4 w-4" />}
              Invoice (A4)
            </Button>
            <Button
              variant="outline"
              disabled={downloadingSize === 'HALF_A4'}
              onClick={() => downloadInvoice('HALF_A4')}
            >
              {downloadingSize === 'HALF_A4' ? (
                <Spinner size="sm" />
              ) : (
                <Download className="h-4 w-4" />
              )}
              Invoice (Half A4)
            </Button>
            {canPay && (
              <Button
                variant="outline"
                onClick={() => {
                  setPayAmount(Number(bill.balanceDue));
                  setPayMethod('CASH');
                  setPayOpen(true);
                }}
              >
                <Wallet className="h-4 w-4" />
                Collect Payment
              </Button>
            )}
            {canRefund && (
              <Button
                variant="outline"
                onClick={() => {
                  setRefundScope('FULL');
                  setSelectedLines({});
                  setRefundOpen(true);
                }}
              >
                <Undo2 className="h-4 w-4" />
                Return / Refund
              </Button>
            )}
            {canVoid && (
              <Button variant="destructive" onClick={() => setVoidOpen(true)}>
                <Ban className="h-4 w-4" />
                Void Bill
              </Button>
            )}
            <Button asChild variant="outline">
              <Link href="/bills">
                <ArrowLeft className="h-4 w-4" />
                Back
              </Link>
            </Button>
          </div>
        }
      />

      <div className="mb-4 flex flex-wrap gap-2">
        <Badge variant="navy">{bill.status}</Badge>
        <Badge variant="secondary">{bill.channel}</Badge>
        <Badge variant="outline">{bill.priceMode}</Badge>
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Line items</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Item</TableHead>
                  <TableHead>Qty</TableHead>
                  <TableHead>Returned</TableHead>
                  <TableHead>Price</TableHead>
                  <TableHead>Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {bill.lines?.map((line) => (
                  <TableRow key={line.id}>
                    <TableCell>
                      <p className="text-navy font-medium">{line.itemName}</p>
                      <p className="text-muted-foreground text-xs">{line.itemSku}</p>
                    </TableCell>
                    <TableCell>{Number(line.quantity)}</TableCell>
                    <TableCell>
                      {Number(line.quantityReturned) > 0 ? Number(line.quantityReturned) : '—'}
                    </TableCell>
                    <TableCell>{money(line.unitPrice)}</TableCell>
                    <TableCell>{money(line.lineTotal)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Totals</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              <Row label="Subtotal" value={money(bill.subtotal)} />
              <Row label="Discounts" value={money(bill.lineDiscountTotal)} />
              <Row label="Tax" value={money(bill.taxTotal)} />
              <Row label="Grand total" value={money(bill.grandTotal)} strong />
              <Row label="Paid" value={money(bill.amountPaid)} />
              <Row label="Balance due" value={money(bill.balanceDue)} />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Payments</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {(bill.payments ?? []).length === 0 ? (
                <p className="text-muted-foreground text-sm">No payments recorded.</p>
              ) : (
                bill.payments.map((p) => (
                  <div
                    key={p.id}
                    className="bg-muted/50 flex items-center justify-between rounded-lg px-3 py-2 text-sm"
                  >
                    <span>{p.method}</span>
                    <span className="font-semibold">{money(p.amount)}</span>
                  </div>
                ))
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      {/* COLLECT PAYMENT DIALOG */}
      <Dialog open={payOpen} onOpenChange={setPayOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Collect Payment — {bill.billNumber}</DialogTitle>
            <DialogDescription>
              Balance due is {money(bill.balanceDue)}. You can collect the full amount or a
              partial top-up.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>Method</Label>
              <Select value={payMethod} onValueChange={(v) => setPayMethod(v as typeof payMethod)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CASH">Cash</SelectItem>
                  <SelectItem value="CARD">Card</SelectItem>
                  <SelectItem value="BANK_TRANSFER">Bank Transfer</SelectItem>
                  <SelectItem value="CHEQUE">Cheque</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="payAmount">Amount</Label>
              <Input
                id="payAmount"
                type="number"
                min="0.01"
                max={Number(bill.balanceDue)}
                step="0.01"
                value={payAmount}
                onChange={(e) =>
                  setPayAmount(Math.min(Number(e.target.value) || 0, Number(bill.balanceDue)))
                }
              />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setPayOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={payMutation.isPending || payAmount <= 0}
              onClick={() => payMutation.mutate()}
            >
              {payMutation.isPending ? 'Collecting…' : 'Collect Payment'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* VOID DIALOG */}
      <Dialog open={voidOpen} onOpenChange={setVoidOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Void Bill {bill.billNumber}</DialogTitle>
            <DialogDescription>
              This restores stock and reverses payments/credit applied to this bill. This cannot
              be undone.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 py-2">
            <Label htmlFor="voidReason">Reason</Label>
            <Input
              id="voidReason"
              value={voidReason}
              onChange={(e) => setVoidReason(e.target.value)}
              placeholder="e.g. Entered by mistake"
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setVoidOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={voidMutation.isPending}
              onClick={() => voidMutation.mutate()}
            >
              {voidMutation.isPending ? 'Voiding…' : 'Void Bill'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* REFUND DIALOG */}
      <Dialog open={refundOpen} onOpenChange={setRefundOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Return / Refund {bill.billNumber}</DialogTitle>
            <DialogDescription>
              Full returns the whole bill; partial lets you pick specific lines and quantities.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>Scope</Label>
              <Select value={refundScope} onValueChange={(v) => setRefundScope(v as 'FULL' | 'PARTIAL')}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="FULL">Full return</SelectItem>
                  <SelectItem value="PARTIAL">Partial return</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {refundScope === 'PARTIAL' && (
              <div className="space-y-2">
                <Label>Lines to return</Label>
                <div className="space-y-2 rounded-md border p-2">
                  {bill.lines?.map((line) => {
                    const remaining = Number(line.quantity) - Number(line.quantityReturned || 0);
                    if (remaining <= 0) return null;
                    return (
                      <div key={line.id} className="flex items-center justify-between gap-2 text-sm">
                        <span className="flex-1">
                          {line.itemName} (max {remaining})
                        </span>
                        <Input
                          type="number"
                          className="w-20"
                          min="0"
                          max={remaining}
                          value={selectedLines[line.id] || ''}
                          onChange={(e) =>
                            setSelectedLines((s) => ({
                              ...s,
                              [line.id]: Math.min(Number(e.target.value) || 0, remaining),
                            }))
                          }
                        />
                      </div>
                    );
                  })}
                </div>
              </div>
            )}

            <div className="space-y-2">
              <Label>Settlement Method</Label>
              <Select value={settlement} onValueChange={(v) => setSettlement(v as RefundSettlement)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="CASH">Cash</SelectItem>
                  <SelectItem value="CARD_REVERSAL">Card Reversal</SelectItem>
                  <SelectItem value="BANK_TRANSFER">Bank Transfer</SelectItem>
                  <SelectItem value="CHEQUE">Cheque</SelectItem>
                  <SelectItem value="CREDIT_NOTE">Store Credit</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="flex items-center gap-2">
              <input
                type="checkbox"
                id="restock"
                checked={restock}
                onChange={(e) => setRestock(e.target.checked)}
                className="rounded border-gray-300"
              />
              <Label htmlFor="restock" className="cursor-pointer">
                Return items to stock
              </Label>
            </div>

            <div className="space-y-2">
              <Label htmlFor="refundReason">Reason</Label>
              <Input id="refundReason" value={reason} onChange={(e) => setReason(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setRefundOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={
                refundMutation.isPending ||
                (refundScope === 'PARTIAL' &&
                  Object.values(selectedLines).every((q) => !q || q <= 0))
              }
              onClick={() => refundMutation.mutate()}
            >
              {refundMutation.isPending ? 'Processing…' : 'Process Refund'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="flex items-center justify-between">
      <span className="text-muted-foreground">{label}</span>
      <span className={strong ? 'text-navy text-base font-bold' : 'font-medium'}>{value}</span>
    </div>
  );
}
