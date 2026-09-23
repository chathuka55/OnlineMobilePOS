package com.possaas.catalog.domain;

/**
 * IMEI format rules.
 *
 * <p>An IMEI is 15 digits whose last digit is a Luhn check digit over the first
 * 14. Validating at intake is what stops a mistyped digit becoming a unit that
 * can never be found again by scanning the actual handset.
 */
public final class Imei {

    private Imei() {
    }

    /** Digits only, or null when blank - scanners sometimes append spaces or dashes. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : digits;
    }

    public static boolean isValid(String raw) {
        String imei = normalize(raw);
        if (imei == null || imei.length() != 15) {
            return false;
        }
        return luhnCheck(imei);
    }

    private static boolean luhnCheck(String digits) {
        int sum = 0;
        // Walk right to left; every second digit is doubled, and a double over 9
        // has its digits added (equivalently, 9 is subtracted).
        for (int i = digits.length() - 1, position = 0; i >= 0; i--, position++) {
            int digit = digits.charAt(i) - '0';
            if (position % 2 == 1) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return sum % 10 == 0;
    }
}
