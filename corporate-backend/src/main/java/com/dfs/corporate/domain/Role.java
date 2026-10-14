package com.dfs.corporate.domain;

public enum Role {
    PLATFORM_ADMIN,
    PARTY_USER,
    /** Branch counter user — XPin verify + pay bill (no wallet deduct). */
    BRANCH_USER
}
