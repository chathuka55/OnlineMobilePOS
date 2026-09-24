-- ============================================================================
-- V21  QC gate and cost-approval record for repairs.
--
-- Two controls a repair shop needs and we had no way to enforce:
--
--   1. A device could go straight from IN_PROGRESS to COMPLETED and out the
--      door with no inspection step. QC is now a status of its own that the
--      job must pass through, and a failed QC sends it back to the bench.
--
--   2. Parts were deducted from stock as a side effect of completing the job,
--      using completed_at as the "already deducted" flag. Consumption is now
--      an explicit act with its own timestamp, and QC is blocked until it has
--      happened - so a job cannot be inspected for handback while the parts it
--      supposedly used are still sitting in sellable stock.
--
-- approved_amount records what the customer actually agreed to pay when the
-- job turned out to cost more than the estimate.
-- ============================================================================

ALTER TABLE repair_orders DROP CONSTRAINT repair_orders_status_check;
ALTER TABLE repair_orders ADD CONSTRAINT repair_orders_status_check CHECK (
    status IN (
        'RECEIVED', 'DIAGNOSING', 'AWAITING_APPROVAL', 'AWAITING_PARTS',
        'IN_PROGRESS', 'QC', 'COMPLETED', 'DELIVERED', 'CANCELLED', 'IRREPARABLE'
    )
);

ALTER TABLE repair_orders ADD COLUMN parts_consumed_at timestamptz;
ALTER TABLE repair_orders ADD COLUMN approved_at       timestamptz;
ALTER TABLE repair_orders ADD COLUMN approved_amount   numeric(14, 2);
ALTER TABLE repair_orders ADD COLUMN approved_by       uuid REFERENCES users (id) ON DELETE SET NULL;

-- Existing jobs deducted their parts at completion, so carry that forward
-- rather than leaving old jobs looking like their parts are still in stock.
UPDATE repair_orders
   SET parts_consumed_at = completed_at
 WHERE completed_at IS NOT NULL
   AND status <> 'CANCELLED';

ALTER TABLE repair_orders ADD CONSTRAINT repair_orders_approved_check CHECK (
    (approved_at IS NULL) = (approved_amount IS NULL)
);

COMMENT ON COLUMN repair_orders.parts_consumed_at IS
    'Set when parts were deducted from stock. QC is blocked until this is set.';
COMMENT ON COLUMN repair_orders.approved_amount IS
    'Customer-approved ceiling. QC is blocked when the job total exceeds it.';
