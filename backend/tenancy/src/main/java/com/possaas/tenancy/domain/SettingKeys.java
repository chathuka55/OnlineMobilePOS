package com.possaas.tenancy.domain;

/**
 * Known setting keys, with the defaults applied when a tenant has no override.
 *
 * <p>Each constant replaces something the desktop build read from disk at runtime.
 */
public final class SettingKeys {

    // --- printing (was config.properties) ---
    public static final String RECEIPT_PRINTER_NAME = "print.receipt.printerName";
    public static final String RECEIPT_WIDTH_MM = "print.receipt.widthMm";
    public static final String RECEIPT_AUTO_PRINT = "print.receipt.autoPrint";
    public static final String INVOICE_PAGE_SIZE = "print.invoice.pageSize";
    public static final String INVOICE_TERMS = "print.invoice.terms";
    public static final String RECEIPT_SHOW_LOGO = "print.receipt.showLogo";
    /** "SIDE" (logo right of business details) or "CENTERED" (logo above, centered). */
    public static final String RECEIPT_LOGO_LAYOUT = "print.receipt.logoLayout";

    // --- till security (was passcode_config.properties) ---
    public static final String LOCK_TIMEOUT_SECONDS = "security.lockTimeoutSeconds";
    public static final String LOCK_REQUIRE_PIN = "security.lockRequirePin";

    // --- selling behaviour ---
    public static final String CASH_ROUNDING_NEAREST = "sales.cashRoundingNearest";
    public static final String ALLOW_NEGATIVE_STOCK = "sales.allowNegativeStock";
    public static final String DEFAULT_CUSTOMER_NAME = "sales.defaultCustomerName";
    public static final String MAX_LINE_DISCOUNT_PERCENT = "sales.maxLineDiscountPercent";
    public static final String REQUIRE_CUSTOMER_ON_CREDIT = "sales.requireCustomerOnCredit";
    public static final String HELD_CART_EXPIRY_HOURS = "sales.heldCartExpiryHours";

    // --- repairs ---
    public static final String REPAIR_DEFAULT_WARRANTY_DAYS = "repairs.defaultWarrantyDays";
    public static final String REPAIR_PROMISED_DAYS = "repairs.defaultPromisedDays";

    // --- inventory ---
    public static final String LOW_STOCK_ALERT_ENABLED = "inventory.lowStockAlertEnabled";
    public static final String BARCODE_LABEL_TEMPLATE = "inventory.barcodeLabelTemplate";

    private SettingKeys() {
    }

    /** Default applied when a tenant has not set the key. Null means "no default". */
    public static String defaultValue(String key) {
        return switch (key) {
            case RECEIPT_WIDTH_MM -> "80";
            case RECEIPT_AUTO_PRINT, RECEIPT_SHOW_LOGO, LOCK_REQUIRE_PIN,
                 LOW_STOCK_ALERT_ENABLED, REQUIRE_CUSTOMER_ON_CREDIT -> "true";
            case ALLOW_NEGATIVE_STOCK -> "false";
            case INVOICE_PAGE_SIZE -> "A4";
            case RECEIPT_LOGO_LAYOUT -> "SIDE";
            // Matches the desktop default of a five-minute idle lock.
            case LOCK_TIMEOUT_SECONDS -> "300";
            // Coins below one rupee are no longer in circulation in Sri Lanka.
            case CASH_ROUNDING_NEAREST -> "1.00";
            case DEFAULT_CUSTOMER_NAME -> "Walk-in Customer";
            case MAX_LINE_DISCOUNT_PERCENT -> "100";
            case HELD_CART_EXPIRY_HOURS -> "72";
            case REPAIR_DEFAULT_WARRANTY_DAYS -> "30";
            case REPAIR_PROMISED_DAYS -> "3";
            default -> null;
        };
    }
}
