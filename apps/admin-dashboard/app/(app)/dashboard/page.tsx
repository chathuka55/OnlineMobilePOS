'use client';

import { useQuery } from '@tanstack/react-query';
import {
  Badge,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Spinner,
} from '@possaas/ui';
import { Package, Receipt, TrendingUp, Users } from 'lucide-react';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

const chartData = [
  { day: 'Mon', sales: 42000 },
  { day: 'Tue', sales: 51000 },
  { day: 'Wed', sales: 48000 },
  { day: 'Thu', sales: 62000 },
  { day: 'Fri', sales: 74000 },
  { day: 'Sat', sales: 91000 },
  { day: 'Sun', sales: 58000 },
];

export default function DashboardPage() {
  const itemsQuery = useQuery({
    queryKey: ['items', 'dashboard'],
    queryFn: () => api.items.list({ size: 5 }),
  });
  const customersQuery = useQuery({
    queryKey: ['customers', 'dashboard'],
    queryFn: () => api.customers.list({ size: 5 }),
  });
  const billsQuery = useQuery({
    queryKey: ['bills', 'dashboard'],
    queryFn: () => api.bills.list({ size: 8 }),
  });

  const items = asList(itemsQuery.data);
  const customers = asList(customersQuery.data);
  const bills = asList(billsQuery.data);
  const loading = itemsQuery.isLoading || customersQuery.isLoading || billsQuery.isLoading;

  const metrics = [
    {
      label: 'Catalog items',
      value: items.length,
      hint: 'Loaded sample',
      icon: Package,
      tone: 'bg-primary/15 text-navy',
    },
    {
      label: 'Customers',
      value: customers.length,
      hint: 'Active CRM slice',
      icon: Users,
      tone: 'bg-[hsl(var(--info)/0.12)] text-[hsl(var(--info))]',
    },
    {
      label: 'Recent bills',
      value: bills.length,
      hint: 'Latest checkouts',
      icon: Receipt,
      tone: 'bg-[hsl(var(--success)/0.12)] text-[hsl(var(--success))]',
    },
    {
      label: 'Sales pulse',
      value: money(bills.reduce((sum, b) => sum + Number(b.grandTotal || 0), 0)),
      hint: 'From recent bills',
      icon: TrendingUp,
      tone: 'bg-navy/10 text-navy',
    },
  ];

  return (
    <div>
      <PageHeader
        title="Dashboard"
        description="Today’s retail pulse across sales, stock, and customers."
      />

      {loading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            {metrics.map((m) => {
              const Icon = m.icon;
              return (
                <Card key={m.label} className="animate-fade-up">
                  <CardContent className="flex items-start justify-between p-5">
                    <div>
                      <p className="text-muted-foreground text-sm">{m.label}</p>
                      <p className="text-navy mt-2 text-2xl font-bold">{m.value}</p>
                      <p className="text-muted-foreground mt-1 text-xs">{m.hint}</p>
                    </div>
                    <div className={`rounded-xl p-2.5 ${m.tone}`}>
                      <Icon className="h-5 w-5" />
                    </div>
                  </CardContent>
                </Card>
              );
            })}
          </div>

          <div className="mt-6 grid gap-4 lg:grid-cols-5">
            <Card className="lg:col-span-3">
              <CardHeader>
                <CardTitle>Weekly sales</CardTitle>
                <CardDescription>Placeholder trend for the reporting module.</CardDescription>
              </CardHeader>
              <CardContent className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData}>
                    <defs>
                      <linearGradient id="salesFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="#8989FD" stopOpacity={0.35} />
                        <stop offset="95%" stopColor="#8989FD" stopOpacity={0} />
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="#E5E9F0" />
                    <XAxis dataKey="day" stroke="#6B7280" fontSize={12} />
                    <YAxis stroke="#6B7280" fontSize={12} />
                    <Tooltip />
                    <Area
                      type="monotone"
                      dataKey="sales"
                      stroke="#192A56"
                      strokeWidth={2}
                      fill="url(#salesFill)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card className="lg:col-span-2">
              <CardHeader>
                <CardTitle>Recent activity</CardTitle>
                <CardDescription>Latest bills from the API.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-3">
                {bills.length === 0 ? (
                  <p className="text-muted-foreground text-sm">
                    No bills yet. Checkout from the POS terminal.
                  </p>
                ) : (
                  bills.slice(0, 6).map((bill) => (
                    <div
                      key={bill.id}
                      className="border-border/70 bg-background/60 flex items-center justify-between rounded-lg border px-3 py-2.5"
                    >
                      <div>
                        <p className="text-navy text-sm font-semibold">{bill.billNumber}</p>
                        <p className="text-muted-foreground text-xs">
                          {bill.customerName || 'Walk-in'} ·{' '}
                          {new Date(bill.billedAt).toLocaleString()}
                        </p>
                      </div>
                      <div className="text-right">
                        <p className="text-sm font-semibold">{money(bill.grandTotal)}</p>
                        <Badge variant={bill.status === 'PAID' ? 'success' : 'secondary'}>
                          {bill.status}
                        </Badge>
                      </div>
                    </div>
                  ))
                )}
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
