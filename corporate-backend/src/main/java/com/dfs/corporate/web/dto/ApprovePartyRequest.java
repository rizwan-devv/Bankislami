package com.dfs.corporate.web.dto;

/**
 * Optional body for admin approve / retry provision.
 * When mobile KYC did not capture CNIC, back office must supply {@code cnicNumber} for DFS provision.
 */
public class ApprovePartyRequest {

    /** Owner CNIC — 13 digits (dashes optional). Required if party has no CNIC yet. */
    private String cnicNumber;

    public String getCnicNumber() { return cnicNumber; }
    public void setCnicNumber(String cnicNumber) { this.cnicNumber = cnicNumber; }
}
