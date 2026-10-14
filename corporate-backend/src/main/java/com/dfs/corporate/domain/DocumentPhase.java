package com.dfs.corporate.domain;

/**
 * Separates admin-configurable document packs and KYC gates.
 * REGISTRATION = signup / Existing-NTN pack; ONBOARDING = post-login Annex-C pack.
 */
public enum DocumentPhase {
    REGISTRATION,
    ONBOARDING;

    public static DocumentPhase parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ONBOARDING;
        }
        return DocumentPhase.valueOf(raw.trim().toUpperCase());
    }
}
