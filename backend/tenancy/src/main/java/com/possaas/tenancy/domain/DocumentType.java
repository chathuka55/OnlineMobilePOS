package com.possaas.tenancy.domain;

/**
 * Numbered document families. The prefix is the default human-facing marker; a tenant can
 * override it per sequence.
 */
public enum DocumentType {

    BILL("INV", ResetPeriod.YEARLY),
    CREDIT_NOTE("CN", ResetPeriod.YEARLY),
    REFUND("RF", ResetPeriod.YEARLY),
    REPAIR_ORDER("REP", ResetPeriod.YEARLY),
    QUOTATION("QT", ResetPeriod.YEARLY),
    GRN("GRN", ResetPeriod.YEARLY),
    WHOLESALE_INVOICE("WS", ResetPeriod.YEARLY),
    SUPPLIER_RETURN("SR", ResetPeriod.YEARLY),
    PAYMENT_RECEIPT("RCT", ResetPeriod.YEARLY),
    STOCK_ADJUSTMENT("ADJ", ResetPeriod.YEARLY);

    private final String defaultPrefix;
    private final ResetPeriod defaultResetPeriod;

    DocumentType(String defaultPrefix, ResetPeriod defaultResetPeriod) {
        this.defaultPrefix = defaultPrefix;
        this.defaultResetPeriod = defaultResetPeriod;
    }

    public String defaultPrefix() {
        return defaultPrefix;
    }

    public ResetPeriod defaultResetPeriod() {
        return defaultResetPeriod;
    }

    public enum ResetPeriod {
        /** One ever-increasing counter. */
        NEVER,
        /** Restarts each calendar year, which is what most accountants expect. */
        YEARLY,
        MONTHLY
    }
}
