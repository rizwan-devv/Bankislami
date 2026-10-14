package com.corporate.agent.service;

import com.corporate.agent.domain.AgentAccount;
import com.corporate.agent.domain.PendingOtp;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory agent registry + OTP store (replace with DB later). */
@Component
public class AgentStore {

    private final Map<String, AgentAccount> byMobile = new ConcurrentHashMap<>();
    private final Map<String, PendingOtp> otps = new ConcurrentHashMap<>();

    @PostConstruct
    void seedDemo() {
        // Demo agent for Android testing
        // imei empty → first login binds device (no forced 123). Change IMEI later to test device-update.
        AgentAccount demo = new AgentAccount("03001234567", "1234", "Demo Corporate Agent", "4210112345671");
        demo.setDeviceVerified(true);
        byMobile.put(demo.getMobile(), demo);
    }

    public Optional<AgentAccount> find(String mobile) {
        if (mobile == null) return Optional.empty();
        return Optional.ofNullable(byMobile.get(normalize(mobile)));
    }

    public AgentAccount save(AgentAccount account) {
        byMobile.put(normalize(account.getMobile()), account);
        return account;
    }

    public PendingOtp createOtp(String mobile, String purpose) {
        String otpId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        // Agent Android OTP fields are 4 digits (register + device verify)
        String pin = String.format("%04d", (int) (Math.random() * 10_000));
        PendingOtp otp = new PendingOtp(otpId, pin, normalize(mobile), purpose,
                Instant.now().plus(10, ChronoUnit.MINUTES));
        otps.put(otpId, otp);
        return otp;
    }

    public Optional<PendingOtp> consumeOtp(String otpId, String otpPin, String purpose) {
        PendingOtp otp = otps.get(otpId);
        if (otp == null || otp.isExpired()) return Optional.empty();
        if (!otp.getPurpose().equals(purpose)) return Optional.empty();
        if (!otp.getOtpPin().equals(otpPin)) return Optional.empty();
        otps.remove(otpId);
        return Optional.of(otp);
    }

    public static String normalize(String mobile) {
        return mobile == null ? "" : mobile.trim().replaceAll("\\s+", "");
    }
}
