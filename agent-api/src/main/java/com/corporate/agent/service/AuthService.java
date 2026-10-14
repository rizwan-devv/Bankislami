package com.corporate.agent.service;

import com.corporate.agent.config.JwtService;
import com.corporate.agent.domain.AgentAccount;
import com.corporate.agent.domain.PendingOtp;
import com.corporate.agent.web.dto.ApiEnvelope;
import com.corporate.agent.web.dto.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Login + device registration are managed HERE (not forwarded to DFS).
 */
@Service
public class AuthService {

    private final AgentStore store;
    private final JwtService jwt;

    public AuthService(AgentStore store, JwtService jwt) {
        this.store = store;
        this.jwt = jwt;
    }

    public Map<String, Object> login(ApiEnvelope envelope) {
        JsonNode p = envelope.getPayload();
        String mobile = text(p, "username");
        String password = text(p, "password");
        if (mobile.isBlank() || password.isBlank()) {
            return ApiResponse.fail("400", "username and password required");
        }

        AgentAccount agent = store.find(mobile).orElse(null);
        if (agent == null) {
            return ApiResponse.fail("401", "USER NOT FOUND");
        }
        if (!password.equals(agent.getPasswordMpin())) {
            return ApiResponse.fail("401", "INVALID CREDENTIALS");
        }

        String imei = envelope.getImieNo() != null ? envelope.getImieNo() : "";
        // New/unknown device → code 123 (Agent Android device-update flow)
        if (agent.getImei() != null && !agent.getImei().isBlank()
                && !imei.isBlank() && !imei.equals(agent.getImei())) {
            return ApiResponse.of("123", "DEVICE UPDATE REQUIRED", Map.of(
                    "mobileNumber", agent.getMobile(),
                    "username", agent.getMobile()
            ));
        }

        if (agent.getImei() == null || agent.getImei().isBlank()) {
            agent.setImei(imei);
            store.save(agent);
        }

        String token = jwt.issue(agent.getMobile(), agent.getCustomerId());
        return ApiResponse.ok(loginProfile(agent, token));
    }

    public Map<String, Object> updateDevice(ApiEnvelope envelope) {
        JsonNode p = envelope.getPayload();
        String mobile = text(p, "username");
        if (store.find(mobile).isEmpty()) {
            return ApiResponse.fail("401", "USER NOT FOUND");
        }
        PendingOtp otp = store.createOtp(mobile, "DEVICE_UPDATE");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("otpId", otp.getOtpId());
        data.put("otpCode", otp.getOtpPin()); // visible in stub/dev
        data.put("mobileNumber", otp.getMobile());
        return ApiResponse.ok(data);
    }

    public Map<String, Object> verifyUpdateDevice(ApiEnvelope envelope) {
        JsonNode p = envelope.getPayload();
        JsonNode verify = p != null ? p.path("verifyOtpRequest") : null;
        String otpId = text(verify, "otpId");
        String otpPin = text(verify, "otpPin");
        String mobile = text(verify, "mobileNumber");
        if (mobile.isBlank()) mobile = text(p, "mobileNumber");

        var consumed = store.consumeOtp(otpId, otpPin, "DEVICE_UPDATE");
        if (consumed.isEmpty()) {
            return ApiResponse.fail("409", "INVALID OR EXPIRED OTP");
        }

        AgentAccount agent = store.find(mobile).orElse(null);
        if (agent == null) {
            return ApiResponse.fail("401", "USER NOT FOUND");
        }
        String imei = text(p, "imeiNo");
        if (imei.isBlank()) imei = envelope.getImieNo();
        agent.setImei(imei);
        agent.setDeviceModel(text(p, "deviceModel"));
        agent.setDeviceVerified(true);
        store.save(agent);

        String token = jwt.issue(agent.getMobile(), agent.getCustomerId());
        return ApiResponse.ok(loginProfile(agent, token));
    }

