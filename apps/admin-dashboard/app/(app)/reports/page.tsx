'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Input,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Badge,
  Spinner,
} from '@possaas/ui';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

export default function ReportsPage() {
  const [salesFrom, setSalesFrom] = useState('');
  const [salesTo, setSalesTo] = useState('');
  const [auditFrom, setAuditFrom] = useState('');
  const [auditTo, setAuditTo] = useState('');

  const { data: salesReport, isLoading: loadingSales } = useQuery({
    queryKey: ['reports', 'sales', salesFrom, salesTo],
    queryFn: () =>
      api.get<any>('/api/v1/reports/sales', {
        from: salesFrom || undefined,
        to: salesTo || undefined,
      }),
  });

  const { data: inventoryReport, isLoading: loadingInventory } = useQuery({
    queryKey: ['reports', 'inventory'],
    queryFn: () => api.items.lowStock(),
  });

  const { data: customersReport, isLoading: loadingCustomers } = useQuery({
    queryKey: ['reports', 'customers'],
    queryFn: () => api.get<any>('/api/v1/reports/customers', { size: 50 }),
  });

  const { data: auditLog, isLoading: loadingAudit } = useQuery({
    queryKey: ['reports', 'audit', auditFrom, auditTo],
    queryFn: () =>
      api.get<any>('/api/v1/audit-events', {
        size: 100,
        from: auditFrom ? `${auditFrom}T00:00:00Z` : undefined,
        to: auditTo ? `${auditTo}T23:59:59Z` : undefined,
      }),
  });

  return (
    <div className="space-y-6">
      <PageHeader title="Reports" description="View sales, inventory, and audit logs." />

      <Tabs defaultValue="sales" className="space-y-4">
        <TabsList>
          <TabsTrigger value="sales">Sales</TabsTrigger>
          <TabsTrigger value="inventory">Inventory</TabsTrigger>
          <TabsTrigger value="customers">Customers</TabsTrigger>
          <TabsTrigger value="audit">Audit Log</TabsTrigger>
        </TabsList>

        <TabsContent value="sales" className="space-y-4">
          <div className="flex items-center gap-4">
            <Input
              type="date"
              value={salesFrom}
              onChange={(e) => setSalesFrom(e.target.value)}
              className="w-48"
            />
            <span>to</span>
            <Input
              type="date"
              value={salesTo}
              onChange={(e) => setSalesTo(e.target.value)}
              className="w-48"
            />
          </div>
          {loadingSales ? (
            <Spinner />
          ) : (
            <>
              <div className="grid gap-4 md:grid-cols-4">
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm font-medium">Total Revenue</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="text-2xl font-bold">
                      {money(salesReport?.totalRevenue || 0)}
                    </div>
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm font-medium">Total Bills</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="text-2xl font-bold">{salesReport?.totalBills || 0}</div>
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm font-medium">Average Bill Value</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="text-2xl font-bold">
                      {money(salesReport?.averageBillValue || 0)}
                    </div>
                  </CardContent>
                </Card>
                <Card>
                  <CardHeader className="pb-2">
                    <CardTitle className="text-sm font-medium">Total Refunds</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <div className="text-2xl font-bold">
                      {money(salesReport?.totalRefunds || 0)}
                    </div>
                  </CardContent>
                </Card>
              </div>
              <Card>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Date</TableHead>
                      <TableHead className="text-right">Bills Count</TableHead>
                      <TableHead className="text-right">Revenue</TableHead>
                      <TableHead className="text-right">Refunds</TableHead>
                      <TableHead className="text-right">Net</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {asList(salesReport?.dailyBreakdown).map((day: any) => (
                      <TableRow key={day.date}>
                        <TableCell>{day.date}</TableCell>
                        <TableCell className="text-right">{day.billsCount}</TableCell>
                        <TableCell className="text-right">{money(day.revenue)}</TableCell>
                        <TableCell className="text-right">{money(day.refunds)}</TableCell>
                        <TableCell className="text-right">{money(day.net)}</TableCell>
                      </TableRow>
                    ))}
                    {asList(salesReport?.dailyBreakdown).length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5} className="text-muted-foreground text-center">
                          No sales data found for the selected period.
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </Card>
            </>
          )}
        </TabsContent>

        <TabsContent value="inventory" className="space-y-4">
          {loadingInventory ? (
            <Spinner />
          ) : (
            <Card>
              <CardHeader>
                <CardTitle>Low Stock Items</CardTitle>
              </CardHeader>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Item</TableHead>
                    <TableHead>SKU</TableHead>
                    <TableHead className="text-right">Stock On Hand</TableHead>
                    <TableHead className="text-right">Reorder Level</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {asList(inventoryReport).map((item: any) => (
                    <TableRow key={item.id}>
                      <TableCell>{item.name}</TableCell>
                      <TableCell>{item.sku}</TableCell>
                      <TableCell className="text-right font-medium text-red-600">
                        {item.stockOnHand}
                      </TableCell>
                      <TableCell className="text-right">{item.reorderLevel}</TableCell>
                      <TableCell>
                        <Badge variant="destructive">Low Stock</Badge>
                      </TableCell>
                    </TableRow>
                  ))}
                  {asList(inventoryReport).length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} className="text-muted-foreground text-center">
                        No low stock items found.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="customers" className="space-y-4">
          {loadingCustomers ? (
            <Spinner />
          ) : (
            <Card>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Customer Name</TableHead>
                    <TableHead>Type</TableHead>
                    <TableHead className="text-right">Total Purchases</TableHead>
                    <TableHead className="text-right">Outstanding Balance</TableHead>
                    <TableHead>Last Purchase Date</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {asList(customersReport).map((customer: any) => (
                    <TableRow key={customer.id}>
                      <TableCell>{customer.name}</TableCell>
                      <TableCell>{customer.type}</TableCell>
                      <TableCell className="text-right">{money(customer.totalPurchases)}</TableCell>
                      <TableCell className="text-right">
                        {money(customer.outstandingBalance)}
                      </TableCell>
                      <TableCell>{customer.lastPurchaseDate}</TableCell>
                    </TableRow>
                  ))}
                  {asList(customersReport).length === 0 && (
                    <TableRow>
                      <TableCell colSpan={5} className="text-muted-foreground text-center">
                        No top customers data found.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="audit" className="space-y-4">
          <div className="flex items-center gap-4">
            <Input
              type="date"
              value={auditFrom}
              onChange={(e) => setAuditFrom(e.target.value)}
              className="w-48"
            />
            <span>to</span>
            <Input
              type="date"
              value={auditTo}
              onChange={(e) => setAuditTo(e.target.value)}
              className="w-48"
            />
          </div>
          {loadingAudit ? (
            <Spinner />
          ) : (
            <Card>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Timestamp</TableHead>
                    <TableHead>Action/Event</TableHead>
                    <TableHead>Details</TableHead>
                    <TableHead>User</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {asList(auditLog).map((log: any) => (
                    <TableRow key={log.id}>
                      <TableCell>{new Date(log.occurredAt).toLocaleString()}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{log.action}</Badge>
                      </TableCell>
                      <TableCell>{log.summary}</TableCell>
                      <TableCell>{log.actorName ?? log.actorEmail}</TableCell>
                    </TableRow>
                  ))}
                  {asList(auditLog).length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4} className="text-muted-foreground text-center">
                        No audit logs found.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </Card>
          )}
        </TabsContent>
      </Tabs>
    </div>
  );
}
