-- ============================================================================
-- V17  Warranty-validity tracking on supplier returns.
--
-- Captured once at return time (not recomputed on read): a serial's warranty
-- window is fixed, but "was it within warranty when this return was made" is
-- a fact about that moment, not about whenever the record is later viewed.
-- ============================================================================
ALTER TABLE supplier_return_lines ADD COLUMN IF NOT EXISTS within_warranty boolean;
