'use client';

import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import {
  Badge,
  EmptyState,
  Input,
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
} from '@possaas/ui';
import { Hash } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList } from '@/lib/api';

interface SerialNumber {
  id: string;
  serialNumber: string;
  itemName: string;
  sku: string;
  status: 'AVAILABLE' | 'SOLD' | 'RESERVED' | 'RETURNED' | 'DEFECTIVE';
  linkedBillNumber?: string;
  grnNumber?: string;
  dateAdded: string;
}

export default function SerialsPage() {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<string>('all');

  const serialsQuery = useQuery({
    queryKey: ['serials', q, status],
    queryFn: () =>
      api.get<SerialNumber[]>('/api/v1/serials', {
        size: 100,
        q: q || undefined,
        status: status === 'all' ? undefined : status,
      }),
  });
  const serials = useMemo(() => asList(serialsQuery.data), [serialsQuery.data]);

  function getStatusVariant(status: string) {
    switch (status) {
      case 'AVAILABLE':
        return 'success';
      case 'SOLD':
        return 'secondary';
      case 'RESERVED':
        return 'secondary';
      case 'RETURNED':
        return 'default';
      case 'DEFECTIVE':
        return 'destructive';
      default:
        return 'default';
    }
  }

  return (
    <div>
      <PageHeader
        title="Serial Numbers Tracker"
        description="Track individual serial numbers, their status, and linked transactions."
      />

      <div className="mb-4 mt-6 flex flex-col gap-4 sm:flex-row">
        <div className="w-full sm:max-w-md">
          <Input
            placeholder="Search by serial number…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        <div className="w-full sm:max-w-xs">
          <Select value={status} onValueChange={setStatus}>
            <SelectTrigger>
              <SelectValue placeholder="Filter by status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Statuses</SelectItem>
              <SelectItem value="AVAILABLE">Available</SelectItem>
              <SelectItem value="SOLD">Sold</SelectItem>
              <SelectItem value="RESERVED">Reserved</SelectItem>
              <SelectItem value="RETURNED">Returned</SelectItem>
              <SelectItem value="DEFECTIVE">Defective</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>

      {serialsQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : serials.length === 0 ? (
        <EmptyState
          title="No serials found"
          description="Try adjusting your search or filters."
          icon={<Hash className="h-6 w-6" />}
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Serial Number</TableHead>
                <TableHead>Item Name</TableHead>
                <TableHead>SKU</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Linked Bill #</TableHead>
                <TableHead>GRN #</TableHead>
                <TableHead>Date Added</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {serials.map((s) => (
                <TableRow key={s.id}>
                  <TableCell className="text-navy font-mono font-medium">
                    {s.serialNumber}
                  </TableCell>
                  <TableCell>{s.itemName}</TableCell>
                  <TableCell className="text-muted-foreground text-xs">{s.sku}</TableCell>
                  <TableCell>
                    <Badge variant={getStatusVariant(s.status)}>{s.status}</Badge>
                  </TableCell>
                  <TableCell>
                    {s.linkedBillNumber ? (
                      <span className="cursor-pointer text-blue-600 hover:underline">
                        {s.linkedBillNumber}
                      </span>
                    ) : (
                      '—'
                    )}
                  </TableCell>
                  <TableCell>{s.grnNumber || '—'}</TableCell>
                  <TableCell>{new Date(s.dateAdded).toLocaleDateString()}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  );
}
