-- ============================================================================
-- V6  Inventory: categories, suppliers, items, serial units, goods received
--     notes, supplier returns, and the append-only stock ledger.
--
-- The desktop app mutated Items.Quantity directly from a dozen call sites, which
-- is how WholesalePanel ended up deducting stock when a line was added to the
-- grid rather than at checkout. Here every change is an immutable
-- stock_movements row and items.quantity_on_hand is a cache derived from it.
-- ============================================================================

CREATE TABLE categories (
    id              uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id       uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    parent_id       uuid REFERENCES categories (id) ON DELETE SET NULL,
    name            varchar(120) NOT NULL,
    display_order   integer NOT NULL DEFAULT 0,
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint NOT NULL DEFAULT 0,

    CONSTRAINT categories_tenant_name_key UNIQUE (tenant_id, name),
    CONSTRAINT categories_not_own_parent_check CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX categories_tenant_idx ON categories (tenant_id) WHERE is_active;


CREATE TABLE suppliers (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    code                varchar(30),
    name                varchar(160) NOT NULL,
    contact_person      varchar(160),
    phone_primary       varchar(32),
    phone_secondary     varchar(32),
    email               citext,
    address_line1       varchar(180),
    address_line2       varchar(180),
    city                varchar(90),
    tax_identifier      varchar(60),
    payment_terms_days  smallint NOT NULL DEFAULT 0,
    total_purchased     numeric(14, 2) NOT NULL DEFAULT 0,
    outstanding_payable numeric(14, 2) NOT NULL DEFAULT 0,
    notes               text,
    is_active           boolean NOT NULL DEFAULT true,
    deleted_at          timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    version             bigint NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX suppliers_tenant_code_idx ON suppliers (tenant_id, code)
    WHERE code IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX suppliers_tenant_name_idx ON suppliers (tenant_id, name) WHERE deleted_at IS NULL;


-- ---------------------------------------------------------------------------
-- items
-- ---------------------------------------------------------------------------
CREATE TABLE items (
    id                      uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id               uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    sku                     varchar(60) NOT NULL,
    name                    varchar(200) NOT NULL,
    description             text,
    category_id             uuid REFERENCES categories (id) ON DELETE SET NULL,
    supplier_id             uuid REFERENCES suppliers (id) ON DELETE SET NULL,
    tax_rate_id             uuid REFERENCES tax_rates (id) ON DELETE SET NULL,
    unit_of_measure         varchar(20) NOT NULL DEFAULT 'PCS',
    image_object_key        varchar(320),

    cost_price              numeric(14, 2) NOT NULL DEFAULT 0,
    retail_price            numeric(14, 2) NOT NULL DEFAULT 0,
    wholesale_price         numeric(14, 2) NOT NULL DEFAULT 0,
    min_selling_price       numeric(14, 2),

    -- Cache of SUM(stock_movements.quantity_delta); see recalculate_item_stock().
    quantity_on_hand        numeric(14, 3) NOT NULL DEFAULT 0,
    quantity_reserved       numeric(14, 3) NOT NULL DEFAULT 0,
    reorder_level           numeric(14, 3) NOT NULL DEFAULT 0,
    reorder_quantity        numeric(14, 3) NOT NULL DEFAULT 0,

    track_inventory         boolean NOT NULL DEFAULT true,
    has_serial_tracking     boolean NOT NULL DEFAULT false,
    allow_negative_stock    boolean NOT NULL DEFAULT false,
    is_old_stock            boolean NOT NULL DEFAULT false,
    warranty_months         smallint NOT NULL DEFAULT 0,
    warranty_label          varchar(60),

    is_active               boolean NOT NULL DEFAULT true,
    deleted_at              timestamptz,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_by              uuid REFERENCES users (id) ON DELETE SET NULL,
    version                 bigint NOT NULL DEFAULT 0,

    CONSTRAINT items_price_check CHECK (
        cost_price >= 0 AND retail_price >= 0 AND wholesale_price >= 0
    ),
    CONSTRAINT items_reserved_check CHECK (quantity_reserved >= 0),
    -- A serialised item is inherently inventory-tracked and whole-numbered.
    CONSTRAINT items_serial_requires_tracking_check CHECK (
        NOT has_serial_tracking OR track_inventory
    )
);

CREATE UNIQUE INDEX items_tenant_sku_idx ON items (tenant_id, sku) WHERE deleted_at IS NULL;
CREATE INDEX items_tenant_name_idx ON items (tenant_id, name) WHERE deleted_at IS NULL;
CREATE INDEX items_name_trgm_idx ON items USING gin (name gin_trgm_ops);
CREATE INDEX items_tenant_category_idx ON items (tenant_id, category_id) WHERE deleted_at IS NULL;
CREATE INDEX items_tenant_supplier_idx ON items (tenant_id, supplier_id) WHERE deleted_at IS NULL;
-- Powers the low-stock dashboard card without a full table scan.
CREATE INDEX items_low_stock_idx ON items (tenant_id)
    WHERE track_inventory AND is_active AND deleted_at IS NULL
      AND quantity_on_hand <= reorder_level;

COMMENT ON COLUMN items.quantity_on_hand IS
    'Denormalised cache of the stock ledger. Never write it outside StockLedgerService.';


-- ---------------------------------------------------------------------------
-- item_barcodes: an item may carry several scannable codes (manufacturer EAN,
-- shop-printed label, supplier code).
-- ---------------------------------------------------------------------------
CREATE TABLE item_barcodes (
    id          uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id   uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    item_id     uuid NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    barcode     varchar(80) NOT NULL,
    is_primary  boolean NOT NULL DEFAULT false,
    created_at  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT item_barcodes_tenant_barcode_key UNIQUE (tenant_id, barcode)
);

CREATE INDEX item_barcodes_item_idx ON item_barcodes (item_id);
CREATE UNIQUE INDEX item_barcodes_one_primary_idx ON item_barcodes (item_id) WHERE is_primary;


-- ---------------------------------------------------------------------------
-- suppliers referenced by GRN before item_serials, so GRNs come first.
-- ---------------------------------------------------------------------------
CREATE TABLE goods_received_notes (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid NOT NULL REFERENCES outlets (id) ON DELETE RESTRICT,
    grn_number          varchar(40) NOT NULL,
    supplier_id         uuid REFERENCES suppliers (id) ON DELETE SET NULL,
    supplier_invoice_no varchar(60),
    status              varchar(20) NOT NULL DEFAULT 'DRAFT',
    received_at         timestamptz NOT NULL DEFAULT now(),
    subtotal            numeric(14, 2) NOT NULL DEFAULT 0,
    tax_amount          numeric(14, 2) NOT NULL DEFAULT 0,
    total               numeric(14, 2) NOT NULL DEFAULT 0,
    notes               text,
    posted_at           timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    updated_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT grn_tenant_number_key UNIQUE (tenant_id, grn_number),
    CONSTRAINT grn_status_check CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED'))
);

CREATE INDEX grn_tenant_received_idx ON goods_received_notes (tenant_id, received_at DESC);
CREATE INDEX grn_supplier_idx ON goods_received_notes (supplier_id);


CREATE TABLE grn_lines (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    grn_id              uuid NOT NULL REFERENCES goods_received_notes (id) ON DELETE CASCADE,
    item_id             uuid NOT NULL REFERENCES items (id) ON DELETE RESTRICT,
    line_number         smallint NOT NULL,
    quantity            numeric(14, 3) NOT NULL,
    unit_cost           numeric(14, 2) NOT NULL DEFAULT 0,
    line_total          numeric(14, 2) NOT NULL DEFAULT 0,
    -- Snapshot so a later price change on the item does not rewrite history.
    retail_price_at_receipt numeric(14, 2),
    warranty_months     smallint NOT NULL DEFAULT 0,

    CONSTRAINT grn_lines_grn_line_key UNIQUE (grn_id, line_number),
    CONSTRAINT grn_lines_quantity_check CHECK (quantity > 0)
);

CREATE INDEX grn_lines_grn_idx ON grn_lines (grn_id);
CREATE INDEX grn_lines_item_idx ON grn_lines (item_id);


-- ---------------------------------------------------------------------------
-- item_serials: one row per physical unit (IMEI / serial number).
--
-- Uniqueness is per tenant, not global as in the desktop schema, where one
-- shop registering a serial would block every other shop from using it.
-- ---------------------------------------------------------------------------
CREATE TABLE item_serials (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    item_id             uuid NOT NULL REFERENCES items (id) ON DELETE CASCADE,
    outlet_id           uuid REFERENCES outlets (id) ON DELETE SET NULL,
    serial_number       varchar(120) NOT NULL,
    status              varchar(20) NOT NULL DEFAULT 'IN_STOCK',
    grn_id              uuid REFERENCES goods_received_notes (id) ON DELETE SET NULL,
    supplier_id         uuid REFERENCES suppliers (id) ON DELETE SET NULL,
    cost_price          numeric(14, 2),
    received_at         timestamptz,
    sold_at             timestamptz,
    -- Polymorphic pointer to whatever consumed the unit: a bill, repair order or
    -- wholesale invoice. Resolved through sold_document_type.
    sold_document_type  varchar(30),
    sold_document_id    uuid,
    warranty_starts_on  date,
    warranty_ends_on    date,
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT item_serials_tenant_serial_key UNIQUE (tenant_id, serial_number),
    CONSTRAINT item_serials_status_check CHECK (
        status IN ('IN_STOCK', 'RESERVED', 'SOLD', 'RETURNED', 'DEFECTIVE', 'WRITTEN_OFF')
    ),
    CONSTRAINT item_serials_sold_doc_check CHECK (
        (sold_document_type IS NULL) = (sold_document_id IS NULL)
    ),
    CONSTRAINT item_serials_sold_doc_type_check CHECK (
        sold_document_type IS NULL OR sold_document_type IN ('BILL', 'REPAIR_ORDER', 'WHOLESALE_INVOICE')
    ),
    CONSTRAINT item_serials_warranty_period_check CHECK (
        warranty_ends_on IS NULL OR warranty_starts_on IS NULL
        OR warranty_ends_on >= warranty_starts_on
    )
);

CREATE INDEX item_serials_item_status_idx ON item_serials (item_id, status);
CREATE INDEX item_serials_tenant_status_idx ON item_serials (tenant_id, status);
CREATE INDEX item_serials_sold_document_idx ON item_serials (sold_document_type, sold_document_id)
    WHERE sold_document_id IS NOT NULL;
CREATE INDEX item_serials_serial_trgm_idx ON item_serials USING gin (serial_number gin_trgm_ops);
CREATE INDEX item_serials_warranty_idx ON item_serials (tenant_id, warranty_ends_on)
    WHERE warranty_ends_on IS NOT NULL;


-- ---------------------------------------------------------------------------
-- supplier_returns: sending faulty stock back upstream. This is the desktop
-- app's `ReturnItems`, which was confusingly named next to customer refunds.
-- ---------------------------------------------------------------------------
CREATE TABLE supplier_returns (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid NOT NULL REFERENCES outlets (id) ON DELETE RESTRICT,
    return_number       varchar(40) NOT NULL,
    supplier_id         uuid REFERENCES suppliers (id) ON DELETE SET NULL,
    status              varchar(20) NOT NULL DEFAULT 'DRAFT',
    returned_at         timestamptz NOT NULL DEFAULT now(),
    reason              varchar(240),
    total_value         numeric(14, 2) NOT NULL DEFAULT 0,
    notes               text,
    posted_at           timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,
    version             bigint NOT NULL DEFAULT 0,

    CONSTRAINT supplier_returns_tenant_number_key UNIQUE (tenant_id, return_number),
    CONSTRAINT supplier_returns_status_check CHECK (status IN ('DRAFT', 'POSTED', 'CANCELLED'))
);

CREATE INDEX supplier_returns_tenant_date_idx ON supplier_returns (tenant_id, returned_at DESC);


CREATE TABLE supplier_return_lines (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    supplier_return_id  uuid NOT NULL REFERENCES supplier_returns (id) ON DELETE CASCADE,
    item_id             uuid NOT NULL REFERENCES items (id) ON DELETE RESTRICT,
    item_serial_id      uuid REFERENCES item_serials (id) ON DELETE SET NULL,
    line_number         smallint NOT NULL,
    quantity            numeric(14, 3) NOT NULL,
    unit_cost           numeric(14, 2) NOT NULL DEFAULT 0,
    line_total          numeric(14, 2) NOT NULL DEFAULT 0,
    reason              varchar(240),

    CONSTRAINT supplier_return_lines_line_key UNIQUE (supplier_return_id, line_number),
    CONSTRAINT supplier_return_lines_quantity_check CHECK (quantity > 0)
);

CREATE INDEX supplier_return_lines_return_idx ON supplier_return_lines (supplier_return_id);
CREATE INDEX supplier_return_lines_item_idx ON supplier_return_lines (item_id);


-- ---------------------------------------------------------------------------
-- stock_movements: append-only ledger. Every quantity change in the system
-- lands here exactly once, in the same transaction as the document that caused
-- it, and items.quantity_on_hand is updated alongside.
--
-- quantity_delta is signed: positive receives, negative issues.
-- ---------------------------------------------------------------------------
CREATE TABLE stock_movements (
    id                  uuid PRIMARY KEY DEFAULT uuid_generate_v7(),
    tenant_id           uuid NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    outlet_id           uuid REFERENCES outlets (id) ON DELETE SET NULL,
    item_id             uuid NOT NULL REFERENCES items (id) ON DELETE RESTRICT,
    item_serial_id      uuid REFERENCES item_serials (id) ON DELETE SET NULL,
    movement_type       varchar(30) NOT NULL,
    quantity_delta      numeric(14, 3) NOT NULL,
    -- Running balance after applying this row, so a stock card can be rendered
    -- without re-summing the whole history.
    balance_after       numeric(14, 3) NOT NULL,
    unit_cost           numeric(14, 2),
    reference_type      varchar(30),
    reference_id        uuid,
    reference_number    varchar(40),
    reason              varchar(240),
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id) ON DELETE SET NULL,

    CONSTRAINT stock_movements_type_check CHECK (
        movement_type IN (
            'GRN', 'SALE', 'SALE_RETURN', 'REPAIR_PART', 'REPAIR_PART_RETURN',
            'WHOLESALE_SALE', 'WHOLESALE_RETURN', 'SUPPLIER_RETURN',
            'ADJUSTMENT_IN', 'ADJUSTMENT_OUT', 'OPENING_BALANCE',
            'TRANSFER_IN', 'TRANSFER_OUT', 'WRITE_OFF'
        )
    ),
    CONSTRAINT stock_movements_delta_nonzero_check CHECK (quantity_delta <> 0),
    CONSTRAINT stock_movements_reference_check CHECK (
        (reference_type IS NULL) = (reference_id IS NULL)
    )
);

CREATE INDEX stock_movements_item_time_idx ON stock_movements (item_id, occurred_at DESC);
CREATE INDEX stock_movements_tenant_time_idx ON stock_movements (tenant_id, occurred_at DESC);
CREATE INDEX stock_movements_reference_idx ON stock_movements (reference_type, reference_id)
    WHERE reference_id IS NOT NULL;
CREATE INDEX stock_movements_serial_idx ON stock_movements (item_serial_id)
    WHERE item_serial_id IS NOT NULL;

-- The ledger is evidence; correcting a mistake means posting a reversing entry.
CREATE TRIGGER stock_movements_append_only
    BEFORE UPDATE OR DELETE ON stock_movements
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();


-- ---------------------------------------------------------------------------
-- Reconciliation helper: rebuild the cached quantity from the ledger. Used by
-- the nightly integrity job and by support when investigating a discrepancy.
-- ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION recalculate_item_stock(p_item_id uuid)
RETURNS numeric
LANGUAGE plpgsql
AS $$
DECLARE
    v_total numeric(14, 3);
BEGIN
    SELECT COALESCE(SUM(quantity_delta), 0)
      INTO v_total
      FROM stock_movements
     WHERE item_id = p_item_id;

    UPDATE items
       SET quantity_on_hand = v_total,
           updated_at = now()
     WHERE id = p_item_id;

    RETURN v_total;
END;
$$;


CREATE TRIGGER categories_set_updated_at BEFORE UPDATE ON categories
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER suppliers_set_updated_at BEFORE UPDATE ON suppliers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER items_set_updated_at BEFORE UPDATE ON items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER grn_set_updated_at BEFORE UPDATE ON goods_received_notes
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER item_serials_set_updated_at BEFORE UPDATE ON item_serials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER supplier_returns_set_updated_at BEFORE UPDATE ON supplier_returns
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
