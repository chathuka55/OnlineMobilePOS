'use client';

import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle } from 'lucide-react';
import {
  Badge,
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  EmptyState,
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
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

const ALL_SUPPLIERS = '__all__';
const NO_SUPPLIER = '__none__';

export default function DamagedStockPage() {
  const [supplierFilter, setSupplierFilter] = useState(ALL_SUPPLIERS);

  const damagedQuery = useQuery({
    queryKey: ['items', 'damaged'],
    queryFn: () => api.items.damaged(),
  });
  const suppliersQuery = useQuery({
    queryKey: ['suppliers', 'all'],
    queryFn: () => api.suppliers.list({ size: 200 }),
  });
  const suppliers = useMemo(() => asList(suppliersQuery.data), [suppliersQuery.data]);

  const rows = useMemo(() => damagedQuery.data ?? [], [damagedQuery.data]);
  const filtered = useMemo(() => {
    if (supplierFilter === ALL_SUPPLIERS) return rows;
    if (supplierFilter === NO_SUPPLIER) return rows.filter((r) => !r.supplierId);
    return rows.filter((r) => r.supplierId === supplierFilter);
  }, [rows, supplierFilter]);

  const bySupplier = useMemo(() => {
    const groups = new Map<string, { name: string; rows: typeof rows; loss: number }>();
    for (const row of filtered) {
      const key = row.supplierId ?? NO_SUPPLIER;
      const name = row.supplierName ?? 'No supplier on record';
      if (!groups.has(key)) groups.set(key, { name, rows: [], loss: 0 });
      const group = groups.get(key)!;
      group.rows.push(row);
      group.loss += Number(row.quantityDamaged) * Number(row.costPrice);
    }
    return Array.from(groups.entries());
  }, [filtered]);

  const totalLoss = filtered.reduce(
    (sum, r) => sum + Number(r.quantityDamaged) * Number(r.costPrice),
    0,
  );

  return (
    <div>
      <PageHeader
        title="Damaged Stock"
        description="Stock held aside as damaged, grouped by supplier. Estimated loss at cost price."
      />

      <div className="mb-4 max-w-xs">
        <Select value={supplierFilter} onValueChange={setSupplierFilter}>
          <SelectTrigger>
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value={ALL_SUPPLIERS}>All suppliers</SelectItem>
            <SelectItem value={NO_SUPPLIER}>No supplier on record</SelectItem>
            {suppliers.map((s) => (
              <SelectItem key={s.id} value={s.id}>
                {s.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {damagedQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : filtered.length === 0 ? (
        <EmptyState
          icon={<AlertTriangle className="h-6 w-6" />}
          title="No damaged stock"
          description="Items marked damaged from the Items page will show up here, grouped by supplier."
        />
      ) : (
        <div className="space-y-4">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-muted-foreground text-sm font-medium">
                Total estimated loss (at cost)
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-navy text-2xl font-bold">{money(totalLoss)}</div>
            </CardContent>
          </Card>

          {bySupplier.map(([key, group]) => (
            <Card key={key}>
              <CardHeader className="flex flex-row items-center justify-between pb-2">
                <CardTitle className="text-base">{group.name}</CardTitle>
                <Badge variant="destructive">{money(group.loss)} loss</Badge>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>SKU</TableHead>
                      <TableHead>Item</TableHead>
                      <TableHead>Qty damaged</TableHead>
                      <TableHead>Cost price</TableHead>
                      <TableHead>Est. loss</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {group.rows.map((r) => (
                      <TableRow key={r.itemId}>
                        <TableCell className="font-mono text-xs">{r.sku}</TableCell>
                        <TableCell className="text-navy font-medium">{r.itemName}</TableCell>
                        <TableCell>{Number(r.quantityDamaged)}</TableCell>
                        <TableCell>{money(r.costPrice)}</TableCell>
                        <TableCell>
                          {money(Number(r.quantityDamaged) * Number(r.costPrice))}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
