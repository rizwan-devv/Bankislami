package com.dfs.corporate.web.dto;

import com.dfs.corporate.domain.PartyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class SignupRequest {
    @NotNull
    private PartyType partyType;
    @NotBlank
    private String fullName;
    private String businessName;
    @NotBlank @Email
    private String email;
    @NotBlank
    private String phone;
    /**
     * Franchise / child wallet signup — parent is taken from invite (preferred).
     * Manual parentPartyPublicId is deprecated.
     */
    private String franchiseInviteToken;
    /** @deprecated Use franchiseInviteToken invite link instead */
    private String parentPartyPublicId;

    /** Existing-customer path: NTN from mock directory (optional). */
    private String ntnNumber;
    private String entityType;
    private String registeredAddress;
    private String natureOfBusiness;
    private String taxCountry;
    /** Comma-separated or list — stored on party notes / selected accounts field if present. */
    private java.util.List<String> selectedAccountNos;

    public PartyType getPartyType() { return partyType; }
    public void setPartyType(PartyType partyType) { this.partyType = partyType; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getFranchiseInviteToken() { return franchiseInviteToken; }
    public void setFranchiseInviteToken(String franchiseInviteToken) { this.franchiseInviteToken = franchiseInviteToken; }
    public String getParentPartyPublicId() { return parentPartyPublicId; }
    public void setParentPartyPublicId(String parentPartyPublicId) { this.parentPartyPublicId = parentPartyPublicId; }
    public String getNtnNumber() { return ntnNumber; }
    public void setNtnNumber(String ntnNumber) { this.ntnNumber = ntnNumber; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getRegisteredAddress() { return registeredAddress; }
    public void setRegisteredAddress(String registeredAddress) { this.registeredAddress = registeredAddress; }
    public String getNatureOfBusiness() { return natureOfBusiness; }
    public void setNatureOfBusiness(String natureOfBusiness) { this.natureOfBusiness = natureOfBusiness; }
    public String getTaxCountry() { return taxCountry; }
    public void setTaxCountry(String taxCountry) { this.taxCountry = taxCountry; }
    public java.util.List<String> getSelectedAccountNos() { return selectedAccountNos; }
    public void setSelectedAccountNos(java.util.List<String> selectedAccountNos) { this.selectedAccountNos = selectedAccountNos; }
}
