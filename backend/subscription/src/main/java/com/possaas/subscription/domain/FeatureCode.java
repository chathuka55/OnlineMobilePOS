package com.possaas.subscription.domain;

/**
 * Feature flags unlocked by a plan. Values must stay in sync with the
 * {@code plan_features_code_check} constraint in V3.
 */
public enum FeatureCode {
    RETAIL_BILLING,
    INVENTORY,
    SERIAL_TRACKING,
    GRN,
    REPAIRS,
    WHOLESALE,
    QUOTATIONS,
    CREDIT_NOTES,
    MULTI_OUTLET,
    ADVANCED_REPORTS,
    JASPER_EXPORT,
    AUDIT_TRAIL,
    API_ACCESS,
    THERMAL_PRINTING,
    BARCODE_LABELS
}
