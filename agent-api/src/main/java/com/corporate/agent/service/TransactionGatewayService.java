package com.corporate.agent.service;

import com.corporate.agent.config.JwtService;
import com.corporate.agent.dfs.DfsProxyClient;
import com.corporate.agent.domain.AgentAccount;
import com.corporate.agent.web.dto.ApiEnvelope;
import com.corporate.agent.web.dto.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactions: App → this service → DFS (when proxy enabled) → App.
 * Portal mode: X-Portal-Key + /v1/corporate/* (same as Corporate portal).
 * When proxy disabled, returns deterministic stubs.
 */
@Service
public class TransactionGatewayService {

    private final DfsProxyClient dfs;
    private final AgentStore store;
    private final JwtService jwt;

    public TransactionGatewayService(DfsProxyClient dfs, AgentStore store, JwtService jwt) {
        this.dfs = dfs;
        this.store = store;
        this.jwt = jwt;
    }

    public Map<String, Object> getAllBanks(String authorization) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                // Portal: POST /v1/corporate/ibft/bankList (not GET getAllBank)
                return dfs.postCorporateEmpty("/v1/corporate/ibft/bankList");
            }
            return dfs.getTxn("/transactions/v1/getAllBank", authorization);
        }
        List<Map<String, String>> banks = List.of(
                bank("627271", "Bank Islami"),
                bank("585951", "Demo Bank A"),
                bank("123456", "Demo Bank B")
        );
        return ApiResponse.ok(Map.of("bankList", banks));
    }

    public Map<String, Object> getBillers(String authorization) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return dfs.getCorporate("/v1/corporate/getbiller");
            }
            return dfs.getTxn("/transactions/v1/getbiller", authorization);
        }
        List<Map<String, String>> billers = List.of(
                Map.of("code", "KE", "name", "K-Electric", "id", "1"),
                Map.of("code", "SSGC", "name", "SSGC", "id", "2")
        );
        return ApiResponse.ok(billers);
    }

    public Map<String, Object> ibftTitleFetch(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return dfs.postCorporate("/v1/corporate/ibft/titleFetch",
                        enrichIbftEnvelope(authorization, body, false));
            }
            return dfs.postTxn("/transactions/v1/ibft/titleFetch", body, authorization);
        }
        JsonNode p = body.getPayload();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("beneficiaryAccountNo", text(p, "beneficiaryAccountNo"));
        data.put("beneficiaryAccountTitle", "STUB BENEFICIARY");
        data.put("beneficiaryBankName", "Stub Bank");
        data.put("beneficiaryBankImd", text(p, "beneficiaryBankImd"));
        data.put("beneficiaryId", "STUB-BEN");
        data.put("amount", text(p, "amount"));
        data.put("stan", stan());
        data.put("rrn", rrn());
        data.put("responseCode", "000");
        data.put("responseDescription", "SUCCESS");
        return ApiResponse.ok(data);
    }

    public Map<String, Object> ibftAdvice(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return dfs.postCorporate("/v1/corporate/ibft/advice",
                        enrichIbftEnvelope(authorization, body, true));
            }
            return dfs.postTxn("/transactions/v1/ibft/advice", body, authorization);
        }
        return stubTxnResult("IBFT");
    }

    public Map<String, Object> billInquiry(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return dfs.postCorporate("/v1/corporate/billInquiry", body);
            }
            return dfs.postTxn("/transactions/v1/billInquiry", body, authorization);
        }
        JsonNode p = body.getPayload();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("utilityCompanyCode", text(p, "utilityCompanyCode"));
        data.put("utilityCompanyName", "Stub Utility");
        data.put("consumerNo", text(p, "consumerNo"));
        data.put("billAmount", "500.00");
        data.put("billDueDate", "2026-10-31");
        data.put("billPaid", "N");
        data.put("stan", stan());
        data.put("rrn", rrn());
        data.put("responseCode", "000");
        data.put("responseDescription", "SUCCESS");
        data.put("consumerName", "Stub Consumer");
        data.put("amount", "500.00");
        return ApiResponse.ok(data);
    }

    public Map<String, Object> billPayment(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return dfs.postCorporate("/v1/corporate/billPayment", body);
            }
            return dfs.postTxn("/transactions/v1/billPayment", body, authorization);
        }
        return stubTxnResult("BILL");
    }

    public Map<String, Object> initiateLocalFt(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return dfs.postCorporate("/v1/corporate/initiateLocalFT", body);
            }
            return dfs.postTxn("/transactions/v1/initiateLocalFT", body, authorization);
        }
        JsonNode p = body.getPayload();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authIdResponse", UUID.randomUUID().toString().substring(0, 12));
        data.put("accountTitle", "STUB W2W TITLE");
        data.put("accountNo", text(p, "accountNo"));
        data.put("responseDescr", "SUCCESS");
        data.put("responseCode", "000");
        data.put("fee", "0");
        List<Map<String, String>> purposes = new ArrayList<>();
        purposes.add(Map.of("id", "1", "name", "Transfer", "code", "TRF"));
        data.put("transPurposes", purposes);
        return ApiResponse.ok(data);
    }

    public Map<String, Object> fundsTransferLocal(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return dfs.postCorporate("/v1/corporate/fundsTransferLocal", body);
            }
            return dfs.postTxn("/transactions/v1/fundsTransferLocal", body, authorization);
        }
        return stubTxnResult("W2W");
    }

    public Map<String, Object> cashInInquiry(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            // No corporate portal equivalent — keep agent path / stub
            if (dfs.isPortalMode()) {
                return ApiResponse.fail("501", "Cash-in inquiry not available in portal proxy mode");
            }
            return dfs.postAgent("/agentapp/v1/customer/cashInInquiry", body, authorization);
        }
        JsonNode p = body.getPayload();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountNo", text(p, "accountNo"));
        data.put("name", "Stub Customer");
        data.put("dob", "1990-01-01");
        return ApiResponse.ok(data);
    }

    public Map<String, Object> internalCashIn(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return ApiResponse.fail("501", "Cash-in not available in portal proxy mode");
            }
            return dfs.postTxn("/transactions/v1/internalcashin", body, authorization);
        }
        return stubCashResult();
    }

    public Map<String, Object> generateCashOutOtp(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return ApiResponse.fail("501", "Cash-out OTP not available in portal proxy mode");
            }
            return dfs.postThirdParty("/thirdparties/genrateOtp", body, authorization);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mobileNumber", text(body.getPayload(), "mobileNumber"));
        data.put("otpId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        data.put("otpCode", "123456");
        data.put("expiryDateTime", java.time.Instant.now().plusSeconds(300).toString());
        data.put("status", "SENT");
        return ApiResponse.ok(data);
    }

    public Map<String, Object> internalCashOut(String authorization, ApiEnvelope body) {
        if (dfs.isEnabled()) {
            if (dfs.isPortalMode()) {
                return ApiResponse.fail("501", "Cash-out not available in portal proxy mode");
            }
            return dfs.postTxn("/transactions/v1/internalcashout", body, authorization);
        }
        return stubCashResult();
    }

    /**
     * Mirror Corporate portal IBFT: inject fromAccountNo / fromAccountNid from logged-in agent,
     * normalize beneficiary aliases, default purpose on advice.
     */
    private ApiEnvelope enrichIbftEnvelope(String authorization, ApiEnvelope body, boolean forAdvice) {
        AgentAccount agent = requireAgent(authorization, body);
        ObjectNode payload = dfs.copyPayload(body != null ? body.getPayload() : null);

        putIfBlank(payload, "fromAccountNo", agent.getMobile());
        String nid = digitsOnly(agent.getNidNo());
        if (nid != null && nid.length() >= 13) {
            putIfBlank(payload, "fromAccountNid", nid.length() > 13 ? nid.substring(0, 13) : nid);
        }

        copyFirstAlias(payload, "beneficiaryAccountNo",
                "accountNo", "toAccountNo", "iban", "beneficiaryIban");
        copyFirstAlias(payload, "beneficiaryBankImd",
                "bankImd", "imd", "beneficiaryBankCode", "bankCode");

        if (forAdvice) {
            String purpose = firstNonBlank(
                    text(payload, "purposeOfPayment"),
                    text(payload, "purpose"),
                    text(payload, "purposeOfTxn"),
                    "Transfer");
            payload.put("purposeOfPayment", purpose);
        }

        ApiEnvelope out = new ApiEnvelope();
        if (body != null) {
            out.setChannel(body.getChannel());
            out.setSegment(body.getSegment());
            out.setImieNo(body.getImieNo());
            out.setLatitude(body.getLatitude());
            out.setLongitude(body.getLongitude());
            out.setLanguage(body.getLanguage());
        }
        out.setPayload(payload);
        return out;
    }

    private AgentAccount requireAgent(String authorization, ApiEnvelope envelope) {
        String mobile = null;
        try {
            mobile = jwt.mobileFromAuthHeader(authorization);
        } catch (Exception ignored) {
            // fall through
        }
        if (mobile == null || mobile.isBlank()) {
            mobile = text(envelope != null ? envelope.getPayload() : null, "mobileNumber");
        }
        if (mobile == null || mobile.isBlank()) {
            mobile = text(envelope != null ? envelope.getPayload() : null, "fromAccountNo");
        }
        return store.find(mobile)
                .orElseThrow(() -> new WalletService.UnauthorizedException(
                        "Invalid or missing token / agent for IBFT"));
    }

    private static void putIfBlank(ObjectNode payload, String field, String value) {
        if (value == null || value.isBlank()) return;
        String existing = text(payload, field);
        if (existing.isBlank()) {
            payload.put(field, value);
        }
    }

    private static void copyFirstAlias(ObjectNode payload, String target, String... aliases) {
        if (!text(payload, target).isBlank()) return;
        for (String a : aliases) {
            String v = text(payload, a);
            if (!v.isBlank()) {
                payload.put(target, v);
                return;
            }
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static String digitsOnly(String s) {
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    private Map<String, Object> stubTxnResult(String type) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("responseCode", "000");
        data.put("responseDescr", "SUCCESS");
        data.put("authIdResponse", "STUB-" + type + "-" + stan());
        data.put("stan", stan());
        data.put("rrn", rrn());
        data.put("transDate", java.time.LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    private Map<String, Object> stubCashResult() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("responseCode", "000");
        data.put("responseDescr", "SUCCESS");
        data.put("authIdResponse", "STUB-CASH-" + stan());
        data.put("transDate", java.time.LocalDateTime.now().toString());
        return ApiResponse.ok(data);
    }

    private static Map<String, String> bank(String imd, String name) {
        return Map.of("imd", imd, "name", name);
    }

    private static String stan() {
        return String.valueOf(System.currentTimeMillis() % 1_000_000);
    }

    private static String rrn() {
        return String.valueOf(System.currentTimeMillis());
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }
}
