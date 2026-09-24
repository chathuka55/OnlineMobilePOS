'use client';

import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  ApiError,
  type Serial,
  type SerialLifecycle,
  type SerialStatus,
} from '@possaas/api-client';
import {
  Badge,
  Button,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
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
  toast,
} from '@possaas/ui';
import { Hash, ScanLine } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

const STATUS_LABELS: Record<SerialStatus, string> = {
  IN_STOCK: 'In Stock',
  RESERVED: 'Reserved',
  SOLD: 'Sold',
  RETURNED: 'Returned',
  DEFECTIVE: 'Defective',
  IN_REPAIR: 'In Repair',
  RMA: 'RMA',
  WRITTEN_OFF: 'Written Off',
};

function statusVariant(status: SerialStatus) {
  switch (status) {
    case 'IN_STOCK':
      return 'success' as const;
    case 'SOLD':
    case 'RESERVED':
      return 'secondary' as const;
    case 'IN_REPAIR':
    case 'RETURNED':
      return 'info' as const;
    case 'DEFECTIVE':
    case 'RMA':
    case 'WRITTEN_OFF':
      return 'destructive' as const;
    default:
      return 'default' as const;
  }
}

export default function SerialsPage() {
  const [q, setQ] = useState('');
  const [status, setStatus] = useState<string>('all');
  const [lookupCode, setLookupCode] = useState('');
  const [lookup, setLookup] = useState<SerialLifecycle | null>(null);
  const [looking, setLooking] = useState(false);

  const serialsQuery = useQuery({
    queryKey: ['serials', q, status],
    queryFn: () =>
      api.serials.list({
        size: 100,
        q: q || undefined,
        status: status === 'all' ? undefined : (status as SerialStatus),
      }),
  });
  const serials = useMemo(() => asList(serialsQuery.data), [serialsQuery.data]);

  // SerialResponse carries itemId only, so names come from a single catalog fetch
  // rather than one request per row.
  const itemsQuery = useQuery({
    queryKey: ['items', 'all-for-serials'],
    queryFn: () => api.items.list({ size: 500 }),
  });
  const itemsById = useMemo(() => {
    const map = new Map<string, { name: string; sku: string }>();
    for (const item of asList(itemsQuery.data)) {
      map.set(item.id, { name: item.name, sku: item.sku });
    }
    return map;
  }, [itemsQuery.data]);

  async function runLookup() {
    const code = lookupCode.trim();
    if (!code) return;
    setLooking(true);
    try {
      setLookup(await api.serials.lookup(code));
    } catch (err) {
      toast({
        title: 'Unit not found',
        description: err instanceof ApiError ? err.message : `Nothing in stock matches “${code}”`,
        variant: 'destructive',
      });
    } finally {
      setLooking(false);
    }
  }

  return (
    <div>
      <PageHeader
        title="IMEI / Serial Units"
        description="Every physical unit, its condition, warranty and full history."
      />

      <div className="bg-card mb-6 rounded-xl border p-4 shadow-sm">
        <label className="text-muted-foreground text-xs font-semibold uppercase tracking-wide">
          Scan a device
        </label>
        <div className="mt-2 flex gap-2">
          <Input
            placeholder="Scan or type an IMEI or serial number…"
            value={lookupCode}
            onChange={(e) => setLookupCode(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') void runLookup();
            }}
            className="font-mono"
          />
          <Button disabled={looking || !lookupCode.trim()} onClick={() => void runLookup()}>
            {looking ? <Spinner size="sm" /> : <ScanLine className="h-4 w-4" />}
            Look up
          </Button>
        </div>
      </div>

      <div className="mb-4 flex flex-col gap-4 sm:flex-row">
        <div className="w-full sm:max-w-md">
          <Input
            placeholder="Search serial or IMEI…"
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
              {(Object.keys(STATUS_LABELS) as SerialStatus[]).map((s) => (
                <SelectItem key={s} value={s}>
                  {STATUS_LABELS[s]}
                </SelectItem>
              ))}
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
          title="No units found"
          description="Units appear here once stock is received through a GRN."
          icon={<Hash className="h-6 w-6" />}
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Serial</TableHead>
                <TableHead>IMEI 1 / 2</TableHead>
                <TableHead>Item</TableHead>
                <TableHead>Condition</TableHead>
                <TableHead>Battery</TableHead>
                <TableHead>Cost</TableHead>
                <TableHead>Warranty</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {serials.map((s: Serial) => {
                const item = itemsById.get(s.itemId);
                const expired = s.warrantyEndsOn != null && new Date(s.warrantyEndsOn) < new Date();
                return (
                  <TableRow key={s.id}>
                    <TableCell className="text-navy font-mono text-xs font-medium">
                      {s.serialNumber}
                    </TableCell>
                    <TableCell className="font-mono text-xs">
                      {s.imei1 ? (
                        <div>
                          <div>{s.imei1}</div>
                          {s.imei2 && <div className="text-muted-foreground">{s.imei2}</div>}
                        </div>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <p className="text-navy font-medium">{item?.name ?? '—'}</p>
                      <p className="text-muted-foreground text-xs">{item?.sku ?? ''}</p>
                    </TableCell>
                    <TableCell>
                      <Badge variant={s.condition === 'NEW' ? 'default' : 'secondary'}>
                        {s.condition}
                        {s.grade ? ` · ${s.grade}` : ''}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      {s.batteryHealth != null ? (
                        <span className={s.batteryHealth < 80 ? 'font-medium text-red-600' : ''}>
                          {s.batteryHealth}%
                        </span>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell>{s.costPrice != null ? money(s.costPrice) : '—'}</TableCell>
                    <TableCell>
                      {s.warrantyEndsOn ? (
                        <span className={expired ? 'text-muted-foreground' : 'text-green-600'}>
                          {new Date(s.warrantyEndsOn).toLocaleDateString()}
                        </span>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusVariant(s.status)}>
                        {STATUS_LABELS[s.status] ?? s.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={!!lookup} onOpenChange={(open) => !open && setLookup(null)}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{lookup?.itemName ?? 'Unit'}</DialogTitle>
            <DialogDescription className="font-mono">
              {lookup?.unit.imei1 ?? lookup?.unit.serialNumber}
            </DialogDescription>
          </DialogHeader>

          {lookup && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-2 text-sm">
                <Detail label="Status" value={STATUS_LABELS[lookup.unit.status]} />
                <Detail
                  label="Warranty"
                  value={
                    lookup.unit.warrantyEndsOn
                      ? `${lookup.underWarranty ? 'Valid until' : 'Expired'} ${new Date(
                          lookup.unit.warrantyEndsOn,
                        ).toLocaleDateString()}`
                      : 'None'
                  }
                />
                <Detail
                  label="Condition"
                  value={`${lookup.unit.condition}${lookup.unit.grade ? ` · ${lookup.unit.grade}` : ''}`}
                />
                <Detail
                  label="Battery"
                  value={lookup.unit.batteryHealth != null ? `${lookup.unit.batteryHealth}%` : '—'}
                />
                <Detail
                  label="Cost"
                  value={lookup.unit.costPrice != null ? money(lookup.unit.costPrice) : '—'}
                />
                <Detail label="SKU" value={lookup.itemSku ?? '—'} />
              </div>

              <div>
                <p className="text-navy mb-2 text-sm font-semibold">History</p>
                {lookup.timeline.length === 0 ? (
                  <p className="text-muted-foreground text-sm">No recorded movements.</p>
                ) : (
                  <ol className="space-y-2">
                    {lookup.timeline.map((e, i) => (
                      <li key={i} className="rounded-lg border px-3 py-2 text-sm">
                        <div className="flex justify-between">
                          <span className="font-medium">{e.event}</span>
                          <span className="text-muted-foreground text-xs">
                            {new Date(e.occurredAt).toLocaleString()}
                          </span>
                        </div>
                        {e.referenceNumber && (
                          <p className="text-muted-foreground text-xs">{e.referenceNumber}</p>
                        )}
                        {e.reason && <p className="text-muted-foreground text-xs">{e.reason}</p>}
                      </li>
                    ))}
                  </ol>
                )}
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-muted-foreground text-xs">{label}</p>
      <p className="font-medium">{value}</p>
    </div>
  );
}
