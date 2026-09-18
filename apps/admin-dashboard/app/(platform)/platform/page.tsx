'use client';

import { useQuery } from '@tanstack/react-query';
import { Card, CardContent, CardHeader, CardTitle, Spinner } from '@possaas/ui';
import { PageHeader } from '@/components/page-header';
import { api, money } from '@/lib/api';

export default function PlatformOverviewPage() {
  const metricsQuery = useQuery({
    queryKey: ['platform', 'metrics'],
    queryFn: () => api.platform.metrics(),
  });

  const m = metricsQuery.data;

  return (
    <div>
      <PageHeader
        title="Platform Overview"
        description="Shops, subscriptions, and revenue across every tenant."
      />

      {metricsQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : !m ? (
        <p className="text-muted-foreground">Could not load platform metrics.</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Stat label="Total Shops" value={m.totalTenants} />
          <Stat label="Active" value={m.activeTenants} tone="success" />
          <Stat label="On Trial" value={m.trialTenants} tone="info" />
          <Stat label="Suspended" value={m.suspendedTenants} tone="destructive" />
          <Stat label="Past Due" value={m.pastDueSubscriptions} tone="destructive" />
          <Stat label="In Grace Period" value={m.graceSubscriptions} tone="info" />
          <Stat label="MRR" value={money(m.mrr, m.currency)} />
          <Stat label="ARR" value={money(m.arr, m.currency)} />
        </div>
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  tone,
}: {
  label: string;
  value: string | number;
  tone?: 'success' | 'destructive' | 'info';
}) {
  const toneClass =
    tone === 'success'
      ? 'text-green-600'
      : tone === 'destructive'
        ? 'text-red-600'
        : tone === 'info'
          ? 'text-blue-600'
          : 'text-navy';
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-muted-foreground text-sm font-medium">{label}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className={`text-2xl font-bold ${toneClass}`}>{value}</div>
      </CardContent>
    </Card>
  );
}
