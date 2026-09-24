export type UUID = string;

// --- Platform admin ---------------------------------------------------------

export type TenantStatus = 'TRIAL' | 'ACTIVE' | 'PAST_DUE' | 'SUSPENDED' | 'CANCELLED';
export type SubscriptionStatus =
  | 'TRIALING'
  | 'ACTIVE'
  | 'PAST_DUE'
  | 'GRACE'
  | 'SUSPENDED'
  | 'CANCELLED'
  | 'EXPIRED';

export interface PlatformTenantSummary {
  id: UUID;
  slug: string;
  businessName: string;
  contactEmail?: string | null;
  status: TenantStatus;
  defaultCurrency: string;
  createdAt: string;
  suspendedAt?: string | null;
  subscriptionStatus?: SubscriptionStatus | null;
  planCode?: string | null;
}

export interface PlatformTenantDetail {
  id: UUID;
  slug: string;
  businessName: string;
  legalName?: string | null;
  taxIdentifier?: string | null;
  status: TenantStatus;
  defaultCurrency: string;
  defaultLocale: string;
  timeZone: string;
  contactEmail?: string | null;
  contactPhone?: string | null;
  onboardedAt?: string | null;
  suspendedAt?: string | null;
  suspensionReason?: string | null;
  createdAt: string;
  subscriptionStatus?: SubscriptionStatus | null;
  planCode?: string | null;
  planName?: string | null;
  trialEndsAt?: string | null;
  currentPeriodEnd?: string | null;
  gracePeriodEndsAt?: string | null;
}

export interface PlatformImpersonateResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: string;
  tenantId: UUID;
  tenantSlug: string;
  actingUserId: UUID;
  impersonatorId: UUID;
}

export interface PlatformMetrics {
  totalTenants: number;
  activeTenants: number;
  trialTenants: number;
  suspendedTenants: number;
  pastDueSubscriptions: number;
  graceSubscriptions: number;
  mrr: number | string;
  arr: number | string;
  churnRateStub: number;
  currency: string;
}

export type FeatureCode =
  | 'RETAIL_BILLING'
  | 'INVENTORY'
  | 'SERIAL_TRACKING'
  | 'GRN'
  | 'REPAIRS'
  | 'WHOLESALE'
  | 'QUOTATIONS'
  | 'CREDIT_NOTES'
  | 'MULTI_OUTLET'
  | 'ADVANCED_REPORTS'
  | 'JASPER_EXPORT'
  | 'AUDIT_TRAIL'
  | 'API_ACCESS'
  | 'THERMAL_PRINTING'
  | 'BARCODE_LABELS';

export interface PlanFeature {
  featureCode: FeatureCode;
  enabled?: boolean;
  limitValue?: number | null;
}

export interface Plan {
  id: UUID;
  code: string;
  name: string;
  description?: string | null;
  currency: string;
  priceMonthly: number | string;
  priceYearly: number | string;
  trialDays: number;
  maxUsers?: number | null;
  maxOutlets?: number | null;
  maxItems?: number | null;
  maxMonthlyBills?: number | null;
  publicPlan: boolean;
  active: boolean;
  displayOrder: number;
  features: PlanFeature[];
}

export interface UpsertPlanRequest {
  code: string;
  name: string;
  description?: string;
  currency?: string;
  priceMonthly?: number;
  priceYearly?: number;
  trialDays?: number;
  maxUsers?: number;
  maxOutlets?: number;
  maxItems?: number;
  maxMonthlyBills?: number;
  publicPlan?: boolean;
  active?: boolean;
  displayOrder?: number;
  features?: PlanFeature[];
}

export interface Tenant {
  id: UUID;
  slug: string;
  businessName: string;
  legalName?: string | null;
  taxIdentifier?: string | null;
  status: string;
  defaultCurrency: string;
  timeZone: string;
  contactEmail?: string | null;
  contactPhone?: string | null;
}

