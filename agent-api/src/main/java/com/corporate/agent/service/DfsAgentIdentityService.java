package com.corporate.agent.service;

import com.corporate.agent.dfs.DfsProxyClient;
import com.corporate.agent.domain.AgentAccount;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;

/**
 * Loads / refreshes agent CNIC (nid) from DFS accountDetails so login session + FT/IBFT have it.
 */
@Service
public class DfsAgentIdentityService {

    private static final Logger log = LoggerFactory.getLogger(DfsAgentIdentityService.class);

    private final DfsProxyClient dfs;
    private final AgentStore store;
    private final String defaultAccountLevel;

    public DfsAgentIdentityService(
            DfsProxyClient dfs,
            AgentStore store,
            @Value("${agent.account-level-code:L4}") String defaultAccountLevel) {
        this.dfs = dfs;
        this.store = store;
        this.defaultAccountLevel = defaultAccountLevel != null && !defaultAccountLevel.isBlank()
                ? defaultAccountLevel.trim() : "L4";
    }

    /** @return true if agent has a 13-digit CNIC after refresh attempt */
    public boolean ensureCnic(AgentAccount agent) {
        if (agent == null) return false;
        if (hasValidCnic(agent.getNidNo())) {
            return true;
        }
        if (!dfs.isEnabled() || !dfs.isPortalMode()) {
            return false;
        }
        ObjectNode payload = dfs.mapper().createObjectNode();
        payload.put("mobileNumber", agent.getMobile());
        Map<String, Object> details = dfs.postAppCorporate("/v1/corporate/accountDetails", payload);
        if (!isSuccess(details)) {
            log.warn("accountDetails failed for {} — cannot refresh CNIC", agent.getMobile());
            return hasValidCnic(agent.getNidNo());
        }
        applyDetails(agent, details);
        store.save(agent);
        boolean ok = hasValidCnic(agent.getNidNo());
        log.info("CNIC refresh for {} → nidLen={}", agent.getMobile(),
                agent.getNidNo() != null ? agent.getNidNo().replaceAll("\\D", "").length() : 0);
        return ok;
    }

    public void applyDetails(AgentAccount agent, Map<String, Object> detailsRoot) {
        if (agent == null || detailsRoot == null) return;
        Object dataObj = detailsRoot.get("data");
        Map<?, ?> data = dataObj instanceof Map<?, ?> m ? m : detailsRoot;

        String nid = findTextDeep(data, "nidNo", "cnic", "cnicNumber", "nationalId", "nid", "cnicNo");
        String digits = digitsOnly(nid);
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
        if (s == null) return null;
        String d = s.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    public static boolean isSuccess(Map<String, Object> resp) {
        if (resp == null) return false;
        Object code = resp.get("responsecode");
        if (code == null) code = resp.get("responseCode");
        return code != null && "000".equals(String.valueOf(code).trim());
    }

    /** Search map (and nested maps/lists) for the first non-blank matching key. */
    @SuppressWarnings("unchecked")
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
}
