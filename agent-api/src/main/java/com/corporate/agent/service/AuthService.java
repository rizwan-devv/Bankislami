package com.corporate.agent.service;

import com.corporate.agent.config.JwtService;
import com.corporate.agent.dfs.DfsProxyClient;
import com.corporate.agent.domain.AgentAccount;
import com.corporate.agent.domain.PendingOtp;
import com.corporate.agent.web.dto.ApiEnvelope;
import com.corporate.agent.web.dto.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Login + device registration are managed HERE (not forwarded to DFS).
 * When portal proxy is on, missing local users can be auto-created from DFS wallet.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AgentStore store;
    private final JwtService jwt;
    private final DfsProxyClient dfs;
    private final DfsAgentIdentityService identity;
    private final boolean autoProvisionOnLogin;
    private final String defaultAccountLevel;

    public AuthService(
            AgentStore store,
            JwtService jwt,
            DfsProxyClient dfs,
            DfsAgentIdentityService identity,
            @Value("${agent.auto-provision-on-login:true}") boolean autoProvisionOnLogin,
            @Value("${agent.account-level-code:L4}") String defaultAccountLevel) {
        this.store = store;
        this.jwt = jwt;
        this.dfs = dfs;
        this.identity = identity;
        this.autoProvisionOnLogin = autoProvisionOnLogin;
        this.defaultAccountLevel = defaultAccountLevel != null && !defaultAccountLevel.isBlank()
                ? defaultAccountLevel.trim() : "L4";
    }

    public Map<String, Object> login(ApiEnvelope envelope) {
        JsonNode p = envelope.getPayload();
        String mobile = text(p, "username");
        String password = text(p, "password");
        if (mobile.isBlank() || password.isBlank()) {
            return ApiResponse.fail("400", "username and password required");
        }

        AgentAccount agent = store.find(mobile).orElse(null);
        if (agent == null && autoProvisionOnLogin) {
            agent = tryAutoProvisionFromDfs(mobile, password);
        }
        if (agent == null) {
            return ApiResponse.fail("401", "USER NOT FOUND");
        }
        if (!password.equals(agent.getPasswordMpin())) {
            return ApiResponse.fail("401", "INVALID CREDENTIALS");
        }

        // Always refresh CNIC into session (app FT/IBFT require it)
        identity.ensureCnic(agent);
        if (!DfsAgentIdentityService.hasValidCnic(agent.getNidNo())) {
            return ApiResponse.fail("409",
                    "CNIC missing for this wallet — complete DFS accountDetails / re-approve with CNIC, then login again");
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

    /**
     * If mobile has a DFS wallet (accountDetails or getbalance L4), create local agent.
     * Local MPIN becomes the password used on this login (until agent-api restart).
     */
    private AgentAccount tryAutoProvisionFromDfs(String mobile, String password) {
        if (!dfs.isEnabled() || !dfs.isPortalMode()) {
            return null;
        }
        boolean walletOk = false;

        ObjectNode detailsPayload = dfs.mapper().createObjectNode();
        detailsPayload.put("mobileNumber", mobile);
        Map<String, Object> details = dfs.postAppCorporate("/v1/corporate/accountDetails", detailsPayload);
        if (DfsAgentIdentityService.isSuccess(details)) {
            walletOk = true;
        }

        if (!walletOk) {
            ObjectNode balPayload = dfs.mapper().createObjectNode();
            balPayload.put("mobileNumber", mobile);
            balPayload.put("accountLevelCode", defaultAccountLevel);
            Map<String, Object> bal = dfs.postAgentCorporate("/agentapp/v1/corporate/getbalance", balPayload);
            walletOk = DfsAgentIdentityService.isSuccess(bal);
            details = null;
        }

        if (!walletOk) {
            log.info("Auto-provision skipped — no DFS wallet for {}", mobile);
            return null;
        }

        AgentAccount created = new AgentAccount(mobile, password, "Agent " + mobile, "");
        created.setAccountLevelCode(defaultAccountLevel);
        created.setDeviceVerified(false);
        if (details != null && DfsAgentIdentityService.isSuccess(details)) {
            identity.applyDetails(created, details);
        }
        identity.ensureCnic(created);
        store.save(created);
        log.info("Auto-provisioned agent {} from DFS (level={}, nidLen={})",
                mobile, created.getAccountLevelCode(),
                created.getNidNo() != null ? created.getNidNo().replaceAll("\\D", "").length() : 0);
        return created;
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
        identity.ensureCnic(agent);
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
            String nidDigits = DfsAgentIdentityService.digitsOnly(nid);
            existing = new AgentAccount(mobile, "1234", "Agent " + mobile,
                    nidDigits != null ? nidDigits : "");
            existing.setAccountLevelCode(defaultAccountLevel);
            existing.setImei(imei);
            existing.setDeviceModel(text(p, "deviceModel"));
            if (!DfsAgentIdentityService.hasValidCnic(existing.getNidNo())) {
                identity.ensureCnic(existing);
            }
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
        identity.ensureCnic(agent);
        store.save(agent);
        return ApiResponse.ok(Map.of(
                "mobileNumber", agent.getMobile(),
                "status", "REGISTERED",
                "defaultMpin", "1234",
                "nidNo", agent.getNidNo() != null ? agent.getNidNo() : ""
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
        String cnic = agent.getNidNo() != null ? agent.getNidNo().replaceAll("\\D", "") : "";
        if (cnic.length() > 13) cnic = cnic.substring(0, 13);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("customerId", agent.getCustomerId());
        data.put("accountTitle", agent.getAccountTitle());
        data.put("accountId", agent.getAccountId());
        data.put("accountNo", agent.getMobile());
        data.put("mobileNumber", agent.getMobile());
        data.put("accountLevelCode", agent.getAccountLevelCode());
        data.put("token", token);
        data.put("accessToken", token);
        data.put("iban", agent.getIban());
        data.put("qr", "CAQR|" + agent.getMobile() + "|");
        // App FT/IBFT session checks — send every common field name
        data.put("nidNo", cnic);
        data.put("cnic", cnic);
        data.put("cnicNumber", cnic);
        data.put("nationalId", cnic);
        return data;
    }

    private static Map<String, String> opt(String id, String name) {
        return Map.of("id", id, "code", id, "name", name, "description", name);
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }
}
