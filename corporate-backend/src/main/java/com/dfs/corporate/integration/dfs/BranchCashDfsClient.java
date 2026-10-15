package com.dfs.corporate.integration.dfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * DFS agent cash-in / cash-out envelopes (AGNT).
 * Paths match the agent app: cashInInquiry, internalcashin, genrateOtp, internalcashout.
 */
@Component
public class BranchCashDfsClient {

    private static final Logger log = LoggerFactory.getLogger(BranchCashDfsClient.class);

    private final String agentBase;
    private final String txnBase;
    private final String thirdBase;
    private final String portalKey;
    private final String bearer;
    private final String defaultImei;
    private final String transPurposeId;
    private final ObjectMapper mapper;
    private final RestClient http;

    public BranchCashDfsClient(
            @Value("${dfs.cash.agent-base-url:http://46.225.160.93:18001}") String agentBase,
            @Value("${dfs.cash.txn-base-url:http://46.225.160.93:18009}") String txnBase,
            @Value("${dfs.cash.third-party-base-url:http://46.225.160.93:18008}") String thirdBase,
            @Value("${dfs.portal-api.portal-key:}") String portalKey,
            @Value("${dfs.cash.bearer:}") String bearer,
            @Value("${dfs.cash.imei:BRANCH-DESK}") String defaultImei,
            @Value("${dfs.cash.trans-purpose-id:1}") String transPurposeId,
            ObjectMapper mapper) {
        this.agentBase = trim(agentBase);
        this.txnBase = trim(txnBase);
        this.thirdBase = trim(thirdBase);
        this.portalKey = portalKey != null ? portalKey.trim() : "";
        this.bearer = bearer != null ? bearer.trim() : "";
        this.defaultImei = defaultImei != null && !defaultImei.isBlank() ? defaultImei.trim() : "BRANCH-DESK";
        this.transPurposeId = transPurposeId != null && !transPurposeId.isBlank() ? transPurposeId.trim() : "1";
        this.mapper = mapper;
        this.http = RestClient.builder().build();
    }

    public String defaultImei() {
        return defaultImei;
    }

    public String transPurposeId() {
        return transPurposeId;
    }

    public JsonNode cashInInquiry(String imei, String customerAccount) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("accountNo", customerAccount);
        payload.put("depositorNid", "");
        return post(agentBase + "/agentapp/v1/customer/cashInInquiry", envelope(imei, payload, true, false));
    }

    public JsonNode internalCashIn(String imei, ObjectNode payload) {
        return post(txnBase + "/transactions/v1/internalcashin", envelope(imei, payload, false, false));
    }

    public JsonNode generateOtp(String imei, String customerMobile) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("otpType", "FT");
        payload.put("mobileNumber", customerMobile);
        payload.put("email", "");
        payload.put("userType", "C");
        payload.put("smsTemplateCode", "CLTF");
        payload.put("identifier", "S");
        return post(thirdBase + "/thirdparties/genrateOtp", envelope(imei, payload, false, false));
    }

    public JsonNode internalCashOut(String imei, ObjectNode fund, ObjectNode otp) {
        ObjectNode payload = mapper.createObjectNode();
        payload.set("fundTransferRequest", fund);
        payload.set("verifyOtpRequest", otp);
        return post(txnBase + "/transactions/v1/internalcashout", envelope(imei, payload, true, true));
    }

    private ObjectNode envelope(String imei, ObjectNode payload, boolean segment, boolean language) {
        ObjectNode root = mapper.createObjectNode();
        root.put("channel", "AGNT");
        if (segment) {
            root.put("segment", "AGENT");
        }
        if (language) {
            root.put("language", "en");
        }
        root.put("imieNo", imei == null || imei.isBlank() ? defaultImei : imei);
        root.set("payload", payload);
        return root;
    }

    private JsonNode post(String url, ObjectNode body) {
        try {
            log.info("DFS cash POST {}", url);
            String raw = http.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (!portalKey.isBlank()) {
                            h.set("X-Portal-Key", portalKey);
                        }
                        if (!bearer.isBlank()) {
                            h.setBearerAuth(bearer.startsWith("Bearer ") ? bearer.substring(7).trim() : bearer);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return mapper.readTree(raw != null && !raw.isBlank() ? raw : "{}");
        } catch (RestClientResponseException ex) {
            String text = ex.getResponseBodyAsString();
            log.warn("DFS cash HTTP {}: {}", ex.getStatusCode().value(), truncate(text));
            try {
                if (text != null && text.trim().startsWith("{")) {
                    return mapper.readTree(text);
                }
            } catch (Exception ignored) {
                /* fall through */
            }
            ObjectNode err = mapper.createObjectNode();
            err.put("responsecode", String.valueOf(ex.getStatusCode().value()));
            err.put("messages", text != null && !text.isBlank() ? truncate(text) : "DFS cash call failed");
            return err;
        } catch (Exception ex) {
            log.warn("DFS cash failed: {}", ex.getMessage());
            ObjectNode err = mapper.createObjectNode();
            err.put("responsecode", "502");
            err.put("messages", "DFS cash call failed: " + ex.getMessage());
            return err;
        }
    }

    private static String trim(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        return t;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
