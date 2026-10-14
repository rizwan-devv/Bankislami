package com.dfs.corporate.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "entity_kyc_policy")
public class EntityKycPolicy {

    @Id
    @Column(name = "entity_type", length = 40)
    private String entityType;

    /** Legacy OR of phase flags — kept for older readers; prefer registration/onboarding columns. */
    @Column(name = "kyc_required", nullable = false)
    private boolean kycRequired = true;

    @Column(name = "registration_kyc_required", nullable = false)
    private boolean registrationKycRequired = true;

    @Column(name = "onboarding_kyc_required", nullable = false)
    private boolean onboardingKycRequired = true;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @Column(name = "display_label", length = 120)
    private String displayLabel;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public boolean isKycRequired() { return kycRequired; }
    public void setKycRequired(boolean kycRequired) { this.kycRequired = kycRequired; }
    public boolean isRegistrationKycRequired() { return registrationKycRequired; }
    public void setRegistrationKycRequired(boolean registrationKycRequired) {
        this.registrationKycRequired = registrationKycRequired;
    }
    public boolean isOnboardingKycRequired() { return onboardingKycRequired; }
    public void setOnboardingKycRequired(boolean onboardingKycRequired) {
        this.onboardingKycRequired = onboardingKycRequired;
    }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getDisplayLabel() { return displayLabel; }
    public void setDisplayLabel(String displayLabel) { this.displayLabel = displayLabel; }
}
