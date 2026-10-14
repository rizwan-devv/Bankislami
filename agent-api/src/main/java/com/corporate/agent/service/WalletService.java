package com.corporate.agent.service;

import com.corporate.agent.config.JwtService;
import com.corporate.agent.dfs.DfsProxyClient;
import com.corporate.agent.domain.AgentAccount;
import com.corporate.agent.web.dto.ApiEnvelope;
import com.corporate.agent.web.dto.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Wallet APIs: DFS portal when proxy enabled; otherwise local stub. */
@Service
public class WalletService {

    private final AgentStore store;
    private final JwtService jwt;
    private final DfsProxyClient dfs;
    private final ObjectMapper mapper;

    public WalletService(AgentStore store, JwtService jwt, DfsProxyClient dfs, ObjectMapper mapper) {
        this.store = store;
        this.jwt = jwt;
        this.dfs = dfs;
        this.mapper = mapper;
    }

    public Map<String, Object> getBalance(String authorization, ApiEnvelope envelope) {
        AgentAccount agent = requireAgent(authorization, envelope);
        if (dfs.isEnabled() && dfs.isPortalMode()) {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("mobileNumber", agent.getMobile());
            payload.put("accountLevelCode",
                    agent.getAccountLevelCode() != null ? agent.getAccountLevelCode() : "L2");
            Map<String, Object> resp = dfs.postAgentCorporate("/agentapp/v1/corporate/getbalance", payload);
            syncLocalBalance(agent, resp);
            return resp;
        }
        return ApiResponse.ok(Map.of(
                "balance", agent.getBalance(),
                "currentBalance", agent.getBalance()
        ));
    }

    public Map<String, Object> viewLimits(String authorization, ApiEnvelope envelope) {
        AgentAccount agent = requireAgent(authorization, envelope);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("accountId", agent.getAccountId());
        data.put("dailyDrTotal", 0);
        data.put("monthlyDrTotal", 0);
        data.put("yearlyDrTotal", 0);
        data.put("dailyCrTotal", 0);
        data.put("monthlyCrTotal", 0);
        data.put("yearlyCrTotal", 0);
        data.put("dailyDrRemaining", 200_000);
        data.put("monthlyDrRemaining", 1_000_000);
        data.put("yearlyDrRemaining", 5_000_000);
        data.put("dailyCrRemaining", 200_000);
        data.put("monthlyCrRemaining", 1_000_000);
        data.put("yearlyCrRemaining", 5_000_000);
        return ApiResponse.ok(data);
    }

    public Map<String, Object> changeMpin(String authorization, ApiEnvelope envelope) {
        AgentAccount agent = requireAgent(authorization, envelope);
        JsonNode p = envelope.getPayload();
        String current = text(p, "currentMpin");
        String neu = text(p, "newMpin");
        String confirm = text(p, "confirmMpin");
        if (!current.equals(agent.getPasswordMpin())) {
            return ApiResponse.fail("401", "CURRENT MPIN INVALID");
        }
        if (neu.isBlank() || !neu.equals(confirm)) {
            return ApiResponse.fail("400", "NEW MPIN MISMATCH");
        }
        agent.setPasswordMpin(neu);
        store.save(agent);
        return ApiResponse.ok(Map.of("messages", "MPIN CHANGED"));
    }

    public Map<String, Object> miniStatement(String authorization, ApiEnvelope envelope) {
        AgentAccount agent = requireAgent(authorization, envelope);
        if (dfs.isEnabled() && dfs.isPortalMode()) {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("mobileNumber", agent.getMobile());
            payload.put("accountLevelCode",
                    agent.getAccountLevelCode() != null ? agent.getAccountLevelCode() : "L2");
            JsonNode req = envelope != null ? envelope.getPayload() : null;
            String from = text(req, "fromDate");
            String to = text(req, "toDate");
            if (!from.isBlank()) payload.put("fromDate", from);
            if (!to.isBlank()) payload.put("toDate", to);
            return dfs.postAgentCorporate("/agentapp/v1/corporate/miniStatment", payload);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("rowkey", 1);
        sample.put("transDocsDescr", "Opening / seed balance");
        sample.put("transDocsCode", "CR");
        sample.put("transDate", LocalDate.now().minusDays(1).toString());
        sample.put("valueDate", LocalDate.now().minusDays(1).toString());
        sample.put("transRefnum", "CA-SEED-" + agent.getAccountId());
        sample.put("txnAmt", agent.getBalance());
        sample.put("feeAmt", 0);
        sample.put("amountType", "C");
        sample.put("fromAccountNo", "");
        sample.put("toAccountNo", agent.getMobile());
        sample.put("fromAccountTitle", "");
        sample.put("toAccountTitle", agent.getAccountTitle());
        sample.put("comments", "Local wallet");
        sample.put("openingBalance", 0);
        sample.put("closingBalance", agent.getBalance());
        sample.put("channel", "AGNT");
        rows.add(sample);
        return ApiResponse.ok(rows);
    }

    public Map<String, Object> decodeQr(String authorization, ApiEnvelope envelope) {
        requireAgent(authorization, envelope);
        String qr = text(envelope.getPayload(), "qr");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", "QR Beneficiary");
        data.put("iban", qr.length() > 8 ? qr.substring(0, Math.min(24, qr.length())) : "PK00QRDEMO");
        data.put("accountNo", data.get("iban"));
        data.put("amount", "");
        data.put("dueDate", "");
        return ApiResponse.ok(data);
    }

    public Map<String, Object> generateQr(String authorization, ApiEnvelope envelope) {
        AgentAccount agent = requireAgent(authorization, envelope);
        JsonNode p = envelope.getPayload();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("qr", "CAQR|" + agent.getMobile() + "|" + text(p, "amount"));
        data.put("type", text(p, "type").isBlank() ? "P" : text(p, "type"));
        return ApiResponse.ok(data);
    }

    @SuppressWarnings("unchecked")
    private void syncLocalBalance(AgentAccount agent, Map<String, Object> resp) {
        if (resp == null) return;
        Object dataObj = resp.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) return;
        Object bal = data.get("currentBalance");
        if (bal == null) bal = data.get("balance");
        if (bal == null) bal = data.get("availableBalance");
        if (bal == null) return;
        try {
            double v = Double.parseDouble(String.valueOf(bal).replace(",", "").trim());
            agent.setBalance(v);
            store.save(agent);
        } catch (NumberFormatException ignored) {
            // keep local seed
        }
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
        return store.find(mobile)
                .orElseThrow(() -> new UnauthorizedException("Invalid or missing token / agent"));
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? "" : v.asText("").trim();
    }

    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) { super(message); }
    }
}
