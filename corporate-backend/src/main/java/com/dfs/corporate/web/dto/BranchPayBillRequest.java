package com.dfs.corporate.web.dto;

import jakarta.validation.constraints.NotBlank;

public class BranchPayBillRequest {
    @NotBlank
    private String companyPublicId;

    @NotBlank
    private String consumerNo;

    /** PAY | SKIP | FINISH — no wallet deduct for any. */
    @NotBlank
    private String action;

    private String notes;

    public String getCompanyPublicId() { return companyPublicId; }
    public void setCompanyPublicId(String companyPublicId) { this.companyPublicId = companyPublicId; }
    public String getConsumerNo() { return consumerNo; }
    public void setConsumerNo(String consumerNo) { this.consumerNo = consumerNo; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
