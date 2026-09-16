package com.possaas.identity.domain;

public enum UserStatus {
    /** Invited but has not yet set a password. */
    INVITED,
    ACTIVE,
    /** Deactivated by an administrator. */
    DISABLED,
    /** Temporarily locked after repeated failed sign-ins. */
    LOCKED
}
