package com.corporate.agent.domain;

import java.time.Instant;

public class PendingOtp {
    private final String otpId;
    private final String otpPin;
    private final String mobile;
    private final String purpose; // DEVICE_UPDATE | REGISTRATION | CASH_OUT
    private final Instant expiresAt;

    public PendingOtp(String otpId, String otpPin, String mobile, String purpose, Instant expiresAt) {
        this.otpId = otpId;
        this.otpPin = otpPin;
        this.mobile = mobile;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
    }

    public String getOtpId() { return otpId; }
    public String getOtpPin() { return otpPin; }
    public String getMobile() { return mobile; }
    public String getPurpose() { return purpose; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
}
