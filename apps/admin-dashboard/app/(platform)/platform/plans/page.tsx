'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  ApiError,
  type FeatureCode,
  type Plan,
  type PlanFeature,
  type UpsertPlanRequest,
} from '@possaas/api-client';
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
  EmptyState,
  Input,
  Label,
  Spinner,
  toast,
} from '@possaas/ui';
import { Package, Pencil, Plus, Trash2 } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, money } from '@/lib/api';

const ALL_FEATURES: { code: FeatureCode; label: string }[] = [
  { code: 'RETAIL_BILLING', label: 'Retail Billing' },
  { code: 'INVENTORY', label: 'Inventory' },
  { code: 'SERIAL_TRACKING', label: 'Serial Tracking' },
  { code: 'GRN', label: 'Goods Received (GRN)' },
  { code: 'REPAIRS', label: 'Repairs' },
  { code: 'WHOLESALE', label: 'Wholesale' },
  { code: 'QUOTATIONS', label: 'Quotations' },
  { code: 'CREDIT_NOTES', label: 'Credit Notes' },
  { code: 'MULTI_OUTLET', label: 'Multi-Outlet' },
  { code: 'ADVANCED_REPORTS', label: 'Advanced Reports' },
  { code: 'JASPER_EXPORT', label: 'PDF Export' },
  { code: 'AUDIT_TRAIL', label: 'Audit Trail' },
  { code: 'API_ACCESS', label: 'API Access' },
  { code: 'THERMAL_PRINTING', label: 'Thermal Printing' },
  { code: 'BARCODE_LABELS', label: 'Barcode Labels' },
];

type PlanForm = {
  code: string;
  name: string;
  description: string;
  currency: string;
  priceMonthly: string;
  priceYearly: string;
  trialDays: string;
  maxUsers: string;
  maxOutlets: string;
  maxItems: string;
  maxMonthlyBills: string;
  publicPlan: boolean;
  active: boolean;
  displayOrder: string;
  enabledFeatures: Set<FeatureCode>;
};

function emptyForm(): PlanForm {
  return {
    code: '',
    name: '',
    description: '',
    currency: 'LKR',
    priceMonthly: '0',
    priceYearly: '0',
    trialDays: '14',
    maxUsers: '',
    maxOutlets: '',
    maxItems: '',
    maxMonthlyBills: '',
    publicPlan: true,
    active: true,
    displayOrder: '0',
    enabledFeatures: new Set(),
  };
}

function toForm(plan: Plan): PlanForm {
  return {
    code: plan.code,
    name: plan.name,
    description: plan.description ?? '',
    currency: plan.currency,
    priceMonthly: String(plan.priceMonthly),
    priceYearly: String(plan.priceYearly),
    trialDays: String(plan.trialDays),
    maxUsers: plan.maxUsers != null ? String(plan.maxUsers) : '',
    maxOutlets: plan.maxOutlets != null ? String(plan.maxOutlets) : '',
    maxItems: plan.maxItems != null ? String(plan.maxItems) : '',
    maxMonthlyBills: plan.maxMonthlyBills != null ? String(plan.maxMonthlyBills) : '',
    publicPlan: plan.publicPlan,
    active: plan.active,
    displayOrder: String(plan.displayOrder),
    enabledFeatures: new Set(
      plan.features.filter((f) => f.enabled !== false).map((f) => f.featureCode),
    ),
  };
}

function toNum(v: string): number | undefined {
  if (v.trim() === '') return undefined;
  const n = Number(v);
  return Number.isNaN(n) ? undefined : n;
}

function toPayload(form: PlanForm): UpsertPlanRequest {
  const features: PlanFeature[] = ALL_FEATURES.map((f) => ({
    featureCode: f.code,
    enabled: form.enabledFeatures.has(f.code),
  }));
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    currency: form.currency.trim() || undefined,
    priceMonthly: toNum(form.priceMonthly),
    priceYearly: toNum(form.priceYearly),
    trialDays: toNum(form.trialDays),
    maxUsers: toNum(form.maxUsers),
    maxOutlets: toNum(form.maxOutlets),
    maxItems: toNum(form.maxItems),
    maxMonthlyBills: toNum(form.maxMonthlyBills),
    publicPlan: form.publicPlan,
    active: form.active,
    displayOrder: toNum(form.displayOrder),
    features,
  };
}

