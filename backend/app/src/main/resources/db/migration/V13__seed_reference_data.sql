-- ============================================================================
-- V13  Platform reference data: permission catalogue, system roles, and the
--      published subscription plans.
--
-- Idempotent throughout so it is safe to re-run against an existing database.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Permissions. requires_step_up marks the destructive ones that demand a fresh
-- password, replacing the desktop app's habit of re-prompting for the admin
-- password before opening a whole panel.
-- ---------------------------------------------------------------------------
INSERT INTO permissions (code, module, description, requires_step_up) VALUES
    ('dashboard.view',          'DASHBOARD',    'View the dashboard and its metrics', false),

    ('sale.create',             'SALES',        'Ring up and complete a sale', false),
    ('sale.view',               'SALES',        'Browse bill history', false),
    ('sale.hold',               'SALES',        'Hold and resume carts', false),
    ('sale.discount.line',      'SALES',        'Apply a discount to a single line', false),
    ('sale.discount.bill',      'SALES',        'Apply a discount to a whole bill', false),
    ('sale.edit',               'SALES',        'Amend a completed bill', true),
    ('sale.void',               'SALES',        'Void a bill', true),
    ('sale.refund',             'SALES',        'Refund a bill', true),
    ('sale.reprint',            'SALES',        'Reprint a receipt or invoice', false),

    ('payment.record',          'PAYMENTS',     'Record a payment against a document', false),
    ('payment.reverse',         'PAYMENTS',     'Reverse a recorded payment', true),
    ('cheque.manage',           'PAYMENTS',     'Clear, deposit or bounce cheques', false),

    ('credit_note.issue',       'CREDIT',       'Issue store credit', true),
    ('credit_note.redeem',      'CREDIT',       'Redeem store credit against a sale', false),
    ('credit_note.void',        'CREDIT',       'Void a credit note', true),

    ('item.view',               'INVENTORY',    'View items and stock levels', false),
    ('item.create',             'INVENTORY',    'Create items', false),
    ('item.edit',               'INVENTORY',    'Edit items and prices', false),
    ('item.delete',             'INVENTORY',    'Delete items', true),
    ('item.cost.view',          'INVENTORY',    'See cost prices and margins', false),
    ('stock.adjust',            'INVENTORY',    'Post a manual stock adjustment', true),
    ('stock.count',            'INVENTORY',    'Perform a stock count', false),
    ('barcode.print',           'INVENTORY',    'Generate and print barcode labels', false),

    ('grn.view',                'PURCHASING',   'View goods received notes', false),
    ('grn.create',              'PURCHASING',   'Receive goods into stock', false),
    ('grn.post',                'PURCHASING',   'Post a GRN, moving stock', false),
    ('supplier.manage',         'PURCHASING',   'Create and edit suppliers', false),
    ('supplier_return.manage',  'PURCHASING',   'Return stock to a supplier', true),

    ('serial.view',             'SERIALS',      'Look up serial and IMEI history', false),
    ('serial.manage',           'SERIALS',      'Add, edit or write off serial units', true),

    ('repair.view',             'REPAIRS',      'View repair orders', false),
    ('repair.create',           'REPAIRS',      'Book in a repair', false),
    ('repair.edit',             'REPAIRS',      'Edit a repair order', false),
    ('repair.status',           'REPAIRS',      'Advance repair status', false),
    ('repair.deliver',          'REPAIRS',      'Hand a device back to the customer', false),
    ('repair.refund',           'REPAIRS',      'Refund a repair', true),

    ('wholesale.view',          'WHOLESALE',    'View wholesale invoices', false),
    ('wholesale.create',        'WHOLESALE',    'Raise a wholesale invoice', false),
    ('wholesale.post',          'WHOLESALE',    'Post a wholesale invoice on credit', false),
    ('wholesale.credit_limit',  'WHOLESALE',    'Set customer credit limits', true),
    ('wholesale.override_limit','WHOLESALE',    'Exceed a customer credit limit', true),

    ('quotation.view',          'QUOTATIONS',   'View quotations', false),
    ('quotation.manage',        'QUOTATIONS',   'Create and send quotations', false),
    ('quotation.convert',       'QUOTATIONS',   'Convert a quotation into a bill', false),

    ('customer.view',           'CRM',          'View customers', false),
    ('customer.manage',         'CRM',          'Create and edit customers', false),
    ('customer.delete',         'CRM',          'Delete a customer', true),

    ('report.view',             'REPORTS',      'Run standard reports', false),
    ('report.financial',        'REPORTS',      'Run financial and profit reports', false),
    ('report.export',           'REPORTS',      'Export reports to PDF or Excel', false),
    ('audit.view',              'AUDIT',        'Read the audit trail', false),

    ('user.view',               'ADMIN',        'View staff accounts', false),
    ('user.manage',             'ADMIN',        'Invite, edit and disable staff', true),
    ('role.manage',             'ADMIN',        'Define roles and permissions', true),
    ('settings.view',           'ADMIN',        'View shop settings', false),
    ('settings.manage',         'ADMIN',        'Change shop settings and branding', true),
    ('tax.manage',              'ADMIN',        'Configure tax rates', true),
    ('outlet.manage',           'ADMIN',        'Create and edit outlets', true),
    ('device.manage',           'ADMIN',        'Approve and revoke POS terminals', true),
    ('billing.manage',          'ADMIN',        'Manage the subscription and payment method', true),
    ('data.export',             'ADMIN',        'Export the shop database', true),

    ('platform.tenant.view',    'PLATFORM',     'View all tenants', false),
    ('platform.tenant.manage',  'PLATFORM',     'Suspend, resume and edit tenants', true),
    ('platform.plan.manage',    'PLATFORM',     'Manage plans and pricing', true),
    ('platform.impersonate',    'PLATFORM',     'Enter a tenant for support purposes', true),
    ('platform.metrics.view',   'PLATFORM',     'View platform-wide revenue metrics', false)
