package com.corporate.agent.web;

import com.corporate.agent.service.WalletService;
import com.corporate.agent.web.dto.ApiEnvelope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Wallet / non-financial — local ledger for now. */
@RestController
@RequestMapping("/agentapp/v1")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping("/getbalance")
    public Map<String, Object> getBalance(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return walletService.getBalance(authorization, body);
    }

    @PostMapping("/viewlimits")
    public Map<String, Object> viewLimits(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return walletService.viewLimits(authorization, body);
    }

    @PostMapping("/changempin")
    public Map<String, Object> changeMpin(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return walletService.changeMpin(authorization, body);
    }

    @PostMapping("/miniStatment")
    public Map<String, Object> miniStatement(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return walletService.miniStatement(authorization, body);
    }

    @PostMapping("/decodeqr")
    public Map<String, Object> decodeQr(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return walletService.decodeQr(authorization, body);
    }

    @PostMapping("/generateQr")
    public Map<String, Object> generateQr(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody ApiEnvelope body) {
        return walletService.generateQr(authorization, body);
    }
}
