'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useMemo, useState } from 'react';
import {
  ApiError,
  type Customer,
  type CustomerRequest,
  type CustomerType,
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
import { Pencil, UserPlus } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

const emptyForm: CustomerRequest = {
  displayName: '',
  customerType: 'RETAIL',
  phonePrimary: '',
  email: '',
  creditLimit: 0,
  active: true,
};

export default function CustomersPage() {
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [form, setForm] = useState<CustomerRequest>(emptyForm);

  const customersQuery = useQuery({
    queryKey: ['customers', q],
    queryFn: () => api.customers.list({ q: q || undefined, size: 100 }),
  });
  const customers = useMemo(() => asList(customersQuery.data), [customersQuery.data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (editing) return api.customers.update(editing.id, form);
      return api.customers.create(form);
    },
    onSuccess: () => {
      toast({ title: editing ? 'Customer updated' : 'Customer created', variant: 'success' });
      setOpen(false);
      setEditing(null);
      setForm(emptyForm);
      void queryClient.invalidateQueries({ queryKey: ['customers'] });
    },
    onError: (err) => {
      toast({
        title: 'Save failed',
        description: err instanceof ApiError ? err.message : 'Could not save customer',
        variant: 'destructive',
      });
    },
  });

  function openCreate() {
    setEditing(null);
    setForm(emptyForm);
    setOpen(true);
  }

  function openEdit(customer: Customer) {
    setEditing(customer);
    setForm({
      code: customer.code ?? undefined,
      displayName: customer.displayName,
      customerType: customer.customerType,
      phonePrimary: customer.phonePrimary ?? '',
      email: customer.email ?? '',
      city: customer.city ?? '',
      creditLimit: customer.creditLimit,
      notes: customer.notes ?? '',
      active: customer.active,
    });
    setOpen(true);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    saveMutation.mutate();
  }

  return (
    <div>
      <PageHeader
        title="Customers"
        description="Retail and wholesale customer accounts."
        actions={
          <Button onClick={openCreate}>
            <UserPlus className="h-4 w-4" />
            New customer
          </Button>
        }
      />

      <div className="mb-4 max-w-md">
        <Input placeholder="Search customers…" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>

      {customersQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : customers.length === 0 ? (
        <EmptyState
          title="No customers"
          description="Add walk-in accounts or wholesale buyers to attach to bills."
          action={
            <Button onClick={openCreate}>
              <UserPlus className="h-4 w-4" />
              Add customer
            </Button>
          }
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Phone</TableHead>
                <TableHead>Outstanding</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-[80px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {customers.map((c) => (
                <TableRow key={c.id}>
                  <TableCell>
                    <div>
                      <p className="text-navy font-medium">{c.displayName}</p>
                      <p className="text-muted-foreground text-xs">{c.email || c.code || '—'}</p>
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant="secondary">{c.customerType}</Badge>
                  </TableCell>
                  <TableCell>{c.phonePrimary || '—'}</TableCell>
                  <TableCell>{money(c.outstandingAmount)}</TableCell>
                  <TableCell>
                    <Badge variant={c.active ? 'success' : 'secondary'}>
                      {c.active ? 'Active' : 'Inactive'}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => openEdit(c)}
                      aria-label="Edit customer"
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
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? 'Edit customer' : 'New customer'}</DialogTitle>
            <DialogDescription>CRM fields sync with /api/v1/customers.</DialogDescription>
          </DialogHeader>
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="displayName">Display name</Label>
              <Input
                id="displayName"
                required
                autoFocus
                value={form.displayName}
                onChange={(e) => setForm((f) => ({ ...f, displayName: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label>Customer type</Label>
              <Select
                value={form.customerType}
                onValueChange={(v) => setForm((f) => ({ ...f, customerType: v as CustomerType }))}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="RETAIL">Retail</SelectItem>
                  <SelectItem value="WHOLESALE">Wholesale</SelectItem>
                  <SelectItem value="DEALER">Dealer</SelectItem>
                  <SelectItem value="WALK_IN">Walk-in</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">Phone</Label>
              <Input
                id="phone"
                value={form.phonePrimary ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, phonePrimary: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                value={form.email ?? ''}
                onChange={(e) => setForm((f) => ({ ...f, email: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="creditLimit">Credit limit</Label>
              <Input
                id="creditLimit"
                type="number"
                step="0.01"
                value={form.creditLimit ?? 0}
                onChange={(e) => setForm((f) => ({ ...f, creditLimit: e.target.value }))}
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
    </div>
  );
}
