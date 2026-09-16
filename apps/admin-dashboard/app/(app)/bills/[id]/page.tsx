'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import {
  Badge,
  Button,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Spinner,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@possaas/ui';
import { ArrowLeft } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, money } from '@/lib/api';

export default function BillDetailPage() {
  const params = useParams<{ id: string }>();
  const billId = params.id;

  const billQuery = useQuery({
    queryKey: ['bills', billId],
    queryFn: () => api.bills.get(billId),
    enabled: Boolean(billId),
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

  return (
    <div>
      <PageHeader
        title={bill.billNumber}
        description={`${bill.customerName || 'Walk-in'} · ${new Date(bill.billedAt).toLocaleString()}`}
        actions={
          <Button asChild variant="outline">
            <Link href="/bills">
              <ArrowLeft className="h-4 w-4" />
              Back
            </Link>
          </Button>
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
