package com.corporate.agent.dfs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Forwards transaction (and optional wallet) calls to DFS upstream.
 * Flow: App → Corporate Agent API → DFS → response → App
 */
@Component
public class DfsProxyClient {

    private static final Logger log = LoggerFactory.getLogger(DfsProxyClient.class);

    private final boolean enabled;
    private final String agentBaseUrl;
    private final String txnBaseUrl;
    private final String thirdPartyBaseUrl;
    private final String upstreamBearer;
    private final ObjectMapper mapper;
    private final RestClient agentClient;
    private final RestClient txnClient;
    private final RestClient tpClient;

    public DfsProxyClient(
            @Value("${dfs.proxy.enabled:false}") boolean enabled,
            @Value("${dfs.agent-base-url}") String agentBaseUrl,
            @Value("${dfs.txn-base-url}") String txnBaseUrl,
            @Value("${dfs.third-party-base-url}") String thirdPartyBaseUrl,
            @Value("${dfs.upstream-bearer:}") String upstreamBearer,
            @Value("${dfs.connect-timeout-ms:15000}") int connectTimeoutMs,
            @Value("${dfs.read-timeout-ms:45000}") int readTimeoutMs,
            ObjectMapper mapper) {
        this.enabled = enabled;
        this.agentBaseUrl = trimSlash(agentBaseUrl);
        this.txnBaseUrl = trimSlash(txnBaseUrl);
        this.thirdPartyBaseUrl = trimSlash(thirdPartyBaseUrl);
        this.upstreamBearer = upstreamBearer != null ? upstreamBearer.trim() : "";
        this.mapper = mapper;

        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.agentClient = RestClient.builder().baseUrl(this.agentBaseUrl).requestFactory(factory).build();
        this.txnClient = RestClient.builder().baseUrl(this.txnBaseUrl).requestFactory(factory).build();
        this.tpClient = RestClient.builder().baseUrl(this.thirdPartyBaseUrl).requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return enabled;
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

    private Map<String, Object> post(RestClient client, String base, String path, Object body, String clientAuth) {
        String auth = resolveAuth(clientAuth);
        try {
            log.info("DFS proxy POST {}{}", base, path);
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
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("responsecode", "502");
            fail.put("messages", "DFS upstream failed: " + ex.getMessage());
            fail.put("data", null);
            return fail;
        }
    }

    private Map<String, Object> get(RestClient client, String base, String path, String clientAuth) {
        String auth = resolveAuth(clientAuth);
        try {
            log.info("DFS proxy GET {}{}", base, path);
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
            Map<String, Object> fail = new LinkedHashMap<>();
            fail.put("responsecode", "502");
            fail.put("messages", "DFS upstream failed: " + ex.getMessage());
            fail.put("data", null);
            return fail;
        }
    }

    private String resolveAuth(String clientAuthorization) {
        if (!upstreamBearer.isBlank()) return upstreamBearer;
        if (clientAuthorization != null && !clientAuthorization.isBlank()) return clientAuthorization;
        return null;
    }

    private Map<String, Object> parseMap(String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("responsecode", "000");
            empty.put("messages", "SUCCESS");
            empty.put("data", null);
            return empty;
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
        Map<String, Object> fail = new LinkedHashMap<>();
        fail.put("responsecode", String.valueOf(status));
        fail.put("messages", truncate(body));
        fail.put("data", null);
        return fail;
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

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 400 ? s.substring(0, 400) + "…" : s;
    }
}
