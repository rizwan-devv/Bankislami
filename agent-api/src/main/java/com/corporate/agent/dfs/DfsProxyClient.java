package com.corporate.agent.dfs;

import com.corporate.agent.web.dto.ApiEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Forwards transaction calls to DFS.
 * <p>
 * Default mode {@code portal}: same as Corporate portal —
 * {@code X-Portal-Key} + {@code /v1/corporate/*} + channel COP.
 * Optional mode {@code bearer}: legacy Agent JWT / DFS_UPSTREAM_BEARER forward.
 */
@Component
public class DfsProxyClient {

    private static final Logger log = LoggerFactory.getLogger(DfsProxyClient.class);

    private final boolean enabled;
    private final String mode;
    private final String portalKey;
    private final String channel;
    private final String agentBaseUrl;
    private final String appBaseUrl;
    private final String txnBaseUrl;
    private final String portalTxnBaseUrl;
    private final String thirdPartyBaseUrl;
    private final String upstreamBearer;
    private final ObjectMapper mapper;
    private final RestClient agentClient;
    private final RestClient appClient;
    private final RestClient txnClient;
    private final RestClient portalTxnClient;
    private final RestClient tpClient;

    public DfsProxyClient(
            @Value("${dfs.proxy.enabled:false}") boolean enabled,
            @Value("${dfs.proxy.mode:portal}") String mode,
            @Value("${dfs.portal-key:}") String portalKey,
            @Value("${dfs.channel:COP}") String channel,
            @Value("${dfs.agent-base-url}") String agentBaseUrl,
            @Value("${dfs.app-base-url:http://46.225.160.93:18002/app}") String appBaseUrl,
            @Value("${dfs.txn-base-url}") String txnBaseUrl,
            @Value("${dfs.portal-txn-base-url:}") String portalTxnBaseUrl,
            @Value("${dfs.third-party-base-url}") String thirdPartyBaseUrl,
            @Value("${dfs.upstream-bearer:}") String upstreamBearer,
            @Value("${dfs.connect-timeout-ms:15000}") int connectTimeoutMs,
            @Value("${dfs.read-timeout-ms:45000}") int readTimeoutMs,
            ObjectMapper mapper) {
        this.enabled = enabled;
        this.mode = mode == null || mode.isBlank() ? "portal" : mode.trim().toLowerCase();
        this.portalKey = portalKey != null ? portalKey.trim() : "";
        this.channel = channel != null && !channel.isBlank() ? channel.trim() : "COP";
        this.agentBaseUrl = trimSlash(agentBaseUrl);
        this.appBaseUrl = trimSlash(appBaseUrl);
        this.txnBaseUrl = trimSlash(txnBaseUrl);
        this.portalTxnBaseUrl = trimSlash(
                portalTxnBaseUrl != null && !portalTxnBaseUrl.isBlank()
                        ? portalTxnBaseUrl
                        : ensureTransactionsBase(this.txnBaseUrl));
        this.thirdPartyBaseUrl = trimSlash(thirdPartyBaseUrl);
        this.upstreamBearer = upstreamBearer != null ? upstreamBearer.trim() : "";
        this.mapper = mapper;

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.agentClient = RestClient.builder().baseUrl(this.agentBaseUrl).requestFactory(factory).build();
        this.appClient = RestClient.builder().baseUrl(this.appBaseUrl).requestFactory(factory).build();
        this.txnClient = RestClient.builder().baseUrl(this.txnBaseUrl).requestFactory(factory).build();
        this.portalTxnClient = RestClient.builder().baseUrl(this.portalTxnBaseUrl).requestFactory(factory).build();
        this.tpClient = RestClient.builder().baseUrl(this.thirdPartyBaseUrl).requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPortalMode() {
        return "portal".equals(mode);
    }

    /** POST corporate path under portal txn base (e.g. /v1/corporate/initiateLocalFT). */
    public Map<String, Object> postCorporate(String corporatePath, ApiEnvelope appBody) {
        try {
            ensurePortalReady();
        } catch (IllegalStateException ex) {
            return portalConfigError(ex);
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("channel", channel);
        JsonNode payload = appBody != null && appBody.getPayload() != null
                ? appBody.getPayload()
                : mapper.createObjectNode();
        body.set("payload", payload);

        try {
            log.info("DFS portal POST channel={} → {}{}", channel, portalTxnBaseUrl, corporatePath);
            String raw = portalTxnClient.post()
                    .uri(corporatePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Portal-Key", portalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseMap(raw);
        } catch (RestClientResponseException ex) {
            log.warn("DFS portal HTTP {}: {}", ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString()));
            return errorFromUpstream(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("DFS portal failed: {}", ex.getMessage());
            return failMap("502", "DFS upstream failed: " + ex.getMessage());
        }
    }

    /** POST empty payload corporate (bankList). */
    public Map<String, Object> postCorporateEmpty(String corporatePath) {
        try {
            ensurePortalReady();
        } catch (IllegalStateException ex) {
            return portalConfigError(ex);
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("channel", channel);
        body.set("payload", mapper.createObjectNode());
        try {
            log.info("DFS portal POST channel={} → {}{}", channel, portalTxnBaseUrl, corporatePath);
            String raw = portalTxnClient.post()
                    .uri(corporatePath)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Portal-Key", portalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseMap(raw);
        } catch (RestClientResponseException ex) {
            log.warn("DFS portal HTTP {}: {}", ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString()));
            return errorFromUpstream(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            return failMap("502", "DFS upstream failed: " + ex.getMessage());
        }
    }

    public Map<String, Object> getCorporate(String corporatePath) {
        try {
            ensurePortalReady();
        } catch (IllegalStateException ex) {
            return portalConfigError(ex);
        }
        try {
            log.info("DFS portal GET → {}{}", portalTxnBaseUrl, corporatePath);
            String raw = portalTxnClient.get()
                    .uri(corporatePath)
                    .header("X-Portal-Key", portalKey)
                    .retrieve()
                    .body(String.class);
            return parseMap(raw);
        } catch (RestClientResponseException ex) {
            log.warn("DFS portal HTTP {}: {}", ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString()));
            return errorFromUpstream(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            return failMap("502", "DFS upstream failed: " + ex.getMessage());
        }
    }

    /**
     * AgentApp corporate APIs on DFS agent host (18001), e.g. getbalance / miniStatment.
     * Same X-Portal-Key + channel as Corporate portal.
     */
    public Map<String, Object> postAgentCorporate(String agentPath, ObjectNode payload) {
        return postPortalHost(agentClient, agentBaseUrl, agentPath, payload, "agent");
    }

    /** App corporate APIs (18002/app), e.g. accountDetails. */
    public Map<String, Object> postAppCorporate(String appPath, ObjectNode payload) {
        return postPortalHost(appClient, appBaseUrl, appPath, payload, "app");
    }

    private Map<String, Object> postPortalHost(
            RestClient client, String base, String path, ObjectNode payload, String label) {
        try {
            ensurePortalReady();
        } catch (IllegalStateException ex) {
            return portalConfigError(ex);
        }
        if (base == null || base.isBlank()) {
            return failMap("503", "dfs." + label + "-base-url is empty");
        }
        ObjectNode body = mapper.createObjectNode();
        body.put("channel", channel);
        body.set("payload", payload != null ? payload : mapper.createObjectNode());
        try {
            log.info("DFS {} portal POST channel={} → {}{}", label, channel, base, path);
            String raw = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Portal-Key", portalKey)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseMap(raw);
        } catch (RestClientResponseException ex) {
            log.warn("DFS {} portal HTTP {}: {}", label, ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString()));
            return errorFromUpstream(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("DFS {} portal failed: {}", label, ex.getMessage());
            return failMap("502", "DFS upstream failed: " + ex.getMessage());
        }
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    /** Deep-copy payload for enrichment without mutating the request body. */
    public ObjectNode copyPayload(JsonNode payload) {
        if (payload == null || payload.isNull() || payload.isMissingNode()) {
            return mapper.createObjectNode();
        }
        return payload.deepCopy();
    }

    public Map<String, Object> postTxn(String path, Object body, String clientAuthorization) {
        return post(txnClient, txnBaseUrl, path, body, clientAuthorization);
    }

    public Map<String, Object> getTxn(String path, String clientAuthorization) {
        return get(txnClient, txnBaseUrl, path, clientAuthorization);
    }

    public Map<String, Object> postAgent(String path, Object body, String clientAuthorization) {
        return post(agentClient, agentBaseUrl, path, body, clientAuthorization);
    }

    public Map<String, Object> postThirdParty(String path, Object body, String clientAuthorization) {
        return post(tpClient, thirdPartyBaseUrl, path, body, clientAuthorization);
    }

    private void ensurePortalReady() {
        if (portalKey.isBlank()) {
            throw new IllegalStateException(
                    "Set CORPORATE_PORTAL_API_KEY (same as portal) when DFS_PROXY_ENABLED=true and mode=portal");
        }
        if (portalTxnBaseUrl.isBlank()) {
            throw new IllegalStateException("dfs.portal-txn-base-url is empty");
        }
    }

    /** Soft-fail wrapper used by callers that prefer JSON error over exception. */
    public Map<String, Object> portalConfigError(Exception ex) {
        return failMap("503", ex.getMessage() != null ? ex.getMessage() : "Portal DFS proxy misconfigured");
    }

    private Map<String, Object> post(RestClient client, String base, String path, Object body, String clientAuth) {
        String auth = resolveAuth(clientAuth);
        try {
            log.info("DFS bearer POST {}{}", base, path);
            String raw = client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        if (auth != null) h.setBearerAuth(stripBearer(auth));
                    })
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return parseMap(raw);
        } catch (RestClientResponseException ex) {
            log.warn("DFS proxy HTTP {}: {}", ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString()));
            return errorFromUpstream(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.warn("DFS proxy failed: {}", ex.getMessage());
            return failMap("502", "DFS upstream failed: " + ex.getMessage());
        }
    }

    private Map<String, Object> get(RestClient client, String base, String path, String clientAuth) {
        String auth = resolveAuth(clientAuth);
        try {
            log.info("DFS bearer GET {}{}", base, path);
            String raw = client.get()
                    .uri(path)
                    .headers(h -> {
                        if (auth != null) h.setBearerAuth(stripBearer(auth));
                    })
                    .retrieve()
                    .body(String.class);
            return parseMap(raw);
        } catch (RestClientResponseException ex) {
            return errorFromUpstream(ex.getStatusCode().value(), ex.getResponseBodyAsString());
        } catch (Exception ex) {
            return failMap("502", "DFS upstream failed: " + ex.getMessage());
        }
    }

    private String resolveAuth(String clientAuthorization) {
        if (!upstreamBearer.isBlank()) return upstreamBearer;
        if (clientAuthorization != null && !clientAuthorization.isBlank()) return clientAuthorization;
        return null;
    }

    private Map<String, Object> parseMap(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            return okEmpty();
        }
        JsonNode node = mapper.readTree(raw);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = mapper.convertValue(node, Map.class);
        return map;
    }

    private Map<String, Object> errorFromUpstream(int status, String body) {
        try {
            if (body != null && body.trim().startsWith("{")) {
                return parseMap(body);
            }
        } catch (Exception ignored) {
        }
        return failMap(String.valueOf(status), truncate(body));
    }

    private static Map<String, Object> failMap(String code, String messages) {
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("responsecode", code);
        fail.put("messages", messages);
        fail.put("data", null);
        return fail;
    }

    private static Map<String, Object> okEmpty() {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("responsecode", "000");
        empty.put("messages", "SUCCESS");
        empty.put("data", null);
        return empty;
    }

    private static String stripBearer(String auth) {
        String t = auth.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) return t.substring(7).trim();
        return t;
    }

    private static String trimSlash(String url) {
        if (url == null) return "";
        String u = url.trim();
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    /** If base is host:18009 only, append /transactions for corporate paths. */
    private static String ensureTransactionsBase(String base) {
        if (base == null || base.isBlank()) return "";
        String u = trimSlash(base);
        if (u.endsWith("/transactions")) return u;
        return u + "/transactions";
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }
}
