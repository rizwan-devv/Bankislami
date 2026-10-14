package com.corporate.agent.web;

import com.corporate.agent.service.TransactionGatewayService;
import com.corporate.agent.web.dto.ApiEnvelope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CashAndThirdPartyController {

    private final TransactionGatewayService gateway;

    public CashAndThirdPartyController(TransactionGatewayService gateway) {
        this.gateway = gateway;
    }

    @PostMapping("/agentapp/v1/customer/cashInInquiry")
    public Map<String, Object> cashInInquiry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.cashInInquiry(authorization, body);
    }

    /** Spelling matches DFS Agent Android (`genrateOtp`). */
    @PostMapping("/thirdparties/genrateOtp")
    public Map<String, Object> generateOtp(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.generateCashOutOtp(authorization, body);
    }
}
