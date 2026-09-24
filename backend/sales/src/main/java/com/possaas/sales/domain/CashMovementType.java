package com.possaas.sales.domain;

/** Cash crossing the drawer for a reason other than a sale or refund. */
public enum CashMovementType {
    /** Money added to the drawer mid-shift. */
    PAY_IN,
    /** Money taken out for an expense. */
    PAYOUT,
    /** Money moved to the safe, out of the cashier's custody. */
    DROP
}
