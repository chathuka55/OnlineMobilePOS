'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useMemo, useState } from 'react';
import { ApiError, type Supplier, type SupplierRequest, type GrnCreateRequest } from '@possaas/api-client';
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
import { Pencil, Plus, Truck, PackagePlus } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

const emptySupplier: SupplierRequest = {
  code: '',
  name: '',
  contactPerson: '',
  phonePrimary: '',
  email: '',
  addressLine1: '',
  city: '',
  notes: '',
  active: true,
};

type GrnLineForm = {
  itemId: string;
  quantity: number;
  unitCost: number;
  warrantyMonths: number;
  serialNumbersText: string;
};

const emptyGrnForm = {
  supplierId: '',
  date: new Date().toISOString().split('T')[0] ?? '',
  lines: [] as GrnLineForm[],
};

export default function SuppliersPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [form, setForm] = useState<SupplierRequest>(emptySupplier);

  const [grnOpen, setGrnOpen] = useState(false);
  const [grnForm, setGrnForm] = useState(emptyGrnForm);

  const suppliersQuery = useQuery({
    queryKey: ['suppliers', q],
    queryFn: () => api.suppliers.list({ size: 100, q: q || undefined }),
  });
  const suppliers = useMemo(() => asList(suppliersQuery.data), [suppliersQuery.data]);

  const itemsQuery = useQuery({
    queryKey: ['items'],
    queryFn: () => api.items.list({ size: 500 }),
  });
  const items = useMemo(() => asList(itemsQuery.data), [itemsQuery.data]);

  const itemsById = useMemo(() => new Map(items.map((i) => [i.id, i])), [items]);

  const grnsQuery = useQuery({
    queryKey: ['grns'],
    queryFn: () => api.grns.list({ size: 50 }),
  });
  const grns = useMemo(() => asList(grnsQuery.data), [grnsQuery.data]);
  const suppliersById = useMemo(() => new Map(suppliers.map((s) => [s.id, s])), [suppliers]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (editing) return api.suppliers.update(editing.id, form);
      return api.suppliers.create(form);
    },
    onSuccess: () => {
      toast({ title: editing ? 'Supplier updated' : 'Supplier created', variant: 'success' });
      setOpen(false);
      setEditing(null);
      setForm(emptySupplier);
      void queryClient.invalidateQueries({ queryKey: ['suppliers'] });
    },
    onError: (err) => {
      toast({
        title: 'Save failed',
        description: err instanceof ApiError ? err.message : 'Could not save supplier',
        variant: 'destructive',
      });
    },
  });

  const saveGrnMutation = useMutation({
    mutationFn: async () => {
      const payload: GrnCreateRequest = {
        supplierId: grnForm.supplierId || undefined,
        receivedAt: grnForm.date ? new Date(`${grnForm.date}T00:00:00`).toISOString() : undefined,
        lines: grnForm.lines.map((l) => ({
          itemId: l.itemId,
          quantity: l.quantity,
          unitCost: l.unitCost,
          warrantyMonths: l.warrantyMonths || undefined,
          serialNumbers: l.serialNumbersText
            ? l.serialNumbersText
                .split(/[\n,]/)
                .map((s) => s.trim())
                .filter(Boolean)
            : undefined,
        })),
      };
      return api.grns.create(payload);
    },
    onSuccess: () => {
      toast({ title: 'GRN created', variant: 'success' });
      setGrnOpen(false);
      void queryClient.invalidateQueries({ queryKey: ['grns'] });
      void queryClient.invalidateQueries({ queryKey: ['items'] });
    },
    onError: (err) => {
      toast({
        title: 'Save failed',
        description: err instanceof ApiError ? err.message : 'Could not save GRN',
        variant: 'destructive',
      });
    },
  });

  function openCreate() {
    setEditing(null);
    setForm(emptySupplier);
    setOpen(true);
  }

  function openEdit(supplier: Supplier) {
    setEditing(supplier);
    setForm({
      code: supplier.code ?? '',
      name: supplier.name,
      contactPerson: supplier.contactPerson ?? '',
      phonePrimary: supplier.phonePrimary ?? '',
      email: supplier.email ?? '',
      addressLine1: supplier.addressLine1 ?? '',
      city: supplier.city ?? '',
      notes: supplier.notes ?? '',
      active: supplier.active ?? true,
    });
    setOpen(true);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    saveMutation.mutate();
  }

  function onGrnSubmit(e: FormEvent) {
    e.preventDefault();
    saveGrnMutation.mutate();
  }

  return (
    <div>
      <PageHeader
        title="Suppliers & GRN"
        description="Manage suppliers and record Goods Received Notes."
      />

      <Tabs defaultValue="suppliers" className="mt-6">
        <TabsList>
          <TabsTrigger value="suppliers">Suppliers</TabsTrigger>
          <TabsTrigger value="grns">Goods Received</TabsTrigger>
        </TabsList>

        <TabsContent value="suppliers" className="mt-4 space-y-4">
          <div className="flex items-center justify-between gap-4">
            <Input
              placeholder="Search suppliers…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              className="max-w-md"
            />
            <Button onClick={openCreate}>
              <Plus className="mr-2 h-4 w-4" />
              New supplier
            </Button>
          </div>

          {suppliersQuery.isLoading ? (
            <div className="flex justify-center py-16">
              <Spinner size="lg" />
            </div>
          ) : suppliers.length === 0 ? (
            <EmptyState
              title="No suppliers"
              description="Add suppliers to record incoming inventory."
              action={
                <Button onClick={openCreate}>
                  <Truck className="mr-2 h-4 w-4" />
                  Add supplier
                </Button>
              }
            />
          ) : (
            <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Code</TableHead>
                    <TableHead>Company</TableHead>
                    <TableHead>Contact</TableHead>
                    <TableHead>Phone / Email</TableHead>
                    <TableHead>City</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="w-[80px]" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {suppliers.map((s) => (
                    <TableRow key={s.id}>
                      <TableCell className="font-medium">{s.code}</TableCell>
                      <TableCell>{s.name}</TableCell>
                      <TableCell>{s.contactPerson || '—'}</TableCell>
                      <TableCell>
                        <div className="text-sm">{s.phonePrimary || '—'}</div>
                        <div className="text-muted-foreground text-xs">{s.email}</div>
                      </TableCell>
                      <TableCell>{s.city || '—'}</TableCell>
                      <TableCell>
                        <Badge variant={s.active ? 'success' : 'secondary'}>
                          {s.active ? 'Active' : 'Inactive'}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Button variant="ghost" size="icon" onClick={() => openEdit(s)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </TabsContent>

        <TabsContent value="grns" className="mt-4 space-y-4">
          <div className="flex justify-end">
            <Button
              onClick={() => {
                setGrnForm(emptyGrnForm);
                setGrnOpen(true);
              }}
            >
              <PackagePlus className="mr-2 h-4 w-4" />
              New GRN
            </Button>
          </div>

          {grnsQuery.isLoading ? (
            <div className="flex justify-center py-16">
              <Spinner size="lg" />
            </div>
          ) : grns.length === 0 ? (
            <EmptyState title="No GRNs" description="Record stock received from suppliers." />
          ) : (
            <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>GRN #</TableHead>
                    <TableHead>Date</TableHead>
                    <TableHead>Supplier</TableHead>
                    <TableHead className="text-right">Total Amount</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {grns.map((g) => (
                    <TableRow key={g.id}>
                      <TableCell className="text-navy font-medium">{g.grnNumber}</TableCell>
                      <TableCell>{new Date(g.receivedAt).toLocaleDateString()}</TableCell>
                      <TableCell>
                        {g.supplierId ? (suppliersById.get(g.supplierId)?.name ?? g.supplierId) : '—'}
                      </TableCell>
                      <TableCell className="text-right font-medium">{money(g.total)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </TabsContent>
      </Tabs>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? 'Edit supplier' : 'New supplier'}</DialogTitle>
            <DialogDescription>Manage supplier information.</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="code">Code</Label>
              <Input
                id="code"
                value={form.code}
                onChange={(e) => setForm((f) => ({ ...f, code: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="name">Company Name</Label>
              <Input
                id="name"
                required
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="contactPerson">Contact Person</Label>
              <Input
                id="contactPerson"
                value={form.contactPerson}
                onChange={(e) => setForm((f) => ({ ...f, contactPerson: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">Phone</Label>
              <Input
                id="phone"
                value={form.phonePrimary}
                onChange={(e) => setForm((f) => ({ ...f, phonePrimary: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={form.email}
                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="city">City</Label>
              <Input
                id="city"
                value={form.city}
                onChange={(e) => setForm((f) => ({ ...f, city: e.target.value }))}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="address">Address</Label>
              <Input
                id="address"
                value={form.addressLine1}
                onChange={(e) => setForm((f) => ({ ...f, addressLine1: e.target.value }))}
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="notes">Notes</Label>
              <Input
                id="notes"
                value={form.notes}
                onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
              />
            </div>
            <DialogFooter className="sm:col-span-2">
              <Button type="button" variant="outline" onClick={() => setOpen(false)}>
                Cancel
              </Button>
              <Button type="submit" disabled={saveMutation.isPending}>
                {saveMutation.isPending ? 'Saving…' : 'Save'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <Dialog open={grnOpen} onOpenChange={setGrnOpen}>
        <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>New Goods Received Note</DialogTitle>
            <DialogDescription>Record items received from a supplier.</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4" onSubmit={onGrnSubmit}>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Supplier</Label>
                <Select
                  value={grnForm.supplierId}
                  onValueChange={(v) => setGrnForm((f) => ({ ...f, supplierId: v }))}
                >
                  <SelectTrigger>
                    <SelectValue placeholder="Select supplier" />
                  </SelectTrigger>
                  <SelectContent>
                    {suppliers.map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Date</Label>
                <Input
                  type="date"
                  required
                  value={grnForm.date}
                  onChange={(e) => setGrnForm((f) => ({ ...f, date: e.target.value }))}
                />
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Items</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setGrnForm((f) => ({
                      ...f,
                      lines: [
                        ...f.lines,
                        { itemId: '', quantity: 1, unitCost: 0, warrantyMonths: 0, serialNumbersText: '' },
                      ],
                    }))
                  }
                >
                  Add Item
                </Button>
              </div>
              {grnForm.lines.map((line, i) => {
                const selectedItem = itemsById.get(line.itemId);
                return (
                  <div key={i} className="space-y-2 rounded-md border p-3">
                    <div className="flex items-center gap-2">
                      <Select
                        value={line.itemId}
                        onValueChange={(v) => {
                          const newLines = [...grnForm.lines];
                          newLines[i]!.itemId = v;
                          setGrnForm((f) => ({ ...f, lines: newLines }));
                        }}
                      >
                        <SelectTrigger className="flex-1">
                          <SelectValue placeholder="Select item" />
                        </SelectTrigger>
                        <SelectContent>
                          {items.map((item) => (
                            <SelectItem key={item.id} value={item.id}>
                              {item.name}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <Input
                        type="number"
                        placeholder="Qty"
                        className="w-20"
                        min="1"
                        value={line.quantity || ''}
                        onChange={(e) => {
                          const newLines = [...grnForm.lines];
                          newLines[i]!.quantity = Number(e.target.value);
                          setGrnForm((f) => ({ ...f, lines: newLines }));
                        }}
                      />
                      <Input
                        type="number"
                        placeholder="Unit Cost"
                        className="w-28"
                        step="0.01"
                        value={line.unitCost || ''}
                        onChange={(e) => {
                          const newLines = [...grnForm.lines];
                          newLines[i]!.unitCost = Number(e.target.value);
                          setGrnForm((f) => ({ ...f, lines: newLines }));
                        }}
                      />
                      <Input
                        type="number"
                        placeholder="Warranty (mo)"
                        className="w-24"
                        min="0"
                        value={line.warrantyMonths || ''}
                        onChange={(e) => {
                          const newLines = [...grnForm.lines];
                          newLines[i]!.warrantyMonths = Number(e.target.value);
                          setGrnForm((f) => ({ ...f, lines: newLines }));
                        }}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        onClick={() => {
                          const newLines = grnForm.lines.filter((_, idx) => idx !== i);
                          setGrnForm((f) => ({ ...f, lines: newLines }));
                        }}
                      >
                        &times;
                      </Button>
                    </div>
                    {selectedItem?.hasSerialTracking && (
                      <div className="space-y-1">
                        <Label className="text-xs">
                          Serial numbers / IMEIs (one per line or comma-separated — must match
                          quantity: {line.quantity || 0})
                        </Label>
                        <textarea
                          className="border-input placeholder:text-muted-foreground focus-visible:ring-ring flex min-h-[60px] w-full rounded-md border bg-transparent px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1"
                          value={line.serialNumbersText}
                          onChange={(e) => {
                            const newLines = [...grnForm.lines];
                            newLines[i]!.serialNumbersText = e.target.value;
                            setGrnForm((f) => ({ ...f, lines: newLines }));
                          }}
                        />
                      </div>
                    )}
                  </div>
                );
              })}
            </div>

            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setGrnOpen(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={
                  saveGrnMutation.isPending || !grnForm.supplierId || grnForm.lines.length === 0
                }
              >
                {saveGrnMutation.isPending ? 'Saving…' : 'Save GRN'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}
