package com.possaas.identity.domain;

/**
 * Codes of the roles seeded by migration V13. Referenced from code that has to assign a
 * role by name, such as tenant signup and staff invitation.
 */
public final class SystemRole {

    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String OWNER = "OWNER";
    public static final String MANAGER = "MANAGER";
    public static final String CASHIER = "CASHIER";
    public static final String TECHNICIAN = "TECHNICIAN";
    public static final String ACCOUNTANT = "ACCOUNTANT";

    /** Roles a tenant administrator may assign. Excludes platform administration. */
    public static final java.util.List<String> ASSIGNABLE =
            java.util.List.of(OWNER, MANAGER, CASHIER, TECHNICIAN, ACCOUNTANT);

    private SystemRole() {
    }
}