ON CONFLICT (code) DO UPDATE
    SET module = EXCLUDED.module,
        description = EXCLUDED.description,
        requires_step_up = EXCLUDED.requires_step_up;


-- ---------------------------------------------------------------------------
-- System roles, shared by every tenant (tenant_id IS NULL).
-- ---------------------------------------------------------------------------
INSERT INTO roles (code, name, description, is_system) VALUES
    ('PLATFORM_ADMIN', 'Platform Administrator',
     'Operates the SaaS itself. Has no access to tenant data without impersonating.', true),
    ('OWNER',   'Owner',      'Full control of the shop, including billing and staff.', true),
    ('MANAGER', 'Manager',    'Runs day-to-day operations; cannot change billing or roles.', true),
    ('CASHIER', 'Cashier',    'Sells, takes payment and prints receipts.', true),
    ('TECHNICIAN', 'Technician', 'Works the repair queue and consumes parts.', true),
    ('ACCOUNTANT', 'Accountant', 'Read-only access to financials, reports and receivables.', true)
ON CONFLICT (code) WHERE tenant_id IS NULL DO UPDATE
    SET name = EXCLUDED.name,
        description = EXCLUDED.description;


-- ---------------------------------------------------------------------------
-- Role to permission mapping.
-- ---------------------------------------------------------------------------

-- OWNER: everything except platform administration.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.code = 'OWNER' AND r.tenant_id IS NULL
   AND p.module <> 'PLATFORM'
ON CONFLICT DO NOTHING;

-- PLATFORM_ADMIN: only platform administration. Tenant data requires an audited
-- impersonation, which grants tenant permissions for that session alone.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.code = 'PLATFORM_ADMIN' AND r.tenant_id IS NULL
   AND p.module = 'PLATFORM'
ON CONFLICT DO NOTHING;

-- MANAGER: operations, minus billing, roles and destructive admin.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
  FROM roles r
  CROSS JOIN permissions p
 WHERE r.code = 'MANAGER' AND r.tenant_id IS NULL
   AND p.module NOT IN ('PLATFORM')
   AND p.code NOT IN (
       'billing.manage', 'role.manage', 'outlet.manage', 'data.export',
       'item.delete', 'customer.delete', 'tax.manage'
   )
ON CONFLICT DO NOTHING;

-- CASHIER: the till. No cost visibility, no voids, no refunds.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
  FROM roles r
  JOIN permissions p ON p.code IN (
        'dashboard.view',
        'sale.create', 'sale.view', 'sale.hold', 'sale.discount.line', 'sale.reprint',
        'payment.record',
        'credit_note.redeem',
        'item.view', 'barcode.print',
        'serial.view',
        'customer.view', 'customer.manage',
        'quotation.view', 'quotation.manage',
        'repair.view', 'repair.create',
        'settings.view'
  )
 WHERE r.code = 'CASHIER' AND r.tenant_id IS NULL
ON CONFLICT DO NOTHING;

