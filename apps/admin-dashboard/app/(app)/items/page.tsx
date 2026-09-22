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
import { PackagePlus, Pencil, Search, AlertTriangle } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

const emptyForm: ItemRequest = {
  sku: '',
  name: '',
  categoryId: null,
  supplierId: null,
  retailPrice: 0,
  costPrice: 0,
  wholesalePrice: 0,
  unitOfMeasure: 'EA',
  reorderLevel: 0,
  trackInventory: true,
  hasSerialTracking: false,
  oldStock: false,
  warrantyMonths: 0,
  warrantyLabel: '',
  active: true,
  barcodes: [],
};

const NONE = '__none__';

export default function ItemsPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Item | null>(null);
  const [form, setForm] = useState<ItemRequest>(emptyForm);
  const [barcode, setBarcode] = useState('');
  const [damageTarget, setDamageTarget] = useState<Item | null>(null);
  const [damageQty, setDamageQty] = useState(1);
  const [damageReason, setDamageReason] = useState('');

  const itemsQuery = useQuery({
    queryKey: ['items', q],
    queryFn: () => api.items.list({ q: q || undefined, size: 100 }),
  });

  const items = useMemo(() => asList(itemsQuery.data), [itemsQuery.data]);

  const categoriesQuery = useQuery({
    queryKey: ['categories'],
    queryFn: () => api.categories.list(),
  });
  const categories = categoriesQuery.data ?? [];
  const [newCategoryOpen, setNewCategoryOpen] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');

  const createCategoryMutation = useMutation({
    mutationFn: (name: string) => api.categories.create({ name }),
    onSuccess: (created) => {
      toast({ title: 'Category created', variant: 'success' });
      setForm((f) => ({ ...f, categoryId: created.id }));
      setNewCategoryOpen(false);
      setNewCategoryName('');
      void queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
    onError: (err) => {
      toast({
        title: 'Could not create category',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const suppliersQuery = useQuery({
    queryKey: ['suppliers', 'all'],
    queryFn: () => api.suppliers.list({ size: 200 }),
  });
  const suppliers = useMemo(() => asList(suppliersQuery.data), [suppliersQuery.data]);

  const damageMutation = useMutation({
    mutationFn: () =>
      api.items.markDamaged(damageTarget!.id, damageQty, damageReason || undefined),
    onSuccess: () => {
      toast({ title: 'Marked as damaged', variant: 'success' });
      setDamageTarget(null);
      setDamageQty(1);
      setDamageReason('');
      void queryClient.invalidateQueries({ queryKey: ['items'] });
    },
    onError: (err) => {
      toast({
        title: 'Could not mark as damaged',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

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
      categoryId: item.categoryId ?? null,
      supplierId: item.supplierId ?? null,
      retailPrice: item.retailPrice,
      costPrice: item.costPrice,
      wholesalePrice: item.wholesalePrice,
      unitOfMeasure: item.unitOfMeasure || 'EA',
      reorderLevel: item.reorderLevel,
      trackInventory: item.trackInventory,
      hasSerialTracking: item.hasSerialTracking,
      oldStock: item.oldStock,
      warrantyMonths: item.warrantyMonths,
      warrantyLabel: item.warrantyLabel ?? '',
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
                <TableHead>Damaged</TableHead>
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
                    {Number(item.quantityDamaged) > 0 ? (
                      <Badge variant="destructive">{Number(item.quantityDamaged)}</Badge>
                    ) : (
                      <span className="text-muted-foreground">—</span>
                    )}
                  </TableCell>
                  <TableCell>
                    <Badge variant={item.active ? 'success' : 'secondary'}>
                      {item.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEdit(item)}
                        aria-label="Edit item"
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        className="text-destructive"
                        onClick={() => {
                          setDamageTarget(item);
                          setDamageQty(1);
                          setDamageReason('');
                        }}
                        aria-label="Mark damaged"
                      >
                        <AlertTriangle className="h-4 w-4" />
                      </Button>
                    </div>
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
            <div className="space-y-2">
              <Label>Category</Label>
              <div className="flex gap-2">
                <Select
                  value={form.categoryId ?? NONE}
                  onValueChange={(v) =>
                    setForm((f) => ({ ...f, categoryId: v === NONE ? null : v }))
                  }
                >
                  <SelectTrigger className="flex-1">
                    <SelectValue placeholder="No category" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value={NONE}>No category</SelectItem>
                    {categories.map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setNewCategoryOpen(true)}
                  aria-label="New category"
                >
                  +
                </Button>
              </div>
            </div>
            <div className="space-y-2">
              <Label>Supplier</Label>
              <Select
                value={form.supplierId ?? NONE}
                onValueChange={(v) => setForm((f) => ({ ...f, supplierId: v === NONE ? null : v }))}
              >
                <SelectTrigger>
                  <SelectValue placeholder="No supplier" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={NONE}>No supplier</SelectItem>
                  {suppliers.map((s) => (
                    <SelectItem key={s.id} value={s.id}>
                      {s.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="warrantyMonths">Warranty (months)</Label>
              <Input
                id="warrantyMonths"
                type="number"
                min="0"
                value={form.warrantyMonths ?? 0}
                onChange={(e) =>
                  setForm((f) => ({ ...f, warrantyMonths: Number(e.target.value) || 0 }))
                }
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="warrantyLabel">Warranty Label</Label>
              <Input
                id="warrantyLabel"
                placeholder="e.g. 6 Months Seller Warranty"
                value={form.warrantyLabel ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, warrantyLabel: e.target.value }))}
              />
            </div>
            <div className="flex items-center gap-2 sm:col-span-2">
              <input
                type="checkbox"
                id="hasSerialTracking"
                checked={form.hasSerialTracking ?? false}
                onChange={(e) => setForm((f) => ({ ...f, hasSerialTracking: e.target.checked }))}
                className="rounded border-gray-300"
              />
              <Label htmlFor="hasSerialTracking" className="cursor-pointer">
                Track individual serial numbers / IMEIs for this item
              </Label>
            </div>
            <div className="flex items-center gap-2 sm:col-span-2">
              <input
                type="checkbox"
                id="oldStock"
                checked={form.oldStock ?? false}
                onChange={(e) => setForm((f) => ({ ...f, oldStock: e.target.checked }))}
                className="rounded border-gray-300"
              />
              <Label htmlFor="oldStock" className="cursor-pointer">
                Mark as old / used stock
              </Label>
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

      <Dialog open={!!damageTarget} onOpenChange={(o) => !o && setDamageTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mark Damaged — {damageTarget?.name}</DialogTitle>
            <DialogDescription>
              Moves stock out of sellable quantity into a separate damaged bucket. On hand:{' '}
              {Number(damageTarget?.quantityOnHand ?? 0)}.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <div className="space-y-2">
              <Label htmlFor="damageQty">Quantity</Label>
              <Input
                id="damageQty"
                type="number"
                min="0.001"
                max={Number(damageTarget?.quantityOnHand ?? 0)}
                step="0.001"
                value={damageQty}
                onChange={(e) => setDamageQty(Number(e.target.value) || 0)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="damageReason">Reason</Label>
              <Input
                id="damageReason"
                value={damageReason}
                onChange={(e) => setDamageReason(e.target.value)}
                placeholder="e.g. Dropped during unpacking"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDamageTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={
                damageMutation.isPending ||
                damageQty <= 0 ||
                damageQty > Number(damageTarget?.quantityOnHand ?? 0)
              }
              onClick={() => damageMutation.mutate()}
            >
              {damageMutation.isPending ? 'Saving…' : 'Mark Damaged'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={newCategoryOpen} onOpenChange={setNewCategoryOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New category</DialogTitle>
          </DialogHeader>
          <div className="space-y-2 py-2">
            <Label htmlFor="newCategoryName">Category name</Label>
            <Input
              id="newCategoryName"
              autoFocus
              value={newCategoryName}
              onChange={(e) => setNewCategoryName(e.target.value)}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setNewCategoryOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={createCategoryMutation.isPending || !newCategoryName.trim()}
              onClick={() => createCategoryMutation.mutate(newCategoryName.trim())}
            >
              {createCategoryMutation.isPending ? 'Creating…' : 'Create'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
