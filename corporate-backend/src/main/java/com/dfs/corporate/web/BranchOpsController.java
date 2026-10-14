package com.dfs.corporate.web;

import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.service.BranchOpsService;
import com.dfs.corporate.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}