    public Map<String, Object> agentDeviceRegistration(ApiEnvelope envelope) {
        JsonNode p = envelope.getPayload();
        String mobile = text(p, "mobileNo");
        if (mobile.isBlank()) mobile = text(p, "mobileNumber");
        String nid = text(p, "nidNo");
        String imei = text(p, "imeiNo");
        if (imei.isBlank()) imei = envelope.getImieNo();

        if (mobile.isBlank()) {
            return ApiResponse.fail("400", "mobileNo required");
        }

        AgentAccount existing = store.find(mobile).orElse(null);
        if (existing == null) {
            // Default MPIN 1234 until change-mpin; title from mobile for stub
            existing = new AgentAccount(mobile, "1234", "Agent " + mobile, nid);
            existing.setImei(imei);
            existing.setDeviceModel(text(p, "deviceModel"));
            store.save(existing);
        }

        PendingOtp otp = store.createOtp(mobile, "REGISTRATION");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("otpId", otp.getOtpId());
        data.put("otpCode", otp.getOtpPin());
        data.put("mobileNumber", otp.getMobile());
        data.put("birthPlace", List.of(opt("1", "Karachi")));
        data.put("occupation", List.of(opt("1", "Business")));
        data.put("city", List.of(opt("1", "Karachi"), opt("2", "Lahore")));
        data.put("accountPurpose", List.of(opt("1", "Business")));
        data.put("businessType", List.of(opt("1", "Retail")));
        data.put("expectedMonthlyVolume", List.of(opt("1", "0-50000")));
        data.put("recoveryQuestion", List.of(opt("1", "Favourite city?")));
        return ApiResponse.ok(data);
    }

    public Map<String, Object> verifyAgentDeviceRegistration(ApiEnvelope envelope) {
        JsonNode p = envelope.getPayload();
        String otpId = text(p, "otpId");
        String otpPin = text(p, "otpPin");
        String mobile = text(p, "mobileNumber");

        var consumed = store.consumeOtp(otpId, otpPin, "REGISTRATION");
        if (consumed.isEmpty()) {
            return ApiResponse.fail("409", "INVALID OR EXPIRED OTP");
        }
        AgentAccount agent = store.find(mobile).orElse(null);
        if (agent == null) {
            return ApiResponse.fail("401", "USER NOT FOUND");
        }
        agent.setDeviceVerified(true);
        store.save(agent);
        return ApiResponse.ok(Map.of(
                "mobileNumber", agent.getMobile(),
                "status", "REGISTERED",
                "defaultMpin", "1234"
        ));
    }

    public Map<String, Object> getAccountUpgradeLovs() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("levelLimit", Map.of(
                "accountLevelName", "Level 2",
                "accountLevelCode", "L2",
                "accountLevelLimit", "500000"
        ));
        data.put("occupations", List.of(opt("1", "Business"), opt("2", "Salaried")));
        data.put("purposes", List.of(opt("1", "Business")));
        data.put("incomeSources", List.of(opt("1", "Salary"), opt("2", "Business")));
        data.put("businessType", List.of(opt("1", "Retail")));
        data.put("recoveryQuestions", List.of(opt("1", "Favourite city?")));
        return ApiResponse.ok(data);
    }

    private Map<String, Object> loginProfile(AgentAccount agent, String token) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", agent.getCustomerId());
        data.put("accountTitle", agent.getAccountTitle());
        data.put("accountId", agent.getAccountId());
        data.put("accountNo", agent.getMobile());
        data.put("currentBalance", agent.getBalance());
        data.put("levelDescr", "Level 2");
        data.put("token", token);
        data.put("accountType", "10");
        data.put("accountStatusDescr", "Active");
        data.put("accountStatusId", 1);
        data.put("biometricallyVerified", "N");
        // Same payload shape as generateQr — app shows this after login
        String iban = agent.getIban() != null ? agent.getIban() : "";
        data.put("qr", iban.isBlank()
                ? "CAQR|" + agent.getMobile() + "|0"
                : iban);
        data.put("iban", agent.getIban());
        data.put("nidNo", agent.getNidNo());
        data.put("appUserId", agent.getAppUserId());
        data.put("dfsId", "CA-" + agent.getCustomerId());
        data.put("accountLevelCode", agent.getAccountLevelCode());
        data.put("fatherName", "");
        data.put("username", agent.getMobile());
        data.put("selfie", "");
        data.put("autoLoginToken", "");
        data.put("email", "");
        data.put("customerRiskProfile", "");
        data.put("expiry", "");
        return data;
    }

    private static Map<String, String> opt(String id, String name) {
        return Map.of("id", id, "name", name, "code", id, "description", name);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }
}
