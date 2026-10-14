package com.corporate.agent.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Value("${dfs.proxy.enabled:false}")
    private boolean dfsProxyEnabled;

    @Value("${app.public-base-url}")
    private String publicBaseUrl;

    @GetMapping({"/", "/health", "/actuator/health"})
    public Map<String, Object> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "UP");
        m.put("service", "corporate-agent-api");
        m.put("publicBaseUrl", publicBaseUrl);
        m.put("dfsProxyEnabled", dfsProxyEnabled);
        m.put("flow", Map.of(
                "login", "managed-here",
                "registration", "managed-here",
                "transactions", dfsProxyEnabled ? "app→us→DFS→app" : "stub-until-DFS_PROXY_ENABLED=true"
        ));
        return m;
    }
}
