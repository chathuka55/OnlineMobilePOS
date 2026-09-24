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
  const [vatFrom, setVatFrom] = useState('');
  const [vatTo, setVatTo] = useState('');
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

  const { data: vatReport, isLoading: loadingVat } = useQuery({
    queryKey: ['reports', 'vat', vatFrom, vatTo],
    queryFn: () =>
      api.reports.vatOutput({ from: vatFrom || undefined, to: vatTo || undefined }),
  });

  const { data: profitReport, isLoading: loadingProfit } = useQuery({
    queryKey: ['reports', 'profit'],
    queryFn: () => api.reports.profit({ limit: 200 }),
  });

  const { data: valuation, isLoading: loadingValuation } = useQuery({
    queryKey: ['reports', 'stock-valuation'],
    queryFn: () => api.reports.stockValuation(),
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
          <TabsTrigger value="vat">VAT</TabsTrigger>
          <TabsTrigger value="profit">Profit</TabsTrigger>
          <TabsTrigger value="valuation">Stock Value</TabsTrigger>
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

        <TabsContent value="vat" className="space-y-4">
          <div className="flex items-center gap-4">
            <Input
              type="date"
              value={vatFrom}
              onChange={(e) => setVatFrom(e.target.value)}
              className="w-48"
            />
            <span>to</span>
            <Input
              type="date"
              value={vatTo}
              onChange={(e) => setVatTo(e.target.value)}
              className="w-48"
            />
          </div>
          {loadingVat ? (
            <Spinner />
          ) : !vatReport ? (
            <p className="text-muted-foreground">No data.</p>
          ) : !vatReport.vatRegistered ? (
            <Card>
              <CardContent className="py-8 text-center">
                <p className="text-navy font-semibold">This shop is not VAT registered</p>
                <p className="text-muted-foreground mt-1 text-sm">
                  No VAT is charged on sales, so there is no output VAT to report. Set VAT
                  registration in Settings once the shop is registered.
                </p>
              </CardContent>
            </Card>
          ) : (
            <>
              <div className="grid gap-4 md:grid-cols-5">
                <Stat label="Gross sales" value={money(vatReport.grossSales)} />
                <Stat
                  label="Taxable value"
                  value={money(vatReport.taxableValue)}
                  hint="supplies VAT was charged on"
                />
                <Stat
                  label="Zero-rated"
                  value={money(vatReport.zeroRatedValue)}
                  hint="no VAT charged"
                />
                <Stat label="Output VAT" value={money(vatReport.vatOutput)} />
                <Stat
                  label="Net VAT payable"
                  value={money(vatReport.netVatPayable)}
                  hint={`less ${money(vatReport.refundedVat)} refunded`}
                />
              </div>
              <Card>
                <CardHeader>
                  <CardTitle>By rate</CardTitle>
                </CardHeader>
                <CardContent>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Rate</TableHead>
                        <TableHead className="text-right">Taxable value</TableHead>
                        <TableHead className="text-right">VAT</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {vatReport.byRate.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={3} className="text-muted-foreground">
                            No taxed sales in this period.
                          </TableCell>
                        </TableRow>
                      ) : (
                        vatReport.byRate.map((r, i) => (
                          <TableRow key={i}>
                            <TableCell>{Number(r.ratePercent)}%</TableCell>
                            <TableCell className="text-right tabular-nums">
                              {money(r.taxableValue)}
                            </TableCell>
                            <TableCell className="text-right tabular-nums">
                              {money(r.vatAmount)}
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                  {vatReport.vatTin && (
                    <p className="text-muted-foreground mt-3 text-xs">
                      VAT No: {vatReport.vatTin}
                    </p>
                  )}
                </CardContent>
              </Card>
            </>
          )}
        </TabsContent>

        <TabsContent value="profit" className="space-y-4">
          {loadingProfit ? (
            <Spinner />
          ) : !profitReport ? (
            <p className="text-muted-foreground">No data.</p>
          ) : (
            <>
              <div className="grid gap-4 md:grid-cols-4">
                <Stat label="Revenue (ex VAT)" value={money(profitReport.totalRevenue)} />
                <Stat label="Cost" value={money(profitReport.totalCost)} />
                <Stat label="Gross profit" value={money(profitReport.totalGrossProfit)} />
                <Stat label="Margin" value={`${Number(profitReport.marginPercent)}%`} />
              </div>
              <Card>
                <CardHeader>
                  <CardTitle>Profit per line</CardTitle>
                </CardHeader>
                <CardContent>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Bill</TableHead>
                        <TableHead>Item</TableHead>
                        <TableHead>IMEI / Serial</TableHead>
                        <TableHead className="text-right">Revenue</TableHead>
                        <TableHead className="text-right">Cost</TableHead>
                        <TableHead className="text-right">Profit</TableHead>
                        <TableHead className="text-right">Margin</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {profitReport.lines.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={7} className="text-muted-foreground">
                            No sales in this period.
                          </TableCell>
                        </TableRow>
                      ) : (
                        profitReport.lines.map((l, i) => (
                          <TableRow key={`${l.billId}-${i}`}>
                            <TableCell className="text-xs">{l.billNumber}</TableCell>
                            <TableCell>{l.itemName}</TableCell>
                            <TableCell className="font-mono text-xs">
                              {l.imei1 || l.serialNumber || '—'}
                            </TableCell>
                            <TableCell className="text-right tabular-nums">
                              {money(l.revenue)}
                            </TableCell>
                            <TableCell className="text-right tabular-nums">
                              {money(l.cost)}
                            </TableCell>
                            <TableCell className="text-right tabular-nums">
                              {money(l.grossProfit)}
                            </TableCell>
                            <TableCell className="text-right">
                              <Badge
                                variant={
                                  Number(l.marginPercent) < 0 ? 'destructive' : 'secondary'
                                }
                              >
                                {Number(l.marginPercent)}%
                              </Badge>
                            </TableCell>
                          </TableRow>
                        ))
                      )}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </>
          )}
        </TabsContent>

        <TabsContent value="valuation" className="space-y-4">
          {loadingValuation ? (
            <Spinner />
          ) : !valuation ? (
            <p className="text-muted-foreground">No data.</p>
          ) : (
            <>
              <div className="grid gap-4 md:grid-cols-4">
                <Stat label="Total stock value" value={money(valuation.totalValue)} />
                <Stat
                  label="Serialised units"
                  value={money(valuation.serialisedValue)}
                  hint="at each unit's own cost"
                />
                <Stat
                  label="Quantity items"
                  value={money(valuation.quantityValue)}
                  hint="at average cost"
                />
                <Stat
                  label="Damaged"
                  value={money(valuation.damagedValue)}
                  hint="held aside, not sellable"
                />
              </div>
              <Card>
                <CardHeader>
                  <CardTitle>By item</CardTitle>
                </CardHeader>
                <CardContent>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>SKU</TableHead>
                        <TableHead>Item</TableHead>
                        <TableHead className="text-right">On hand</TableHead>
                        <TableHead className="text-right">Unit cost</TableHead>
                        <TableHead className="text-right">Value</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {valuation.items.map((r) => (
                        <TableRow key={r.itemId}>
                          <TableCell className="font-mono text-xs">{r.sku}</TableCell>
                          <TableCell>
                            {r.itemName}
                            {r.serialised && (
                              <Badge variant="secondary" className="ml-2 text-[10px]">
                                serialised
                              </Badge>
                            )}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {Number(r.quantityOnHand)}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {money(r.unitCost)}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">
                            {money(r.value)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </>
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

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-muted-foreground text-sm font-medium">{label}</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="text-navy text-2xl font-bold tabular-nums">{value}</div>
        {hint ? <p className="text-muted-foreground mt-1 text-xs">{hint}</p> : null}
      </CardContent>
    </Card>
  );
}