export default function PlatformPlansPage() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<Plan | 'new' | null>(null);
  const [form, setForm] = useState<PlanForm>(emptyForm());
  const [deleteTarget, setDeleteTarget] = useState<Plan | null>(null);

  const plansQuery = useQuery({
    queryKey: ['platform', 'plans'],
    queryFn: () => api.platform.plans.list(),
  });

  const plans = plansQuery.data ?? [];

  function openCreate() {
    setForm(emptyForm());
    setEditing('new');
  }

  function openEdit(plan: Plan) {
    setForm(toForm(plan));
    setEditing(plan);
  }

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = toPayload(form);
      if (editing && editing !== 'new') {
        return api.platform.plans.update(editing.id, payload);
      }
      return api.platform.plans.create(payload);
    },
    onSuccess: () => {
      toast({ title: `Plan ${editing === 'new' ? 'created' : 'updated'}`, variant: 'success' });
      setEditing(null);
      void queryClient.invalidateQueries({ queryKey: ['platform', 'plans'] });
    },
    onError: (err) => {
      toast({
        title: 'Could not save plan',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => api.platform.plans.delete(id),
    onSuccess: () => {
      toast({ title: 'Plan deleted', variant: 'success' });
      setDeleteTarget(null);
      void queryClient.invalidateQueries({ queryKey: ['platform', 'plans'] });
    },
    onError: (err) => {
      toast({
        title: 'Could not delete plan',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  function toggleFeature(code: FeatureCode) {
    setForm((prev) => {
      const next = new Set(prev.enabledFeatures);
      if (next.has(code)) next.delete(code);
      else next.add(code);
      return { ...prev, enabledFeatures: next };
    });
  }

  const canSave = form.code.trim().length > 0 && form.name.trim().length > 0;

  return (
    <div>
      <PageHeader
        title="Plans"
        description="Subscription tiers offered to shops, with per-feature access control."
        actions={
          <Button onClick={openCreate}>
            <Plus className="h-4 w-4" />
            New Plan
          </Button>
        }
      />

      {plansQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : plans.length === 0 ? (
        <EmptyState
          icon={<Package className="h-6 w-6" />}
          title="No plans yet"
          description="Create your first subscription plan to start onboarding shops."
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {plans.map((plan) => (
            <Card key={plan.id} className={!plan.active ? 'opacity-60' : undefined}>
              <CardHeader className="pb-2">
                <div className="flex items-start justify-between">
                  <div>
                    <CardTitle className="text-lg">{plan.name}</CardTitle>
                    <p className="text-muted-foreground font-mono text-xs">{plan.code}</p>
                  </div>
                  <div className="flex gap-1">
                    {!plan.active && <Badge variant="secondary">Inactive</Badge>}
                    {!plan.publicPlan && <Badge variant="outline">Private</Badge>}
                  </div>
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                <div>
                  <span className="text-navy text-2xl font-bold">
                    {money(plan.priceMonthly, plan.currency)}
                  </span>
                  <span className="text-muted-foreground text-sm"> /mo</span>
                  <span className="text-muted-foreground ml-2 text-xs">
                    ({money(plan.priceYearly, plan.currency)}/yr)
                  </span>
                </div>
                {plan.description && (
                  <p className="text-muted-foreground text-sm">{plan.description}</p>
                )}
                <div className="text-muted-foreground flex flex-wrap gap-x-4 gap-y-1 text-xs">
                  <span>Users: {plan.maxUsers ?? '∞'}</span>
                  <span>Outlets: {plan.maxOutlets ?? '∞'}</span>
                  <span>Items: {plan.maxItems ?? '∞'}</span>
                  <span>Bills/mo: {plan.maxMonthlyBills ?? '∞'}</span>
                </div>
                <div className="flex flex-wrap gap-1">
                  {plan.features
                    .filter((f) => f.enabled !== false)
                    .map((f) => (
                      <Badge key={f.featureCode} variant="info" className="text-[10px]">
                        {ALL_FEATURES.find((x) => x.code === f.featureCode)?.label ??
                          f.featureCode}
                      </Badge>
                    ))}
                </div>
                <div className="flex justify-end gap-2 pt-2">
                  <Button size="sm" variant="outline" onClick={() => openEdit(plan)}>
                    <Pencil className="h-3.5 w-3.5" />
                    Edit
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="text-destructive"
                    onClick={() => setDeleteTarget(plan)}
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                    Delete
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      <Dialog open={editing !== null} onOpenChange={(open) => !open && setEditing(null)}>
        <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{editing === 'new' ? 'New Plan' : `Edit ${form.name}`}</DialogTitle>
            <DialogDescription>
              Configure pricing, limits, and which features shops on this plan can use.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-2 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="code">Plan code</Label>
              <Input
                id="code"
                value={form.code}
                disabled={editing !== 'new'}
                onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))}
                placeholder="STARTER"
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="name">Display name</Label>
              <Input
                id="name"
                value={form.name}
                onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
                placeholder="Starter"
              />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="description">Description</Label>
              <Input
                id="description"
                value={form.description}
                onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="priceMonthly">Price / month</Label>
              <Input
                id="priceMonthly"
                type="number"
                min="0"
                step="0.01"
                value={form.priceMonthly}
                onChange={(e) => setForm((f) => ({ ...f, priceMonthly: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="priceYearly">Price / year</Label>
              <Input
                id="priceYearly"
                type="number"
                min="0"
                step="0.01"
                value={form.priceYearly}
                onChange={(e) => setForm((f) => ({ ...f, priceYearly: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="currency">Currency</Label>
              <Input
                id="currency"
                maxLength={3}
                value={form.currency}
                onChange={(e) => setForm((f) => ({ ...f, currency: e.target.value.toUpperCase() }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="trialDays">Trial days</Label>
              <Input
                id="trialDays"
                type="number"
                min="0"
                value={form.trialDays}
                onChange={(e) => setForm((f) => ({ ...f, trialDays: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="maxUsers">Max users (blank = unlimited)</Label>
              <Input
                id="maxUsers"
                type="number"
                min="0"
                value={form.maxUsers}
                onChange={(e) => setForm((f) => ({ ...f, maxUsers: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="maxOutlets">Max outlets</Label>
              <Input
                id="maxOutlets"
                type="number"
                min="0"
                value={form.maxOutlets}
                onChange={(e) => setForm((f) => ({ ...f, maxOutlets: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="maxItems">Max items</Label>
              <Input
                id="maxItems"
                type="number"
                min="0"
                value={form.maxItems}
                onChange={(e) => setForm((f) => ({ ...f, maxItems: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="maxMonthlyBills">Max bills / month</Label>
              <Input
                id="maxMonthlyBills"
                type="number"
                min="0"
                value={form.maxMonthlyBills}
                onChange={(e) => setForm((f) => ({ ...f, maxMonthlyBills: e.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="displayOrder">Display order</Label>
              <Input
                id="displayOrder"
                type="number"
                value={form.displayOrder}
                onChange={(e) => setForm((f) => ({ ...f, displayOrder: e.target.value }))}
              />
            </div>
            <div className="flex items-center justify-between gap-2 rounded-lg border p-3">
              <Label htmlFor="publicPlan" className="cursor-pointer">
                Public (visible on signup)
              </Label>
              <input
                type="checkbox"
                id="publicPlan"
                checked={form.publicPlan}
                onChange={(e) => setForm((f) => ({ ...f, publicPlan: e.target.checked }))}
                className="h-4 w-4 rounded border-gray-300"
              />
            </div>
            <div className="flex items-center justify-between gap-2 rounded-lg border p-3">
              <Label htmlFor="active" className="cursor-pointer">
                Active
              </Label>
              <input
                type="checkbox"
                id="active"
                checked={form.active}
                onChange={(e) => setForm((f) => ({ ...f, active: e.target.checked }))}
                className="h-4 w-4 rounded border-gray-300"
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label>Features</Label>
            <div className="grid gap-2 sm:grid-cols-2">
              {ALL_FEATURES.map((f) => (
                <label
                  key={f.code}
                  className="flex cursor-pointer items-center justify-between gap-2 rounded-lg border p-2.5 text-sm"
                >
                  {f.label}
                  <input
                    type="checkbox"
                    checked={form.enabledFeatures.has(f.code)}
                    onChange={() => toggleFeature(f.code)}
                    className="h-4 w-4 rounded border-gray-300"
                  />
                </label>
              ))}
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>
              Cancel
            </Button>
            <Button
              disabled={!canSave || saveMutation.isPending}
              onClick={() => saveMutation.mutate()}
            >
              {saveMutation.isPending ? 'Saving…' : 'Save Plan'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!deleteTarget} onOpenChange={(open) => !open && setDeleteTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Delete {deleteTarget?.name}?</DialogTitle>
            <DialogDescription>
              Shops currently on this plan will keep their subscription, but it can no longer be
              assigned to new shops. This cannot be undone.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={deleteMutation.isPending}
              onClick={() => deleteMutation.mutate(deleteTarget!.id)}
            >
              {deleteMutation.isPending ? 'Deleting…' : 'Delete Plan'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
