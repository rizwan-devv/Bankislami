package com.corporate.agent.web;

import com.corporate.agent.service.TransactionGatewayService;
import com.corporate.agent.web.dto.ApiEnvelope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Financial transactions — proxy to DFS when dfs.proxy.enabled=true.
 * App → this API → DFS → App
 */
@RestController
@RequestMapping("/transactions/v1")
public class TransactionsController {

    private final TransactionGatewayService gateway;

    public TransactionsController(TransactionGatewayService gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/getAllBank")
    public Map<String, Object> getAllBanks(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return gateway.getAllBanks(authorization);
    }

    @GetMapping("/getbiller")
    public Map<String, Object> getBillers(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return gateway.getBillers(authorization);
    }

    @PostMapping("/ibft/titleFetch")
    public Map<String, Object> ibftTitleFetch(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.ibftTitleFetch(authorization, body);
    }

    @PostMapping("/ibft/advice")
    public Map<String, Object> ibftAdvice(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.ibftAdvice(authorization, body);
    }

    @PostMapping("/billInquiry")
    public Map<String, Object> billInquiry(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.billInquiry(authorization, body);
    }

    @PostMapping("/billPayment")
    public Map<String, Object> billPayment(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.billPayment(authorization, body);
    }

    @PostMapping("/initiateLocalFT")
    public Map<String, Object> initiateLocalFt(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.initiateLocalFt(authorization, body);
    }

    @PostMapping("/fundsTransferLocal")
    public Map<String, Object> fundsTransferLocal(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.fundsTransferLocal(authorization, body);
    }

    @PostMapping("/internalcashin")
    public Map<String, Object> internalCashIn(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.internalCashIn(authorization, body);
    }

    @PostMapping("/internalcashout")
    public Map<String, Object> internalCashOut(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return gateway.internalCashOut(authorization, body);
    }
}
