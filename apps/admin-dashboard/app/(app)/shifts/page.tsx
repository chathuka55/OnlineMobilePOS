'use client';

import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  ApiError,
  type CashMovementType,
  type ShiftReport,
  type ShiftStatus,
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
import { Banknote, LockKeyhole, Play } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { api, asList, money } from '@/lib/api';

const MOVEMENT_LABELS: Record<CashMovementType, string> = {
  PAY_IN: 'Pay in',
  PAYOUT: 'Payout / expense',
  DROP: 'Safe drop',
};

export default function ShiftsPage() {
  const queryClient = useQueryClient();
  const [openFloat, setOpenFloat] = useState(0);
  const [closeOpen, setCloseOpen] = useState(false);
  const [countedCash, setCountedCash] = useState(0);
  const [closeNote, setCloseNote] = useState('');
  const [moveOpen, setMoveOpen] = useState(false);
  const [moveType, setMoveType] = useState<CashMovementType>('PAYOUT');
  const [moveAmount, setMoveAmount] = useState(0);
  const [moveReason, setMoveReason] = useState('');

  // 404 just means no shift is open at this till, which is a normal state.
  const currentQuery = useQuery({
    queryKey: ['shifts', 'current'],
    queryFn: async () => {
      try {
        return await api.shifts.current();
      } catch {
        return null;
      }
    },
    retry: false,
  });
  const current = currentQuery.data ?? null;

  const historyQuery = useQuery({
    queryKey: ['shifts', 'history'],
    queryFn: () => api.shifts.list({ status: 'CLOSED' as ShiftStatus, size: 50 }),
  });
  const history = useMemo(() => asList(historyQuery.data), [historyQuery.data]);

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['shifts'] });
  }

  function fail(title: string) {
    return (err: unknown) =>
      toast({
        title,
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
  }

  const openMutation = useMutation({
    mutationFn: () => api.shifts.open({ openingFloat: openFloat }),
    onSuccess: (s) => {
      toast({ title: `Shift ${s.shiftNumber} opened`, variant: 'success' });
      setOpenFloat(0);
      refresh();
    },
    onError: fail('Could not open shift'),
  });

  const moveMutation = useMutation({
    mutationFn: () =>
      api.shifts.recordMovement(current!.shiftId, {
        movementType: moveType,
        amount: moveAmount,
        reason: moveReason || undefined,
      }),
    onSuccess: () => {
      toast({ title: 'Cash movement recorded', variant: 'success' });
      setMoveOpen(false);
      setMoveAmount(0);
      setMoveReason('');
      refresh();
    },
    onError: fail('Could not record movement'),
  });

  const closeMutation = useMutation({
    mutationFn: () =>
      api.shifts.close(current!.shiftId, {
        countedCash,
        note: closeNote || undefined,
      }),
    onSuccess: (s) => {
      const v = Number(s.variance ?? 0);
      toast({
        title: `Shift ${s.shiftNumber} closed`,
        description:
          v === 0
            ? 'Drawer balanced exactly.'
            : `${v < 0 ? 'Short' : 'Over'} by ${money(Math.abs(v))}.`,
        variant: v === 0 ? 'success' : 'destructive',
      });
      setCloseOpen(false);
      setCountedCash(0);
      setCloseNote('');
      refresh();
    },
    onError: fail('Could not close shift'),
  });

  return (
    <div>
      <PageHeader
        title="Shifts & Cash Drawer"
        description="Open a till, track cash in and out, and reconcile it at close."
      />

      {currentQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner size="lg" />
        </div>
      ) : !current ? (
        <Card className="mb-8">
          <CardHeader>
            <CardTitle>No open shift</CardTitle>
          </CardHeader>
          <CardContent className="flex flex-wrap items-end gap-3">
            <div className="space-y-2">
              <Label htmlFor="openingFloat">Opening float</Label>
              <Input
                id="openingFloat"
                type="number"
                min="0"
                step="0.01"
                className="w-48"
                value={openFloat}
                onChange={(e) => setOpenFloat(Number(e.target.value) || 0)}
              />
            </div>
            <Button disabled={openMutation.isPending} onClick={() => openMutation.mutate()}>
              <Play className="h-4 w-4" />
              {openMutation.isPending ? 'Opening…' : 'Open Shift'}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <DrawerCard
          shift={current}
          onMovement={() => setMoveOpen(true)}
          onClose={() => {
            setCountedCash(Number(current.expectedCash));
            setCloseOpen(true);
          }}
        />
      )}

      <h2 className="text-navy mb-3 mt-8 text-lg font-semibold">Past shifts (Z-reports)</h2>
      {historyQuery.isLoading ? (
        <div className="flex justify-center py-10">
          <Spinner />
        </div>
      ) : history.length === 0 ? (
        <EmptyState
          icon={<Banknote className="h-6 w-6" />}
          title="No closed shifts yet"
          description="Closing a shift freezes its Z-report and lists it here."
        />
      ) : (
        <div className="bg-card overflow-hidden rounded-xl border shadow-sm">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Shift</TableHead>
                <TableHead>Opened</TableHead>
                <TableHead>Closed</TableHead>
                <TableHead className="text-right">Expected</TableHead>
                <TableHead className="text-right">Counted</TableHead>
                <TableHead className="text-right">Variance</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {history.map((s) => {
                const v = Number(s.variance ?? 0);
                return (
                  <TableRow key={s.shiftId}>
                    <TableCell className="text-navy font-medium">{s.shiftNumber}</TableCell>
                    <TableCell className="text-sm">
                      {new Date(s.openedAt).toLocaleString()}
                    </TableCell>
                    <TableCell className="text-sm">
                      {s.closedAt ? new Date(s.closedAt).toLocaleString() : '—'}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {money(s.expectedCash)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {money(s.countedCash ?? 0)}
                    </TableCell>
                    <TableCell className="text-right">
                      <Badge variant={v === 0 ? 'success' : 'destructive'}>
                        {v === 0 ? 'Balanced' : `${v > 0 ? '+' : ''}${money(v)}`}
                      </Badge>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </div>
      )}

      <Dialog open={moveOpen} onOpenChange={setMoveOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Record Cash Movement</DialogTitle>
            <DialogDescription>
              Cash crossing the drawer for something other than a sale or refund.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <div className="space-y-2">
              <Label>Type</Label>
              <Select value={moveType} onValueChange={(v) => setMoveType(v as CashMovementType)}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(MOVEMENT_LABELS) as CashMovementType[]).map((t) => (
                    <SelectItem key={t} value={t}>
                      {MOVEMENT_LABELS[t]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="moveAmount">Amount</Label>
              <Input
                id="moveAmount"
                type="number"
                min="0.01"
                step="0.01"
                value={moveAmount}
                onChange={(e) => setMoveAmount(Number(e.target.value) || 0)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="moveReason">Reason</Label>
              <Input
                id="moveReason"
                value={moveReason}
                onChange={(e) => setMoveReason(e.target.value)}
                placeholder="e.g. Tea money, bank deposit"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setMoveOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={moveMutation.isPending || moveAmount <= 0}
              onClick={() => moveMutation.mutate()}
            >
              {moveMutation.isPending ? 'Saving…' : 'Record'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={closeOpen} onOpenChange={setCloseOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Close Shift (Z-Report)</DialogTitle>
            <DialogDescription>
              Count the drawer and enter the total. This freezes the shift and records any
              variance — it can&apos;t be reopened.
            </DialogDescription>
          </DialogHeader>
          {current && (
            <div className="space-y-3 py-2">
              <div className="flex justify-between text-sm">
                <span className="text-muted-foreground">Expected in drawer</span>
                <span className="font-semibold tabular-nums">{money(current.expectedCash)}</span>
              </div>
              <div className="space-y-2">
                <Label htmlFor="countedCash">Counted cash</Label>
                <Input
                  id="countedCash"
                  type="number"
                  min="0"
                  step="0.01"
                  value={countedCash}
                  onChange={(e) => setCountedCash(Number(e.target.value) || 0)}
                />
              </div>
              {(() => {
                const v = countedCash - Number(current.expectedCash);
                if (Math.abs(v) < 0.005) {
                  return <p className="text-sm font-medium text-green-600">Drawer balances.</p>;
                }
                return (
                  <p className="text-sm font-medium text-red-600">
                    {v < 0 ? 'Short' : 'Over'} by {money(Math.abs(v))}
                  </p>
                );
              })()}
              <div className="space-y-2">
                <Label htmlFor="closeNote">Note</Label>
                <Input
                  id="closeNote"
                  value={closeNote}
                  onChange={(e) => setCloseNote(e.target.value)}
                  placeholder="Explain any variance"
                />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setCloseOpen(false)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={closeMutation.isPending}
              onClick={() => closeMutation.mutate()}
            >
              <LockKeyhole className="h-4 w-4" />
              {closeMutation.isPending ? 'Closing…' : 'Close Shift'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function DrawerCard({
  shift,
  onMovement,
  onClose,
}: {
  shift: ShiftReport;
  onMovement: () => void;
  onClose: () => void;
}) {
  const rows: Array<[string, number | string, boolean?]> = [
    ['Opening float', shift.openingFloat],
    ['Cash sales', shift.cashSales],
    ['Cash repair receipts', shift.cashRepairs],
    ['Cash collections', shift.cashWholesale],
    ['Pay-ins', shift.payIns],
    ['Cash refunds', shift.cashRefunds, true],
    ['Payouts / expenses', shift.payouts, true],
    ['Safe drops', shift.drops, true],
  ];

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between">
        <div>
          <CardTitle>{shift.shiftNumber}</CardTitle>
          <p className="text-muted-foreground text-sm">
            Open since {new Date(shift.openedAt).toLocaleString()}
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" onClick={onMovement}>
            <Banknote className="h-4 w-4" />
            Cash Movement
          </Button>
          <Button onClick={onClose}>
            <LockKeyhole className="h-4 w-4" />
            Close Shift
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        <div className="max-w-md space-y-1.5">
          {rows.map(([label, value, negative]) => (
            <div key={label} className="flex justify-between text-sm">
              <span className="text-muted-foreground">{label}</span>
              <span className="tabular-nums">
                {negative ? '−' : ''}
                {money(value)}
              </span>
            </div>
          ))}
          <div className="text-navy flex justify-between border-t pt-2 text-base font-bold">
            <span>Expected in drawer</span>
            <span className="tabular-nums">{money(shift.expectedCash)}</span>
          </div>
        </div>

        {shift.movements.length > 0 && (
          <div className="mt-6">
            <p className="text-navy mb-2 text-sm font-semibold">Cash movements</p>
            <ul className="space-y-1">
              {shift.movements.map((m) => (
                <li key={m.id} className="flex justify-between rounded-lg border px-3 py-2 text-sm">
                  <span>
                    {MOVEMENT_LABELS[m.movementType]}
                    {m.reason ? ` · ${m.reason}` : ''}
                  </span>
                  <span className="tabular-nums">
                    {m.movementType === 'PAY_IN' ? '+' : '−'}
                    {money(m.amount)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
