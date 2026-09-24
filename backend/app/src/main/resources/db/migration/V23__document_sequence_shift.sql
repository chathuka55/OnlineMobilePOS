-- ============================================================================
-- V23  Allow SHIFT document numbers.
--
-- document_sequences constrains document_type to a fixed list, so adding a new
-- DocumentType in Java isn't enough: allocating the first number for it fails
-- on the CHECK constraint. Adding SHIFT here is what makes V22's shifts usable.
-- ============================================================================

ALTER TABLE document_sequences DROP CONSTRAINT document_sequences_type_check;
ALTER TABLE document_sequences ADD CONSTRAINT document_sequences_type_check CHECK (
    document_type IN (
        'BILL', 'CREDIT_NOTE', 'REFUND', 'REPAIR_ORDER', 'QUOTATION',
        'GRN', 'WHOLESALE_INVOICE', 'SUPPLIER_RETURN', 'PAYMENT_RECEIPT',
        'STOCK_ADJUSTMENT', 'SHIFT'
    )
);
