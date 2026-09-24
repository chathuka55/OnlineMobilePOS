-- ============================================================================
-- V24  Per-line share of the bill-level discount.
--
-- A bill discount reduces what the customer pays, so it has to reduce the VAT
-- they are charged. Until now line tax was computed before the bill discount
-- existed and the discount was simply subtracted from the total, which left the
-- printed VAT describing an amount nobody paid: on the reference example, VAT
-- of 44,389.83 on a bill that actually charged 43,627.12.
--
-- Storing each line's share makes the correction auditable - you can see which
-- line absorbed which part of the discount - and is what a partial return needs
-- in order to reverse exactly the right amount of discount and tax.
-- ============================================================================

ALTER TABLE bill_lines
    ADD COLUMN allocated_bill_discount numeric(14, 2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN bill_lines.allocated_bill_discount IS
    'This line''s pro-rata share of the bill-level discount, allocated by '
    'largest remainder so the shares sum exactly to the discount given.';
