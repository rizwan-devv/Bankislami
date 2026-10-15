package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.BranchOpsService;
import com.dfs.corporate.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/branch")
public class BranchOpsController {

    private final BranchOpsService branchOpsService;

    public BranchOpsController(BranchOpsService branchOpsService) {
        this.branchOpsService = branchOpsService;
    }

    @PostMapping("/xpin/verify")
    public XpinVerifyResponse verifyXpin(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody XpinVerifyRequest req
    ) {
        return branchOpsService.verifyXpin(principal, req);
    }

    @GetMapping("/companies")
    public List<BranchCompanyResponse> companies(@AuthenticationPrincipal AccountPrincipal principal) {
        return branchOpsService.listCompanies(principal);
    }

    @GetMapping("/companies/{companyPublicId}/bills")
    public List<CorporateBillResponse> companyBills(
            @AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable String companyPublicId
    ) {
        return branchOpsService.listConsumers(principal, companyPublicId);
    }

    @PostMapping("/pay-bill")
    public BranchPayBillResponse payBill(
            @AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody BranchPayBillRequest req
    ) {
        return branchOpsService.payOrSkip(principal, req);
    }

    @GetMapping("/wallet")
    public Map<String, Object> wallet(@AuthenticationPrincipal AccountPrincipal principal) {
        return branchOpsService.wallet(principal);
    }

    @PutMapping("/wallet")
    public Map<String, Object> saveWallet(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestBody Map<String, String> body
    ) {
        return branchOpsService.saveWallet(
                principal,
                body.get("mobile"),
                body.get("cnic"),
                body.get("mpin"),
                body.get("imei"));
    }

    @PostMapping("/cash-in/inquiry")
    public Map<String, Object> cashInInquiry(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestBody Map<String, String> body
    ) {
        return branchOpsService.cashInInquiry(principal, body.get("accountNo"), body.get("depositorNid"));
    }

    @PostMapping("/cash-in")
    public Map<String, Object> cashIn(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestBody Map<String, String> body
    ) {
        return branchOpsService.cashIn(
                principal,
                body.get("accountNo"),
                body.get("amount"),
                body.get("narration"),
                body.get("depositorNid"),
                body.get("depositorName"),
                body.get("depositorDob"));
    }

    @PostMapping("/cash-out/otp")
    public Map<String, Object> cashOutOtp(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestBody Map<String, String> body
    ) {
        return branchOpsService.cashOutOtp(principal, body.get("mobileNumber"));
    }

    @PostMapping("/cash-out")
    public Map<String, Object> cashOut(
            @AuthenticationPrincipal AccountPrincipal principal,
            @RequestBody Map<String, String> body
    ) {
        return branchOpsService.cashOut(
                principal,
                body.get("accountNo"),
                body.get("amount"),
                body.get("narration"),
                body.get("otpId"),
                body.get("otpPin"));
    }
}
