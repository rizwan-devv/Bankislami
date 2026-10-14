package com.corporate.agent.service;

import com.corporate.agent.dfs.DfsProxyClient;
import com.corporate.agent.domain.AgentAccount;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collection;
import java.util.Map;

/**
 * Loads agent CNIC for login session + FT/IBFT.
 * Order: existing local → DFS accountDetails → Corporate party DB (portal approve CNIC).
 * Never clears a valid local CNIC.
 */
@Service
public class DfsAgentIdentityService {

    private static final Logger log = LoggerFactory.getLogger(DfsAgentIdentityService.class);

    private final DfsProxyClient dfs;
    private final AgentStore store;
    private final ObjectMapper mapper;
    private final String defaultAccountLevel;
    private final String corporateBaseUrl;
    private final String portalKey;
    private final RestClient corporateClient;

    public DfsAgentIdentityService(
            DfsProxyClient dfs,
            AgentStore store,
            ObjectMapper mapper,
            @Value("${agent.account-level-code:L4}") String defaultAccountLevel,
            @Value("${agent.corporate-base-url:http://bankislami-backend:8091}") String corporateBaseUrl,
            @Value("${dfs.portal-key:}") String portalKey) {
        this.dfs = dfs;
        this.store = store;
        this.mapper = mapper;
        this.defaultAccountLevel = defaultAccountLevel != null && !defaultAccountLevel.isBlank()
                ? defaultAccountLevel.trim() : "L4";
        this.corporateBaseUrl = trimSlash(corporateBaseUrl);
        this.portalKey = portalKey != null ? portalKey.trim() : "";
        this.corporateClient = RestClient.builder().baseUrl(this.corporateBaseUrl).build();
    }

    /** @return true if agent has a 13-digit CNIC after refresh attempt */
    public boolean ensureCnic(AgentAccount agent) {
        if (agent == null) return false;
        if (hasValidCnic(agent.getNidNo())) {
            return true;
        }

        // 1) DFS accountDetails (often nidNo=null for corporate wallets)
        if (dfs.isEnabled() && dfs.isPortalMode()) {
            ObjectNode payload = dfs.mapper().createObjectNode();
            payload.put("mobileNumber", agent.getMobile());
            Map<String, Object> details = dfs.postAppCorporate("/v1/corporate/accountDetails", payload);
            if (isSuccess(details)) {
                applyDetails(agent, details);
            }
        }

        if (hasValidCnic(agent.getNidNo())) {
            store.save(agent);
            return true;
        }

        // 2) Corporate portal DB (back-office / KYC CNIC) — same source portal FT/IBFT uses
        if (applyCorporateCnic(agent)) {
            store.save(agent);
            log.info("CNIC from corporate party for {} → {}", agent.getMobile(), agent.getNidNo());
            return true;
        }

        store.save(agent);
        log.warn("CNIC still missing for {} after DFS + corporate lookup", agent.getMobile());
        return false;
    }

    private boolean applyCorporateCnic(AgentAccount agent) {
        if (corporateBaseUrl.isBlank() || portalKey.isBlank()) {
            return false;
        }
        try {
            String raw = corporateClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/public/agent-identity")
                            .queryParam("mobile", agent.getMobile())
                            .build())
                    .header("X-Portal-Key", portalKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
            if (raw == null || raw.isBlank()) return false;
            JsonNode root = mapper.readTree(raw);
            JsonNode data = root.has("data") && !root.get("data").isNull() ? root.get("data") : root;
            String nid = text(data, "nidNo", "cnicNumber", "cnic");
            String digits = digitsOnly(nid);
            if (digits != null && digits.length() >= 13) {
                agent.setNidNo(digits.substring(0, 13));
                String title = text(data, "accountTitle", "fullName");
                if (title != null && !title.isBlank()) {
                    agent.setAccountTitle(title.trim());
                }
                return true;
            }
        } catch (Exception ex) {
            log.warn("Corporate CNIC lookup failed for {}: {}", agent.getMobile(), ex.getMessage());
        }
        return false;
    }

    public void applyDetails(AgentAccount agent, Map<String, Object> detailsRoot) {
        if (agent == null || detailsRoot == null) return;
        Object dataObj = detailsRoot.get("data");
        Map<?, ?> data = dataObj instanceof Map<?, ?> m ? m : detailsRoot;

        String nid = findTextDeep(data, "nidNo", "cnic", "cnicNumber", "nationalId", "nid", "cnicNo");
        String digits = digitsOnly(nid);
        // DFS may return JSON null → skip; never wipe existing valid CNIC
        if (digits != null && digits.length() >= 13) {
            agent.setNidNo(digits.substring(0, 13));
        }

        String title = findTextDeep(data, "accountTitle", "customerName", "fullName", "name");
        if (title != null && !title.isBlank()) {
            agent.setAccountTitle(title.trim());
        }

        String level = findTextDeep(data, "accountLevelCode", "levelCode", "accountLevel");
        if (level != null && !level.isBlank()) {
            agent.setAccountLevelCode(level.trim());
        } else if (agent.getAccountLevelCode() == null || agent.getAccountLevelCode().isBlank()) {
            agent.setAccountLevelCode(defaultAccountLevel);
        }
    }

    public static boolean hasValidCnic(String raw) {
        String d = digitsOnly(raw);
        return d != null && d.length() >= 13;
    }

    public static String digitsOnly(String s) {
        if (s == null || "null".equalsIgnoreCase(s.trim())) return null;
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    public static boolean isSuccess(Map<String, Object> resp) {
        if (resp == null) return false;
        Object code = resp.get("responsecode");
        if (code == null) code = resp.get("responseCode");
        return code != null && "000".equals(String.valueOf(code).trim());
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null || node.isNull()) return "";
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) {
                String v = node.get(k).asText("").trim();
                if (!v.isEmpty() && !"null".equalsIgnoreCase(v)) return v;
            }
        }
        return "";
    }

    static String findTextDeep(Object node, String... keys) {
        if (node == null) return "";
        if (node instanceof Map<?, ?> map) {
            for (String k : keys) {
                Object v = map.get(k);
                if (v != null && !(v instanceof Map) && !(v instanceof Collection)) {
                    String s = String.valueOf(v).trim();
                    if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) return s;
                }
            }
            for (Object v : map.values()) {
                String found = findTextDeep(v, keys);
                if (found != null && !found.isEmpty()) return found;
            }
        } else if (node instanceof Collection<?> col) {
            for (Object v : col) {
                String found = findTextDeep(v, keys);
                if (found != null && !found.isEmpty()) return found;
            }
        }
        return "";
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }
}
