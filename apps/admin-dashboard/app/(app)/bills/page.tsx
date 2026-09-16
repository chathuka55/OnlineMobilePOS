'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  Badge,
  Button,
  EmptyState,
  Spinner,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@possaas/ui';
import { Receipt } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

function statusVariant(status: string) {
  if (status === 'COMPLETED') return 'success' as const;
  if (status === 'VOIDED') return 'destructive' as const;
  if (status === 'PARTIALLY_PAID') return 'info' as const;
  if (status === 'REFUNDED' || status === 'PARTIALLY_REFUNDED') return 'secondary' as const;
  return 'secondary' as const;
}

export default function BillsPage() {
  const billsQuery = useQuery({
    queryKey: ['bills'],
    queryFn: () => api.bills.list({ size: 100 }),
  });
  const bills = asList(billsQuery.data);

  return (
    <div>
      <PageHeader
        title="Billing / Bills"
        description="Checkout history and invoice status across outlets."
      />

      {billsQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : bills.length === 0 ? (
        <EmptyState
          title="No bills yet"
          description="Completed checkouts from the POS terminal will appear here."
          icon={<Receipt className="h-6 w-6" />}
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
    </div>
  );
}
