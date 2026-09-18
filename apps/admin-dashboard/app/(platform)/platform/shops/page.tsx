'use client';

import { useRouter } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { ApiError, type PlatformTenantSummary, type TenantStatus } from '@possaas/api-client';
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
import { Ban, CheckCircle2, LogIn, Search, Store } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList } from '@/lib/api';

const STATUS_LABELS: Record<TenantStatus, string> = {
  TRIAL: 'Trial',
  ACTIVE: 'Active',
  PAST_DUE: 'Past Due',
  SUSPENDED: 'Suspended',
  CANCELLED: 'Cancelled',
};

function statusVariant(status: TenantStatus) {
  switch (status) {
    case 'ACTIVE':
      return 'success' as const;
    case 'TRIAL':
      return 'info' as const;
    case 'PAST_DUE':
      return 'secondary' as const;
    case 'SUSPENDED':
    case 'CANCELLED':
      return 'destructive' as const;
    default:
      return 'default' as const;
  }
}

export default function PlatformShopsPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [q, setQ] = useState('');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [suspendTarget, setSuspendTarget] = useState<PlatformTenantSummary | null>(null);
  const [suspendReason, setSuspendReason] = useState('');
  const [impersonatingId, setImpersonatingId] = useState<string | null>(null);

  const shopsQuery = useQuery({
    queryKey: ['platform', 'tenants', q, statusFilter],
    queryFn: () =>
      api.platform.tenants.list({
        q: q || undefined,
        status: statusFilter !== 'ALL' ? (statusFilter as TenantStatus) : undefined,
        size: 100,
      }),
  });

  const shops = useMemo(() => asList(shopsQuery.data), [shopsQuery.data]);

  const suspendMutation = useMutation({
    mutationFn: () => api.platform.tenants.suspend(suspendTarget!.id, suspendReason || undefined),
    onSuccess: () => {
      toast({ title: `${suspendTarget?.businessName} suspended`, variant: 'success' });
      setSuspendTarget(null);
      setSuspendReason('');
      void queryClient.invalidateQueries({ queryKey: ['platform', 'tenants'] });
    },
    onError: (err) => {
      toast({
        title: 'Could not suspend shop',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const unsuspendMutation = useMutation({
    mutationFn: (id: string) => api.platform.tenants.unsuspend(id),
    onSuccess: () => {
      toast({ title: 'Shop reinstated', variant: 'success' });
      void queryClient.invalidateQueries({ queryKey: ['platform', 'tenants'] });
    },
    onError: (err) => {
      toast({
        title: 'Could not reinstate shop',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  async function impersonate(shop: PlatformTenantSummary) {
    setImpersonatingId(shop.id);
    try {
      const result = await api.platform.tenants.impersonate(shop.id);
      api.tokens.setAccessToken(result.accessToken);
      api.tokens.setUserJson('');
      toast({ title: `Signed in as ${shop.businessName}`, variant: 'success' });
      router.push('/dashboard');
    } catch (err) {
      toast({
        title: 'Could not impersonate shop',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
      setImpersonatingId(null);
    }
  }

  return (
    <div>
      <PageHeader
        title="Shops"
        description={`Manage every shop on the platform. Total: ${shops.length}`}
      />

      <div className="mb-4 flex flex-col gap-4 sm:flex-row sm:items-center">
        <div className="relative max-w-sm flex-1">
          <Search className="text-muted-foreground absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2" />
          <Input
            className="pl-9"
            placeholder="Search shops…"
            value={q}
            onChange={(e) => setQ(e.target.value)}
          />
        </div>
        <div className="w-full sm:w-48">
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger>
              <SelectValue placeholder="All Statuses" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="ALL">All Statuses</SelectItem>
              {(Object.keys(STATUS_LABELS) as TenantStatus[]).map((s) => (
                <SelectItem key={s} value={s}>
                  {STATUS_LABELS[s]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {shopsQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : shops.length === 0 ? (
        <EmptyState
          icon={<Store className="h-6 w-6" />}
          title="No shops found"
          description="Shops appear here once they sign up."
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Shop</TableHead>
                <TableHead>Slug</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Plan</TableHead>
                <TableHead>Subscription</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="w-[220px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {shops.map((shop) => (
                <TableRow key={shop.id}>
                  <TableCell className="text-navy font-medium">{shop.businessName}</TableCell>
                  <TableCell className="font-mono text-xs">{shop.slug}</TableCell>
                  <TableCell>
                    <Badge variant={statusVariant(shop.status)}>
                      {STATUS_LABELS[shop.status] ?? shop.status}
                    </Badge>
                  </TableCell>
                  <TableCell>{shop.planCode ?? '—'}</TableCell>
                  <TableCell>{shop.subscriptionStatus ?? '—'}</TableCell>
                  <TableCell>{new Date(shop.createdAt).toLocaleDateString()}</TableCell>
                  <TableCell>
                    <div className="flex justify-end gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={impersonatingId === shop.id}
                        onClick={() => impersonate(shop)}
                      >
                        {impersonatingId === shop.id ? (
                          <Spinner size="sm" />
                        ) : (
                          <LogIn className="h-3.5 w-3.5" />
                        )}
                        Log in as
                      </Button>
                      {shop.status === 'SUSPENDED' ? (
                        <Button
                          size="sm"
                          variant="outline"
                          disabled={unsuspendMutation.isPending}
                          onClick={() => unsuspendMutation.mutate(shop.id)}
                        >
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          Reinstate
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-destructive"
                          onClick={() => setSuspendTarget(shop)}
                        >
                          <Ban className="h-3.5 w-3.5" />
                          Suspend
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={!!suspendTarget} onOpenChange={(open) => !open && setSuspendTarget(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Suspend {suspendTarget?.businessName}</DialogTitle>
            <DialogDescription>
              The shop becomes read-only immediately - staff can still see their data but cannot
              make new sales, repairs, or changes until reinstated.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2 py-2">
            <Label htmlFor="suspendReason">Reason (optional)</Label>
            <Input
              id="suspendReason"
              value={suspendReason}
              onChange={(e) => setSuspendReason(e.target.value)}
              placeholder="e.g. Non-payment"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSuspendTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={suspendMutation.isPending}
              onClick={() => suspendMutation.mutate()}
            >
              {suspendMutation.isPending ? 'Suspending…' : 'Suspend Shop'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
