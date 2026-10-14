package com.corporate.agent.web;

import com.corporate.agent.service.AuthService;
import com.corporate.agent.web.dto.ApiEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Auth & device onboarding — managed locally (not DFS).
 * Paths match DFS Agent Android OnboardingApi.
 */
@RestController
@RequestMapping("/agentapp/v1")
public class OnboardingController {

    private final AuthService authService;

    public OnboardingController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody ApiEnvelope body) {
        return authService.login(body);
    }

    @PostMapping("/updateDevice")
    public Map<String, Object> updateDevice(@RequestBody ApiEnvelope body) {
        return authService.updateDevice(body);
    }

    @PostMapping("/verifyUpdateDevice")
    public Map<String, Object> verifyUpdateDevice(@RequestBody ApiEnvelope body) {
        return authService.verifyUpdateDevice(body);
    }

    @PostMapping("/agentDeviceRegistration")
    public Map<String, Object> agentDeviceRegistration(@RequestBody ApiEnvelope body) {
        return authService.agentDeviceRegistration(body);
    }

    @PostMapping("/verifyAgentdeviceRegistration")
    public Map<String, Object> verifyAgentDeviceRegistration(@RequestBody ApiEnvelope body) {
        return authService.verifyAgentDeviceRegistration(body);
    }

    @GetMapping("/getAccountUpgradeLovs")
    public Map<String, Object> getAccountUpgradeLovs() {
        return authService.getAccountUpgradeLovs();
    }
}