export interface Outlet {
  id: UUID;
  code: string;
  name: string;
  defaultOutlet: boolean;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  phonePrimary?: string | null;
  phoneSecondary?: string | null;
  email?: string | null;
  website?: string | null;
  receiptFooter?: string | null;
  logoDataUrl?: string | null;
}

export interface User {
  id: UUID;
  email: string;
  fullName: string;
  phone?: string | null;
  tenantId: UUID;
  tenantSlug: string;
  roles: string[];
  permissions: string[];
  platformAdmin: boolean;
  hasPin: boolean;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  expiresAt: string;
  user: User;
}

export interface LoginRequest {
  emailOrUsername: string;
  password: string;
  tenantSlug?: string;
  deviceId?: string;
  deviceLabel?: string;
}

export interface SignupRequest {
  businessName: string;
  contactEmail: string;
  password: string;
  fullName: string;
  slug?: string;
  phone?: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface Barcode {
  id: UUID;
  barcode: string;
  primaryBarcode: boolean;
}

export interface Category {
  id: UUID;
  name: string;
  parentId?: UUID | null;
  displayOrder: number;
  active: boolean;
}

export interface CategoryRequest {
  name: string;
  parentId?: UUID | null;
  displayOrder?: number;
  active?: boolean;
}

export interface Supplier {
  id: UUID;
  code?: string | null;
  name: string;
  contactPerson?: string | null;
  phonePrimary?: string | null;
  phoneSecondary?: string | null;
  email?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  taxIdentifier?: string | null;
  paymentTermsDays: number;
  totalPurchased: number | string;
  outstandingPayable: number | string;
  notes?: string | null;
  active: boolean;
}

export interface SupplierRequest {
  code?: string;
  name: string;
  contactPerson?: string;
  phonePrimary?: string;
  phoneSecondary?: string;
  email?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  taxIdentifier?: string;
  paymentTermsDays?: number;
  notes?: string;
  active?: boolean;
}

export interface GrnLineRequest {
  itemId: UUID;
  quantity: number | string;
  unitCost: number | string;
  retailPriceAtReceipt?: number | string;
  warrantyMonths?: number;
  serialNumbers?: string[];
}

export interface GrnCreateRequest {
  outletId?: UUID;
  supplierId?: UUID;
  supplierInvoiceNo?: string;
  notes?: string;
  receivedAt?: string;
  lines: GrnLineRequest[];
}

export interface GrnLine {
  id: UUID;
  itemId: UUID;
  lineNumber: number;
  quantity: number | string;
  unitCost: number | string;
  lineTotal: number | string;
  retailPriceAtReceipt: number | string;
  warrantyMonths: number;
}

export type GrnStatus = 'DRAFT' | 'POSTED' | 'CANCELLED';

export interface Grn {
  id: UUID;
  outletId: UUID;
  grnNumber: string;
  supplierId?: UUID | null;
  supplierInvoiceNo?: string | null;
  status: GrnStatus;
  receivedAt: string;
  subtotal: number | string;
  taxAmount: number | string;
  total: number | string;
  notes?: string | null;
  postedAt?: string | null;
  lines: GrnLine[];
}

export interface Item {
  id: UUID;
  sku: string;
  name: string;
  description?: string | null;
  categoryId?: UUID | null;
  supplierId?: UUID | null;
  taxRateId?: UUID | null;
  unitOfMeasure: string;
  costPrice: number | string;
  retailPrice: number | string;
  wholesalePrice: number | string;
  minSellingPrice: number | string;
  quantityOnHand: number | string;
  quantityReserved: number | string;
  quantityDamaged: number | string;
  reorderLevel: number | string;
  reorderQuantity: number | string;
  trackInventory: boolean;
  hasSerialTracking: boolean;
  allowNegativeStock: boolean;
  oldStock: boolean;
  warrantyMonths: number;
  warrantyLabel?: string | null;
  active: boolean;
  barcodes: Barcode[];
}

export type SerialStatus =
  | 'IN_STOCK'
  | 'RESERVED'
  | 'SOLD'
  | 'RETURNED'
  | 'DEFECTIVE'
  | 'IN_REPAIR'
  | 'RMA'
  | 'WRITTEN_OFF';

export type UnitCondition = 'NEW' | 'OPEN_BOX' | 'USED' | 'REFURB';

export type UnitGrade = 'A' | 'B' | 'C' | 'D';

export interface Serial {
  id: UUID;
  itemId: UUID;
  outletId?: UUID | null;
  serialNumber: string;
  imei1?: string | null;
  imei2?: string | null;
  condition: UnitCondition;
  grade?: UnitGrade | null;
  batteryHealth?: number | null;
  status: SerialStatus;
  costPrice?: number | string | null;
  warrantyStartsOn?: string | null;
  warrantyEndsOn?: string | null;
  notes?: string | null;
}

export interface SerialEvent {
  event: string;
  quantityDelta: number | string;
  unitCost?: number | string | null;
  referenceType?: string | null;
  referenceId?: UUID | null;
  referenceNumber?: string | null;
  reason?: string | null;
  occurredAt: string;
}

export interface SerialLifecycle {
  unit: Serial;
  itemName?: string | null;
  itemSku?: string | null;
  underWarranty: boolean;
  timeline: SerialEvent[];
}

export type ShiftStatus = 'OPEN' | 'CLOSED';

export type CashMovementType = 'PAY_IN' | 'PAYOUT' | 'DROP';

export interface CashMovement {
  id: UUID;
  movementType: CashMovementType;
  amount: number | string;
  reason?: string | null;
  reference?: string | null;
  occurredAt: string;
}

/** Drawer reconciliation for one shift - the X and Z report payload. */
export interface ShiftReport {
  shiftId: UUID;
  shiftNumber: string;
  status: ShiftStatus;
  outletId: UUID;
  openedAt: string;
  closedAt?: string | null;
  openingFloat: number | string;
  cashSales: number | string;
  cashRepairs: number | string;
  cashWholesale: number | string;
  cashRefunds: number | string;
  payIns: number | string;
  payouts: number | string;
  drops: number | string;
  expectedCash: number | string;
  countedCash?: number | string | null;
  variance?: number | string | null;
  movements: CashMovement[];
}

export interface VatRateRow {
  ratePercent: number | string;
  taxableValue: number | string;
  vatAmount: number | string;
}

/** What a VAT return is filed from. */
export interface VatOutputReport {
  from: string;
  to: string;
  vatRegistered: boolean;
  vatTin?: string | null;
  billCount: number;
  grossSales: number | string;
  taxableValue: number | string;
  zeroRatedValue: number | string;
  vatOutput: number | string;
  refundedGross: number | string;
  refundedVat: number | string;
  netVatPayable: number | string;
  currency: string;
  byRate: VatRateRow[];
}

export interface ProfitLineRow {
  billId: UUID;
  billNumber: string;
  billedAt: string;
  itemSku: string;
  itemName: string;
  serialNumber?: string | null;
  imei1?: string | null;
  quantity: number | string;
  revenue: number | string;
  cost: number | string;
  grossProfit: number | string;
  marginPercent: number | string;
}

export interface ProfitReport {
  from: string;
  to: string;
  totalRevenue: number | string;
  totalCost: number | string;
  totalGrossProfit: number | string;
  marginPercent: number | string;
  currency: string;
  lines: ProfitLineRow[];
}

export interface StockValuationRow {
  itemId: UUID;
  sku: string;
  itemName: string;
  quantityOnHand: number | string;
  unitCost: number | string;
  value: number | string;
  serialised: boolean;
}

export interface StockValuationReport {
  totalValue: number | string;
  serialisedValue: number | string;
  quantityValue: number | string;
  damagedValue: number | string;
  currency: string;
  items: StockValuationRow[];
}

export interface DamagedItem {
  itemId: UUID;
  sku: string;
  itemName: string;
  supplierId?: UUID | null;
  supplierName?: string | null;
  quantityDamaged: number | string;
  costPrice: number | string;
}

export interface ItemRequest {
  sku: string;
  name: string;
  description?: string;
  categoryId?: UUID | null;
  supplierId?: UUID | null;
  taxRateId?: UUID | null;
  unitOfMeasure?: string;
  costPrice?: number | string;
  retailPrice?: number | string;
  wholesalePrice?: number | string;
  minSellingPrice?: number | string;
  reorderLevel?: number | string;
  reorderQuantity?: number | string;
  /** Opening stock count. Only applied when creating a new item. */
  initialQuantity?: number | string;
  trackInventory?: boolean;
  hasSerialTracking?: boolean;
  allowNegativeStock?: boolean;
  oldStock?: boolean;
  warrantyMonths?: number;
  warrantyLabel?: string;
  active?: boolean;
  barcodes?: Array<{ barcode: string; primaryBarcode?: boolean }>;
}

export type CustomerType = 'RETAIL' | 'WHOLESALE' | 'BOTH';

export interface Customer {
  id: UUID;
  code?: string | null;
  displayName: string;
  customerType: CustomerType;
  phonePrimary?: string | null;
  phoneSecondary?: string | null;
  email?: string | null;
  addressLine1?: string | null;
  addressLine2?: string | null;
  city?: string | null;
  taxIdentifier?: string | null;
  creditLimit: number | string;
  outstandingAmount: number | string;
  lifetimeSales: number | string;
  loyaltyPoints: number;
  defaultTaxRateId?: UUID | null;
  notes?: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CustomerRequest {
  code?: string;
  displayName: string;
  customerType: CustomerType;
  phonePrimary?: string;
  phoneSecondary?: string;
  email?: string;
  addressLine1?: string;
  addressLine2?: string;
  city?: string;
  taxIdentifier?: string;
  creditLimit?: number | string;
  defaultTaxRateId?: UUID | null;
  notes?: string;
  active?: boolean;
}

export type CartStatus = 'OPEN' | 'HELD' | 'CHECKED_OUT' | 'ABANDONED';
export type PriceMode = 'RETAIL' | 'WHOLESALE';
export type DiscountType = 'NONE' | 'PERCENT' | 'AMOUNT';
export type BillStatus =
  | 'COMPLETED'
  | 'PARTIALLY_PAID'
  | 'UNPAID'
  | 'VOIDED'
  | 'REFUNDED'
  | 'PARTIALLY_REFUNDED';
export type BillChannel = 'RETAIL' | 'REPAIR' | 'WHOLESALE' | 'ONLINE';
export type PaymentMethod = 'CASH' | 'CARD' | 'BANK_TRANSFER' | 'CHEQUE' | 'CREDIT' | 'OTHER';

export interface CartLine {
  id: UUID;
  lineNumber: number;
  itemId: UUID;
  description?: string | null;
  quantity: number | string;
  unitPrice: number | string;
  discountType?: DiscountType | null;
  discountInput?: number | string | null;
  taxRateId?: UUID | null;
  warrantyLabel?: string | null;
  serialIds?: UUID[];
}

export interface Cart {
  id: UUID;
  status: CartStatus;
  outletId: UUID;
  customerId?: UUID | null;
  customerName?: string | null;
  priceMode: PriceMode;
  label?: string | null;
  note?: string | null;
  heldAt?: string | null;
  convertedBillId?: UUID | null;
  lines: CartLine[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateCartRequest {
  customerId?: UUID;
  customerName?: string;
  priceMode?: PriceMode;
  label?: string;
  note?: string;
}

export interface CartLineRequest {
  itemId: UUID;
  quantity: number | string;
  unitPrice?: number | string;
  discountType?: DiscountType;
  discountInput?: number | string;
  taxRateId?: UUID;
  warrantyLabel?: string;
  description?: string;
  serialIds?: UUID[];
}

export interface BillLine {
  id: UUID;
  lineNumber: number;
  itemId: UUID;
  itemSku: string;
  itemName: string;
  quantity: number | string;
  unitPrice: number | string;
  unitCost: number | string;
  grossAmount: number | string;
  discountType?: DiscountType | null;
  discountInput?: number | string | null;
  discountAmount: number | string;
  netAmount: number | string;
  taxRatePercent: number | string;
  taxAmount: number | string;
  taxInclusive: boolean;
  lineTotal: number | string;
  warrantyLabel?: string | null;
  warrantyMonths: number;
  quantityReturned: number | string;
}

export interface Payment {
  id: UUID;
  method: PaymentMethod;
  amount: number | string;
  tenderedAmount?: number | string | null;
  changeAmount?: number | string | null;
  reference?: string | null;
  receivedAt: string;
}

export interface Bill {
  id: UUID;
  billNumber: string;
  status: BillStatus;
  channel: BillChannel;
  priceMode: PriceMode;
  outletId: UUID;
  customerId?: UUID | null;
  customerName?: string | null;
  customerPhone?: string | null;
  currency: string;
  subtotal: number | string;
  lineDiscountTotal: number | string;
  billDiscountType?: DiscountType | null;
  billDiscountInput?: number | string | null;
  billDiscountAmount: number | string;
  taxTotal: number | string;
  roundingAdjustment: number | string;
  grandTotal: number | string;
  creditApplied: number | string;
  amountPaid: number | string;
  balanceDue: number | string;
  costOfGoods: number | string;
  note?: string | null;
  billedAt: string;
  dueDate?: string | null;
  voidedAt?: string | null;
  voidReason?: string | null;
  sourceCartId?: UUID | null;
  lines: BillLine[];
  payments: Payment[];
}

export type RefundScope = 'FULL' | 'PARTIAL';
export type RefundSettlement = 'CASH' | 'CARD_REVERSAL' | 'BANK_TRANSFER' | 'CREDIT_NOTE' | 'CHEQUE';

export interface RefundLineRequest {
  billLineId: UUID;
  quantity: number | string;
  serialIds?: UUID[];
  conditionNote?: string;
}

export interface CreateRefundRequest {
  billId: UUID;
  refundType?: RefundScope;
  settlement?: RefundSettlement;
  restock?: boolean;
  reason?: string;
  note?: string;
  idempotencyKey?: string;
  lines?: RefundLineRequest[];
}

export interface RefundLine {
  id: UUID;
  billLineId: UUID;
  itemId: UUID;
  itemName: string;
  quantity: number | string;
  unitPrice: number | string;
  lineTotal: number | string;
  restocked: boolean;
}

export interface Refund {
  id: UUID;
  refundNumber: string;
  sourceId: UUID;
  sourceNumber: string;
  refundScope: RefundScope;
  settlement: RefundSettlement;
  refundAmount: number | string;
  restock: boolean;
  reason?: string | null;
  creditNoteId?: UUID | null;
  refundedAt: string;
  lines: RefundLine[];
}

export interface BillSummary {
  id: UUID;
  billNumber: string;
  status: BillStatus;
  customerName?: string | null;
  grandTotal: number | string;
  amountPaid: number | string;
  balanceDue: number | string;
  billedAt: string;
}

export interface CheckoutRequest {
  cartId?: UUID;
  lines?: Array<{
    itemId: UUID;
    quantity: number | string;
    unitPrice: number | string;
    discountType?: DiscountType;
    discountInput?: number | string;
    taxRateId?: UUID;
    warrantyLabel?: string;
    serialIds?: UUID[];
  }>;
  payments?: Array<{
    method: PaymentMethod;
    amount: number | string;
    tenderedAmount?: number | string;
    reference?: string;
    note?: string;
    idempotencyKey?: string;
  }>;
  creditNoteApplications?: Array<{ creditNoteId: UUID; amount: number | string }>;
  idempotencyKey?: string;
  customerId?: UUID;
  customerName?: string;
  customerPhone?: string;
  priceMode?: PriceMode;
  channel?: BillChannel;
  billDiscountType?: DiscountType;
  billDiscountInput?: number | string;
  note?: string;
  deviceId?: string;
  dueDate?: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ApiErrorBody {
  message?: string;
  error?: string;
  code?: string;
  details?: unknown;
}

export class ApiError extends Error {
  status: number;
  body?: ApiErrorBody;

  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.body = body;
  }
}
