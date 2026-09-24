import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ApiError,
  type Cart,
  type Item,
  type PaymentMethod,
  type Serial,
  type User,
} from '@possaas/api-client';
import {
  Badge,
  Button,
  Input,
  Spinner,
  toast,
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@possaas/ui';
import {
  CreditCard,
  Banknote,
  PauseCircle,
  Trash2,
  UserRound,
  Store,
  ScanBarcode,
  Settings,
  FileCheck2,
  Wrench,
  ShoppingBag,
  ShoppingCart,
} from 'lucide-react';
import type { IconComponent } from '@possaas/ui';

const CreditCardIcon = CreditCard as IconComponent;
const BanknoteIcon = Banknote as IconComponent;
const PauseCircleIcon = PauseCircle as IconComponent;
const Trash2Icon = Trash2 as IconComponent;
const UserRoundIcon = UserRound as IconComponent;
const StoreIcon = Store as IconComponent;
const ScanBarcodeIcon = ScanBarcode as IconComponent;
const SettingsIcon = Settings as IconComponent;
const FileCheck2Icon = FileCheck2 as IconComponent;
const WrenchIcon = Wrench as IconComponent;
const ShoppingBagIcon = ShoppingBag as IconComponent;
const ShoppingCartIcon = ShoppingCart as IconComponent;
import type { Outlet, Tenant } from '@possaas/api-client';
import { api, money } from './lib/api';
import {
  printBestEffort,
  getStoredReceiptFormat,
  setStoredReceiptFormat,
  canPrintRaw,
  type ReceiptFormat,
} from './lib/print';
import { toReceiptModel } from './lib/receiptModel';
import { Keypad } from './components/Keypad';
import { LoginGate } from './components/LoginGate';
import { Receipt, type LogoLayout } from './components/Receipt';
import { CustomerSearch } from './components/CustomerSearch';
import { RepairsPanel } from './components/RepairsPanel';
import { WholesalePanel } from './components/WholesalePanel';
import { SerialPicker } from './components/SerialPicker';

type Mode = 'RETAIL' | 'REPAIRS' | 'WHOLESALE';

type LocalLine = {
  key: string;
  itemId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  discountType?: 'NONE' | 'PERCENT' | 'AMOUNT';
  discountInput?: number;
  hasSerialTracking?: boolean;
  serialIds?: string[];
  serialNumbers?: string[];
};

function getEffectivePrice(line: LocalLine) {
  if (line.discountType === 'PERCENT' && line.discountInput) {
    return line.unitPrice * (1 - line.discountInput / 100);
  }
  if (line.discountType === 'AMOUNT' && line.discountInput) {
    return Math.max(0, line.unitPrice - line.discountInput);
  }
  return line.unitPrice;
}

const PAYMENTS: Array<{ method: PaymentMethod | 'CHEQUE'; label: string; icon: IconComponent }> = [
  { method: 'CASH', label: 'Cash', icon: BanknoteIcon },
  { method: 'CARD', label: 'Card', icon: CreditCardIcon },
  { method: 'BANK_TRANSFER', label: 'Transfer', icon: CreditCardIcon },
  { method: 'CHEQUE', label: 'Cheque', icon: FileCheck2Icon },
];

export default function App() {
  const [user, setUser] = useState<User | null>(() => api.auth.getStoredUser());
  const [mode, setMode] = useState<Mode>('RETAIL');
  const permissions = user?.permissions ?? [];
  const can = (permission: string) => permissions.includes(permission);
  const [search, setSearch] = useState('');
  const [lines, setLines] = useState<LocalLine[]>([]);
  const [customerName, setCustomerName] = useState('Walk-in Customer');
  const [customerId, setCustomerId] = useState<string | null>(null);
  const [payAmount, setPayAmount] = useState<number | null>(null);
  const [serialPickerItem, setSerialPickerItem] = useState<Item | null>(null);
  const [heldCarts, setHeldCarts] = useState<Cart[]>([]);
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod | 'CHEQUE'>('CASH');
  const [busy, setBusy] = useState(false);
  const [cartId, setCartId] = useState<string | null>(null);
  const searchRef = useRef<HTMLInputElement>(null);

  const [chequeNumber, setChequeNumber] = useState('');
  const [bankName, setBankName] = useState('');
  const [chequeDate, setChequeDate] = useState('');

  const [holdCartOpen, setHoldCartOpen] = useState(false);
  const [holdCartLabel, setHoldCartLabel] = useState('');

  const [pinLocked, setPinLocked] = useState(() => !!api.auth.getStoredUser()?.hasPin);
  const [pinInput, setPinInput] = useState('');
  const [pinVerifying, setPinVerifying] = useState(false);
  const [pinSettingOpen, setPinSettingOpen] = useState(false);
  const [newPin, setNewPin] = useState('');
  const [pinPassword, setPinPassword] = useState('');
  const [pinSaving, setPinSaving] = useState(false);

  const [lastBill, setLastBill] = useState<any>(null);
  const [shopTenant, setShopTenant] = useState<Tenant | null>(null);
  const [shopOutlet, setShopOutlet] = useState<Outlet | null>(null);
  const [logoLayout, setLogoLayout] = useState<LogoLayout>('SIDE');
  const [receiptFormat, setReceiptFormat] = useState<ReceiptFormat>(() => getStoredReceiptFormat());

  useEffect(() => {
    if (!user) return;
    api.shop
      .tenant()
      .then(setShopTenant)
      .catch(() => {});
    api.shop
      .outlets()
      .then((outlets) => setShopOutlet(outlets.find((o) => o.defaultOutlet) ?? outlets[0] ?? null))
      .catch(() => {});
    api
      .get<Record<string, string>>('/api/v1/settings')
      .then((settings) => {
        if (settings['print.receipt.logoLayout'] === 'CENTERED') setLogoLayout('CENTERED');
      })
      .catch(() => {});
  }, [user]);

  const subtotal = useMemo(
    () => lines.reduce((sum, line) => sum + line.quantity * getEffectivePrice(line), 0),
    [lines],
  );

  const refreshHeld = useCallback(async () => {
    try {
      const carts = await api.carts.list({ status: 'HELD' });
      setHeldCarts(Array.isArray(carts) ? carts : []);
    } catch {
      /* ignore when offline / unauthenticated */
    }
  }, []);

  useEffect(() => {
    if (user) void refreshHeld();
  }, [user, refreshHeld]);

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'F2') {
        e.preventDefault();
        promptHoldCart();
      }
      if (e.key === 'F4') {
        e.preventDefault();
        void pay();
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        setSearch('');
        searchRef.current?.focus();
      }
    }
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lines, cartId, paymentMethod, customerName, customerId, payAmount, subtotal]);

  async function ensureCart() {
    if (cartId) return cartId;
    const cart = await api.carts.create({ customerName, priceMode: 'RETAIL' });
    setCartId(cart.id);
    return cart.id;
  }

  function addItem(item: Item) {
    setLines((prev) => {
      const existing = prev.find((l) => l.itemId === item.id);
      if (existing) {
        return prev.map((l) => (l.itemId === item.id ? { ...l, quantity: l.quantity + 1 } : l));
      }
      return [
        ...prev,
        {
          key: `${item.id}-${Date.now()}`,
          itemId: item.id,
          sku: item.sku,
          name: item.name,
          quantity: 1,
          unitPrice: Number(item.retailPrice),
        },
      ];
    });
  }

  function handleSerialPicked(serial: Serial) {
    const item = serialPickerItem;
    if (!item) return;
    setLines((prev) => [
      ...prev,
      {
        key: `${item.id}-${serial.id}`,
        itemId: item.id,
        sku: item.sku,
        name: item.name,
        quantity: 1,
        unitPrice: Number(item.retailPrice),
        hasSerialTracking: true,
        serialIds: [serial.id],
        serialNumbers: [serial.serialNumber],
      },
    ]);
    setSerialPickerItem(null);
    void ensureCart()
      .then((id) =>
        api.carts.addLine(id, {
          itemId: item.id,
          quantity: 1,
          unitPrice: item.retailPrice,
          serialIds: [serial.id],
        }),
      )
      .catch(() => {
        /* keep local cart if API line fails */
      });
  }

  async function lookup(query: string) {
    const q = query.trim();
    if (!q) return;
    setBusy(true);
    try {
      let item: Item | null = null;
      try {
        item = await api.items.byBarcode(q);
      } catch {
        const listed = await api.items.list({ q, size: 1 });
        const rows = Array.isArray(listed) ? listed : listed.content;
        item = rows[0] ?? null;
      }
      if (!item) {
        toast({ title: 'Not found', description: `No item for “${q}”`, variant: 'destructive' });
        return;
      }
      if (item.hasSerialTracking) {
        setSerialPickerItem(item);
        setSearch('');
        return;
      }
      addItem(item);
      try {
        const id = await ensureCart();
        await api.carts.addLine(id, {
          itemId: item.id,
          quantity: 1,
          unitPrice: item.retailPrice,
        });
      } catch {
        /* keep local cart if API line fails */
      }
      setSearch('');
    } catch (err) {
      toast({
        title: 'Lookup failed',
        description: err instanceof ApiError ? err.message : 'Could not search items',
        variant: 'destructive',
      });
    } finally {
      setBusy(false);
      searchRef.current?.focus();
    }
  }

  async function onSearchSubmit(e: FormEvent) {
    e.preventDefault();
    await lookup(search);
  }

  function promptHoldCart() {
    if (lines.length === 0) {
      toast({ title: 'Cart empty', description: 'Add items before holding (F2).' });
      return;
    }
    setHoldCartLabel(customerName);
    setHoldCartOpen(true);
  }

  async function holdCart() {
    setBusy(true);
    try {
      const id = await ensureCart();
      await api.carts.update(id, { customerName });
      const held = await api.carts.hold(id, holdCartLabel);
      setHeldCarts((prev) => [held, ...prev.filter((c) => c.id !== held.id)]);
      setLines([]);
      setCartId(null);
      setCustomerName('Walk-in Customer');
      setHoldCartOpen(false);
      toast({
        title: 'Cart held',
        description: 'Press a held cart to resume. (F2)',
        variant: 'success',
      });
    } catch (err) {
      toast({
        title: 'Hold failed',
        description: err instanceof ApiError ? err.message : 'Could not hold cart',
        variant: 'destructive',
      });
    } finally {
      setBusy(false);
      searchRef.current?.focus();
    }
  }

  async function resumeCart(cart: Cart) {
    setBusy(true);
    try {
      const resumed = await api.carts.resume(cart.id);
      setCartId(resumed.id);
      setCustomerName(resumed.customerName || 'Walk-in Customer');
      setLines(
        (resumed.lines ?? []).map((line) => ({
          key: line.id,
          itemId: line.itemId,
          sku: '',
          name: line.description || 'Item',
          quantity: Number(line.quantity),
          unitPrice: Number(line.unitPrice),
        })),
      );
      setHeldCarts((prev) => prev.filter((c) => c.id !== cart.id));
      toast({ title: 'Cart resumed', variant: 'success' });
    } catch (err) {
      toast({
        title: 'Resume failed',
        description: err instanceof ApiError ? err.message : 'Could not resume cart',
        variant: 'destructive',
      });
    } finally {
      setBusy(false);
    }
  }

  async function pay() {
    if (lines.length === 0) {
      toast({ title: 'Nothing to pay', description: 'Scan an item first. (F4)' });
      return;
    }
    const amountNow = Math.min(payAmount ?? subtotal, subtotal);
    if (amountNow <= 0) {
      toast({ title: 'Enter an amount to collect', variant: 'destructive' });
      return;
    }
    setBusy(true);
    try {
      // The bill is built from these local lines directly (not the held-cart's server
      // copy, which only tracks the first-scan quantity) - that's the only way quantity
      // edits, discounts, and serial numbers picked in this session actually get billed.
      const bill = await api.bills.checkout({
        customerId: customerId ?? undefined,
        customerName,
        channel: 'RETAIL',
        priceMode: 'RETAIL',
        payments: [
          {
            method: paymentMethod,
            amount: amountNow,
            tenderedAmount: paymentMethod === 'CASH' ? amountNow : undefined,
            reference: paymentMethod === 'CHEQUE' ? chequeNumber : undefined,
          },
        ],
        lines: lines.map((l) => ({
          itemId: l.itemId,
          quantity: l.quantity,
          unitPrice: getEffectivePrice(l),
          serialIds: l.serialIds,
        })),
      });
      const isPartial = amountNow < subtotal - 0.001;
      toast({
        title: isPartial ? 'Partial payment recorded' : 'Payment complete',
        description: isPartial
          ? `${bill.billNumber} · paid ${money(amountNow)} · balance ${money(bill.balanceDue)}`
          : `${bill.billNumber} · ${money(bill.grandTotal)}`,
        variant: 'success',
      });
      setLastBill(bill);
      setLines([]);
      setCartId(null);
      setCustomerName('Walk-in Customer');
      setCustomerId(null);
      setPayAmount(null);
      setChequeNumber('');
      setBankName('');
      setChequeDate('');
    } catch (err) {
      toast({
        title: 'Checkout failed',
        description: err instanceof ApiError ? err.message : 'Payment did not complete',
        variant: 'destructive',
      });
    } finally {
      setBusy(false);
      searchRef.current?.focus();
    }
  }

  if (!user && !api.tokens.getAccessToken()) {
    return <LoginGate onAuthed={(u) => setUser(u)} />;
  }

  if (pinLocked) {
    return (
      <div className="bg-navy flex h-screen items-center justify-center">
        <div className="w-80 text-center">
          <h1 className="mb-6 text-2xl font-bold text-white">Enter PIN</h1>
          <div className="mb-6 flex justify-center gap-2">
            {[0, 1, 2, 3].map((i) => (
              <div
                key={i}
                className={`border-primary h-4 w-4 rounded-full border ${pinInput.length > i ? 'bg-primary' : 'bg-transparent'}`}
              />
            ))}
          </div>
          <Keypad
            onDigit={(d) => {
              if (pinVerifying) return;
              const next = pinInput + d;
              if (next.length <= 4) {
                setPinInput(next);
                if (next.length === 4) {
                  setPinVerifying(true);
                  api.auth
                    .verifyPin({ pin: next })
                    .then(() => {
                      setPinLocked(false);
                      setPinInput('');
                    })
                    .catch(() => {
                      toast({ title: 'Invalid PIN', variant: 'destructive' });
                      setPinInput('');
                    })
                    .finally(() => setPinVerifying(false));
                }
              }
            }}
            onBackspace={() => setPinInput((s) => s.slice(0, -1))}
            onClear={() => setPinInput('')}
            onEnter={() => {}}
          />
        </div>
      </div>
    );
  }

  if (lastBill) {
    return (
      <div className="flex h-screen flex-col items-center justify-center bg-[#F5F7FA]">
        <div className="w-full max-w-md rounded-2xl bg-white p-8 text-center shadow-lg">
          <div className="mx-auto mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-green-100 text-green-600">
            <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>
          <h2 className="text-navy mb-2 text-2xl font-bold">Payment Successful</h2>
          <p className="text-muted-foreground mb-6">
            Bill No: {lastBill.billNumber} • {money(lastBill.grandTotal)}
          </p>
          <div className="mb-6">
            <Select
              value={receiptFormat}
              onValueChange={(val) => {
                const format = val as ReceiptFormat;
                setReceiptFormat(format);
                setStoredReceiptFormat(format);
              }}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="thermal58">58mm Thermal Receipt</SelectItem>
                <SelectItem value="thermal80">80mm Thermal Receipt</SelectItem>
                <SelectItem value="half-a4">Half A4</SelectItem>
                <SelectItem value="a4">Full A4</SelectItem>
              </SelectContent>
            </Select>
            <p className="text-muted-foreground mt-2 text-xs">
              {receiptFormat.startsWith('thermal') && canPrintRaw()
                ? 'Prints straight to the thermal printer, no dialog.'
                : 'Opens the printer dialog.'}
            </p>
          </div>
          <div className="flex flex-col gap-3">
            <Button
              size="lg"
              onClick={() => {
                void printBestEffort(
                  lastBill.billNumber,
                  toReceiptModel(lastBill, shopTenant, shopOutlet, user?.fullName),
                  receiptFormat,
                );
              }}
            >
              Print Receipt
            </Button>
            <Button size="lg" variant="outline" onClick={() => setLastBill(null)}>
              New Sale
            </Button>
          </div>
        </div>
        <Receipt bill={lastBill} tenant={shopTenant} outlet={shopOutlet} logoLayout={logoLayout} />
      </div>
    );
  }

  return (
    <div className="bg-navy text-navy-foreground flex h-screen flex-col">
      <header className="flex items-center justify-between gap-4 border-b border-white/10 px-4 py-3">
        <div className="flex items-center gap-3">
          <div className="bg-primary text-primary-foreground flex h-10 w-10 items-center justify-center rounded-xl">
            <ScanBarcodeIcon className="h-5 w-5" />
          </div>
          <div>
            <p className="text-lg font-bold tracking-tight">Easy POS</p>
            <p className="text-xs text-white/55">Terminal · F2 hold · F4 pay · Esc clear</p>
          </div>
        </div>

        <div className="flex items-center gap-1 rounded-lg bg-white/10 p-1">
          {(
            [
              { key: 'RETAIL', label: 'Retail', icon: ShoppingCartIcon, need: 'sale.create' },
              { key: 'REPAIRS', label: 'Repairs', icon: WrenchIcon, need: 'repair.view' },
              { key: 'WHOLESALE', label: 'Wholesale', icon: ShoppingBagIcon, need: 'wholesale.view' },
            ] as const
          )
            // A plain cashier has no wholesale or repair-editing rights, so the
            // backend would refuse these anyway - don't offer a tab that 403s.
            .filter(({ need }) => can(need))
            .map(({ key, label, icon: Icon }) => (
            <button
              key={key}
              type="button"
              onClick={() => setMode(key)}
              className={`flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition ${
                mode === key ? 'bg-primary text-navy' : 'text-white/70 hover:bg-white/10'
              }`}
            >
              <Icon className="h-3.5 w-3.5" />
              {label}
            </button>
          ))}
        </div>

        <div className="flex flex-1 items-center gap-2 overflow-x-auto px-2">
          {mode !== 'RETAIL' ? null : heldCarts.length === 0 ? (
            <span className="text-xs text-white/40">No held carts</span>
          ) : (
            heldCarts.map((cart) => (
              <button
                key={cart.id}
                type="button"
                onClick={() => void resumeCart(cart)}
                className="hover:bg-primary/20 rounded-lg border border-white/15 bg-white/5 px-3 py-1.5 text-left text-xs"
              >
                <span className="text-primary font-semibold">{cart.label || 'Held'}</span>
                <span className="ml-2 text-white/60">{cart.customerName || 'Walk-in'}</span>
              </button>
            ))
          )}
        </div>

        <div className="flex items-center gap-4 text-sm">
          <div className="flex items-center gap-2 text-white/80">
            <UserRoundIcon className="text-primary h-4 w-4" />
            {user?.fullName ?? 'Cashier'}
          </div>
          <div className="flex items-center gap-2 text-white/80">
            <StoreIcon className="text-primary h-4 w-4" />
            {user?.tenantSlug ?? 'Outlet'}
          </div>
          {busy ? <Spinner size="sm" className="border-primary" /> : null}
          <button
            onClick={() => setPinSettingOpen(true)}
            className="text-white/80 hover:text-white"
          >
            <SettingsIcon className="h-5 w-5" />
          </button>
        </div>
      </header>

      <Dialog open={holdCartOpen} onOpenChange={setHoldCartOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Hold Cart</DialogTitle>
          </DialogHeader>
          <div className="py-4">
            <label className="text-sm font-medium">Cart Label</label>
            <Input
              value={holdCartLabel}
              onChange={(e) => setHoldCartLabel(e.target.value)}
              placeholder="e.g. Customer name or Table number"
              className="mt-1"
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setHoldCartOpen(false)}>
              Cancel
            </Button>
            <Button onClick={() => void holdCart()}>Hold Cart</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog
        open={pinSettingOpen}
        onOpenChange={(open) => {
          setPinSettingOpen(open);
          if (!open) {
            setNewPin('');
            setPinPassword('');
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Set Screen Lock PIN</DialogTitle>
          </DialogHeader>
          <div className="space-y-3 py-4">
            <div>
              <label className="text-sm font-medium">Your account password</label>
              <Input
                type="password"
                value={pinPassword}
                onChange={(e) => setPinPassword(e.target.value)}
                placeholder="Confirm it's you"
                className="mt-1"
              />
            </div>
            <div>
              <label className="text-sm font-medium">New PIN (4 digits)</label>
              <Input
                type="password"
                maxLength={4}
                value={newPin}
                onChange={(e) => setNewPin(e.target.value.replace(/\D/g, ''))}
                placeholder="Leave empty to disable"
                className="mt-1 text-center text-xl tracking-[1em]"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPinSettingOpen(false)}>
              Cancel
            </Button>
            <Button
              disabled={pinSaving}
              onClick={() => {
                if (newPin.length > 0 && newPin.length !== 4) {
                  toast({ title: 'PIN must be 4 digits', variant: 'destructive' });
                  return;
                }
                if (!pinPassword) {
                  toast({ title: 'Enter your password to confirm', variant: 'destructive' });
                  return;
                }
                setPinSaving(true);
                api.auth
                  .setPin({ password: pinPassword, pin: newPin })
                  .then(() => {
                    const stored = api.auth.getStoredUser();
                    if (stored) {
                      stored.hasPin = newPin.length > 0;
                      api.tokens.setUserJson(JSON.stringify(stored));
                    }
                    toast({
                      title: newPin ? 'PIN saved' : 'PIN disabled',
                      variant: 'success',
                    });
                    setPinSettingOpen(false);
                    setNewPin('');
                    setPinPassword('');
                  })
                  .catch(() => {
                    toast({ title: 'Incorrect password', variant: 'destructive' });
                  })
                  .finally(() => setPinSaving(false));
              }}
            >
              Save PIN
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <SerialPicker
        item={serialPickerItem}
        excludeIds={lines.flatMap((l) => l.serialIds ?? [])}
        onPick={handleSerialPicked}
        onClose={() => setSerialPickerItem(null)}
      />

      {mode === 'REPAIRS' && (
        <div className="min-h-0 flex-1 overflow-y-auto bg-[#F5F7FA]">
          <RepairsPanel />
        </div>
      )}
      {mode === 'WHOLESALE' && (
        <div className="min-h-0 flex-1 overflow-y-auto bg-[#F5F7FA]">
          <WholesalePanel />
        </div>
      )}

      {mode === 'RETAIL' && (
      <div className="grid min-h-0 flex-1 grid-cols-1 lg:grid-cols-2">
        <section className="text-foreground flex min-h-0 flex-col bg-[#F5F7FA]">
          <div className="flex items-center justify-between gap-3 border-b px-4 py-3">
            <div className="flex-1">
              <label className="text-muted-foreground text-xs font-semibold uppercase tracking-wide">
                Customer
              </label>
              <div className="relative mt-1">
                <CustomerSearch
                  placeholder="Walk-in Customer"
                  onSelect={(c) => {
                    setCustomerId(c.id);
                    setCustomerName(c.displayName);
                  }}
                />
              </div>
              {!customerId && (
                <Input
                  value={customerName === 'Walk-in Customer' ? '' : customerName}
                  onChange={(e) => {
                    setCustomerId(null);
                    setCustomerName(e.target.value || 'Walk-in Customer');
                  }}
                  placeholder="or type a walk-in name"
                  className="mt-1"
                />
              )}
            </div>
            <Button
              variant="outline"
              className="mt-5"
              onClick={() => {
                setLines([]);
                setCartId(null);
              }}
            >
              <Trash2Icon className="h-4 w-4" />
              Clear
            </Button>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto p-4">
            {lines.length === 0 ? (
              <div className="border-border flex h-full flex-col items-center justify-center rounded-xl border border-dashed bg-white/70 text-center">
                <p className="text-navy text-lg font-semibold">Cart is empty</p>
                <p className="text-muted-foreground mt-1 text-sm">
                  Scan a barcode or search on the right panel.
                </p>
              </div>
            ) : (
              <ul className="space-y-2">
                {lines.map((line) => (
                  <li
                    key={line.key}
                    className="flex flex-col gap-3 rounded-xl border bg-white px-4 py-3 shadow-sm"
                  >
                    <div className="flex items-center justify-between">
                      <div>
                        <p className="text-navy font-semibold">{line.name}</p>
                        <p className="text-muted-foreground text-xs">
                          {line.sku || line.itemId.slice(0, 8)} · {money(line.unitPrice)}
                        </p>
                        {line.hasSerialTracking && line.serialNumbers?.[0] && (
                          <p className="text-primary font-mono text-xs">
                            SN: {line.serialNumbers[0]}
                          </p>
                        )}
                      </div>
                      <div className="flex items-center gap-3">
                        {line.hasSerialTracking ? (
                          <Button
                            size="icon"
                            variant="outline"
                            className="h-8 w-8"
                            aria-label="Remove"
                            onClick={() =>
                              setLines((prev) => prev.filter((l) => l.key !== line.key))
                            }
                          >
                            <Trash2Icon className="h-4 w-4" />
                          </Button>
                        ) : (
                        <div className="flex items-center gap-2">
                          <Button
                            size="icon"
                            variant="outline"
                            className="h-8 w-8"
                            onClick={() =>
                              setLines((prev) =>
                                prev
                                  .map((l) =>
                                    l.key === line.key
                                      ? { ...l, quantity: l.quantity - 1 }
                                      : l,
                                  )
                                  .filter((l) => l.quantity > 0),
                              )
                            }
                          >
                            −
                          </Button>
                          <Badge variant="secondary" className="min-w-8 justify-center">
                            {line.quantity}
                          </Badge>
                          <Button
                            size="icon"
                            variant="outline"
                            className="h-8 w-8"
                            onClick={() =>
                              setLines((prev) =>
                                prev.map((l) =>
                                  l.key === line.key ? { ...l, quantity: l.quantity + 1 } : l,
                                ),
                              )
                            }
                          >
                            +
                          </Button>
                        </div>
                        )}
                        <div className="w-24 text-right">
                          <p className="text-navy font-bold">
                            {money(line.quantity * getEffectivePrice(line))}
                          </p>
                          {getEffectivePrice(line) < line.unitPrice && (
                            <p className="text-muted-foreground text-xs line-through">
                              {money(line.quantity * line.unitPrice)}
                            </p>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-2 border-t pt-2">
                      <Select
                        value={line.discountType || 'NONE'}
                        onValueChange={(v: any) =>
                          setLines((prev) =>
                            prev.map((l) =>
                              l.key === line.key
                                ? { ...l, discountType: v === 'NONE' ? undefined : v }
                                : l,
                            ),
                          )
                        }
                      >
                        <SelectTrigger className="h-8 w-28 text-xs">
                          <SelectValue placeholder="Discount" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="NONE">No Discount</SelectItem>
                          <SelectItem value="PERCENT">%</SelectItem>
                          <SelectItem value="AMOUNT">Amount</SelectItem>
                        </SelectContent>
                      </Select>
                      {line.discountType && line.discountType !== 'NONE' && (
                        <Input
                          type="number"
                          placeholder="Value"
                          className="h-8 w-20 text-xs"
                          value={line.discountInput || ''}
                          onChange={(e) =>
                            setLines((prev) =>
                              prev.map((l) =>
                                l.key === line.key
                                  ? { ...l, discountInput: Number(e.target.value) }
                                  : l,
                              ),
                            )
                          }
                        />
                      )}
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="border-t bg-white px-4 py-4">
            <div className="flex items-end justify-between">
              <div>
                <p className="text-muted-foreground text-sm">Subtotal</p>
                <p className="text-navy text-3xl font-bold tracking-tight">{money(subtotal)}</p>
              </div>
              <Button variant="secondary" size="lg" onClick={() => promptHoldCart()}>
                <PauseCircleIcon className="h-4 w-4" />
                Hold (F2)
              </Button>
            </div>
          </div>
        </section>

        <section className="flex min-h-0 flex-col bg-gradient-to-b from-[#192A56] via-[#1d3163] to-[#151f3d] p-4 text-white">
          <form onSubmit={onSearchSubmit} className="mb-4">
            <label className="text-primary mb-2 block text-xs font-semibold uppercase tracking-[0.16em]">
              Barcode / search
            </label>
            <Input
              ref={searchRef}
              autoFocus
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Scan or type SKU / barcode…"
              className="h-14 border-white/20 bg-white/10 text-lg text-white placeholder:text-white/40"
            />
          </form>

          <Keypad
            onDigit={(d) => setSearch((s) => s + d)}
            onBackspace={() => setSearch((s) => s.slice(0, -1))}
            onClear={() => setSearch('')}
            onEnter={() => void lookup(search)}
          />

          <div className="mt-4">
            <p className="mb-2 text-xs font-semibold uppercase tracking-[0.16em] text-white/50">
              Payment method
            </p>
            <div className="grid grid-cols-4 gap-2">
              {PAYMENTS.map(({ method, label, icon: Icon }) => (
                <button
                  key={method}
                  type="button"
                  onClick={() => setPaymentMethod(method)}
                  className={`flex flex-col items-center gap-1 rounded-xl border px-3 py-3 text-sm font-semibold transition ${
                    paymentMethod === method
                      ? 'border-primary bg-primary/20 text-primary'
                      : 'border-white/15 bg-white/5 text-white/80 hover:bg-white/10'
                  }`}
                >
                  <Icon className="h-5 w-5" />
                  {label}
                </button>
              ))}
            </div>
            {paymentMethod === 'CHEQUE' && (
              <div className="mt-3 grid grid-cols-3 gap-2">
                <Input
                  placeholder="Cheque No"
                  value={chequeNumber}
                  onChange={(e) => setChequeNumber(e.target.value)}
                  className="bg-white/10 text-white placeholder:text-white/40"
                />
                <Input
                  placeholder="Bank Name"
                  value={bankName}
                  onChange={(e) => setBankName(e.target.value)}
                  className="bg-white/10 text-white placeholder:text-white/40"
                />
                <Input
                  type="date"
                  value={chequeDate}
                  onChange={(e) => setChequeDate(e.target.value)}
                  className="bg-white/10 text-white placeholder:text-white/40 [&::-webkit-calendar-picker-indicator]:invert"
                />
              </div>
            )}
          </div>

          <div className="mt-4">
            <label className="text-xs font-semibold uppercase tracking-[0.16em] text-white/50">
              Amount to collect now (leave full for a normal sale)
            </label>
            <Input
              type="number"
              min="0"
              max={subtotal}
              step="0.01"
              value={payAmount ?? subtotal}
              onChange={(e) => setPayAmount(Math.min(Number(e.target.value) || 0, subtotal))}
              className="mt-1 border-white/20 bg-white/10 text-white placeholder:text-white/40"
            />
            {payAmount != null && payAmount < subtotal && (
              <p className="mt-1 text-xs text-amber-300">
                Partial payment — remaining {money(subtotal - payAmount)} stays as balance due.
              </p>
            )}
          </div>

          <Button
            size="xl"
            className="mt-auto h-16 w-full text-lg"
            disabled={busy || lines.length === 0}
            onClick={() => void pay()}
          >
            Pay {money(payAmount ?? subtotal)} (F4)
          </Button>
        </section>
      </div>
      )}
    </div>
  );
}