-- TECHNICIAN: the workshop.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
  FROM roles r
  JOIN permissions p ON p.code IN (
        'dashboard.view',
        'repair.view', 'repair.create', 'repair.edit', 'repair.status', 'repair.deliver',
        'item.view', 'serial.view',
        'customer.view', 'customer.manage',
        'grn.view',
        'settings.view'
  )
 WHERE r.code = 'TECHNICIAN' AND r.tenant_id IS NULL
ON CONFLICT DO NOTHING;

-- ACCOUNTANT: read-only financial oversight plus cheque handling.
INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, p.code
  FROM roles r
  JOIN permissions p ON p.code IN (
        'dashboard.view',
        'sale.view', 'wholesale.view', 'repair.view', 'quotation.view',
        'item.view', 'item.cost.view', 'grn.view', 'serial.view',
        'customer.view',
        'payment.record', 'cheque.manage',
        'report.view', 'report.financial', 'report.export',
        'audit.view', 'settings.view'
  )
 WHERE r.code = 'ACCOUNTANT' AND r.tenant_id IS NULL
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- Subscription plans. Prices in LKR; the free tier exists so a shop can keep
-- reading its data after a trial lapses rather than being locked out.
-- ---------------------------------------------------------------------------
INSERT INTO plans (
    code, name, description, currency, price_monthly, price_yearly, trial_days,
    max_users, max_outlets, max_items, max_monthly_bills, is_public, display_order
) VALUES
    ('FREE', 'Free',
     'Read-only access to your existing data. No new sales.',
     'LKR', 0, 0, 0, 1, 1, 100, 0, false, 0),

    ('STARTER', 'Starter',
     'Single-till retail billing and inventory for a small shop.',
     'LKR', 2500, 25000, 14, 2, 1, 1000, 1500, true, 1),

    ('PROFESSIONAL', 'Professional',
     'Adds repairs, serial tracking, goods receiving and full reporting.',
     'LKR', 5900, 59000, 14, 8, 1, 20000, NULL, true, 2),

    ('BUSINESS', 'Business',
     'Wholesale credit control, multiple outlets and unlimited staff.',
     'LKR', 12900, 129000, 14, NULL, 5, NULL, NULL, true, 3),

    ('ENTERPRISE', 'Enterprise',
     'Unlimited everything, API access and a priority support channel.',
     'LKR', 24900, 249000, 14, NULL, NULL, NULL, NULL, true, 4)
ON CONFLICT (code) DO UPDATE
    SET name = EXCLUDED.name,
        description = EXCLUDED.description,
        price_monthly = EXCLUDED.price_monthly,
        price_yearly = EXCLUDED.price_yearly,
        max_users = EXCLUDED.max_users,
        max_outlets = EXCLUDED.max_outlets,
        max_items = EXCLUDED.max_items,
        max_monthly_bills = EXCLUDED.max_monthly_bills,
        display_order = EXCLUDED.display_order;


-- Plan entitlements.
INSERT INTO plan_features (plan_id, feature_code)
SELECT p.id, f.code
  FROM plans p
  CROSS JOIN (VALUES
        ('RETAIL_BILLING'), ('INVENTORY'), ('CREDIT_NOTES'), ('THERMAL_PRINTING')
  ) AS f(code)
 WHERE p.code IN ('STARTER', 'PROFESSIONAL', 'BUSINESS', 'ENTERPRISE')
ON CONFLICT DO NOTHING;

INSERT INTO plan_features (plan_id, feature_code)
SELECT p.id, f.code
  FROM plans p
  CROSS JOIN (VALUES
        ('SERIAL_TRACKING'), ('GRN'), ('REPAIRS'), ('QUOTATIONS'),
        ('ADVANCED_REPORTS'), ('BARCODE_LABELS'), ('AUDIT_TRAIL')
  ) AS f(code)
 WHERE p.code IN ('PROFESSIONAL', 'BUSINESS', 'ENTERPRISE')
ON CONFLICT DO NOTHING;

INSERT INTO plan_features (plan_id, feature_code)
SELECT p.id, f.code
  FROM plans p
  CROSS JOIN (VALUES
        ('WHOLESALE'), ('MULTI_OUTLET'), ('JASPER_EXPORT')
  ) AS f(code)
 WHERE p.code IN ('BUSINESS', 'ENTERPRISE')
ON CONFLICT DO NOTHING;

INSERT INTO plan_features (plan_id, feature_code)
SELECT p.id, 'API_ACCESS'
  FROM plans p
 WHERE p.code = 'ENTERPRISE'
ON CONFLICT DO NOTHING;
