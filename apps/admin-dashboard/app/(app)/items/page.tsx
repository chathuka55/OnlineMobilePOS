'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useMemo, useState } from 'react';
import { ApiError, type Item, type ItemRequest } from '@possaas/api-client';
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
import { PackagePlus, Pencil, Search } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

const emptyForm: ItemRequest = {
  sku: '',
  name: '',
  retailPrice: 0,
  costPrice: 0,
  wholesalePrice: 0,
  unitOfMeasure: 'EA',
  trackInventory: true,
  active: true,
  barcodes: [],
};

export default function ItemsPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Item | null>(null);
  const [form, setForm] = useState<ItemRequest>(emptyForm);
  const [barcode, setBarcode] = useState('');

  const itemsQuery = useQuery({
    queryKey: ['items', q],
    queryFn: () => api.items.list({ q: q || undefined, size: 100 }),
  });

  const items = useMemo(() => asList(itemsQuery.data), [itemsQuery.data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const payload: ItemRequest = {
        ...form,
        barcodes: barcode
          ? [{ barcode, primaryBarcode: true }]
          : editing?.barcodes?.map((b) => ({
              barcode: b.barcode,
              primaryBarcode: b.primaryBarcode,
            })),
      };
      if (editing) return api.items.update(editing.id, payload);
      return api.items.create(payload);
    },
    onSuccess: () => {
      toast({
        title: editing ? 'Item updated' : 'Item created',
        variant: 'success',
      });
      setOpen(false);
      setEditing(null);
      setForm(emptyForm);
      setBarcode('');
      void queryClient.invalidateQueries({ queryKey: ['items'] });
    },
    onError: (err) => {
      toast({
        title: 'Save failed',
        description: err instanceof ApiError ? err.message : 'Could not save item',
        variant: 'destructive',
      });
    },
  });

  function openCreate() {
    setEditing(null);
    setForm(emptyForm);
    setBarcode('');
    setOpen(true);
  }

  function openEdit(item: Item) {
    setEditing(item);
    setForm({
      sku: item.sku,
      name: item.name,
      description: item.description ?? '',
      retailPrice: item.retailPrice,
      costPrice: item.costPrice,
      wholesalePrice: item.wholesalePrice,
      unitOfMeasure: item.unitOfMeasure || 'EA',
      reorderLevel: item.reorderLevel,
      trackInventory: item.trackInventory,
      active: item.active,
    });
    setBarcode(
      item.barcodes?.find((b) => b.primaryBarcode)?.barcode ?? item.barcodes?.[0]?.barcode ?? '',
    );
    setOpen(true);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    saveMutation.mutate();
  }

  return (
    <div>
      <PageHeader
        title="Items"
        description="Manage SKUs, pricing, and barcodes for the catalog."
        actions={
          <Button onClick={openCreate}>
            <PackagePlus className="h-4 w-4" />
            New item
          </Button>
        }
      />

      <div className="mb-4 flex max-w-md items-center gap-2">
        <div className="relative flex-1">
          <Search className="text-muted-foreground absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2" />
          <Input
            className="pl-9"
            placeholder="Search SKU, name, barcode…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
      </div>

      {itemsQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          title="No items yet"
          description="Create your first SKU to start selling on the POS terminal."
          action={
            <Button onClick={openCreate}>
              <PackagePlus className="h-4 w-4" />
              Create item
            </Button>
          }
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>SKU</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Retail</TableHead>
                <TableHead>On hand</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-[80px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-mono text-xs">{item.sku}</TableCell>
                  <TableCell className="text-navy font-medium">{item.name}</TableCell>
                  <TableCell>{money(item.retailPrice)}</TableCell>
                  <TableCell>{Number(item.quantityOnHand)}</TableCell>
                  <TableCell>
                    <Badge variant={item.active ? 'success' : 'secondary'}>
                      {item.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => openEdit(item)}
                      aria-label="Edit item"
                    >
                      <Pencil className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>{editing ? 'Edit item' : 'Create item'}</DialogTitle>
            <DialogDescription>Fields map to the catalog API ItemRequest.</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="sku">SKU</Label>
              <Input
                id="sku"
                required
                value={form.sku}
                onChange={(e) => setForm((f) => ({ ...f, sku: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="barcode">Barcode</Label>
              <Input id="barcode" value={barcode} onChange={(e) => setBarcode(e.target.value)} />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="name">Name</Label>
              <Input
                id="name"
                required
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="cost">Cost</Label>
              <Input
                id="cost"
                type="number"
                step="0.01"
                value={form.costPrice ?? 0}
                onChange={(e) => setForm((f) => ({ ...f, costPrice: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="retail">Retail</Label>
              <Input
                id="retail"
                type="number"
                step="0.01"
                required
                value={form.retailPrice ?? 0}
                onChange={(e) => setForm((f) => ({ ...f, retailPrice: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="wholesale">Wholesale</Label>
              <Input
                id="wholesale"
                type="number"
                step="0.01"
                value={form.wholesalePrice ?? 0}
                onChange={(e) => setForm((f) => ({ ...f, wholesalePrice: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="reorder">Reorder level</Label>
              <Input
                id="reorder"
                type="number"
                step="1"
                value={form.reorderLevel ?? 0}
                onChange={(e) => setForm((f) => ({ ...f, reorderLevel: e.target.value }))}
              />
            </div>
            <DialogFooter className="sm:col-span-2">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? 'Saving…' : 'Save item'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
