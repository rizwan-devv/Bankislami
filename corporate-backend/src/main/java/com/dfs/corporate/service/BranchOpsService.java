package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.*;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.*;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BranchOpsService {

    private final CorporateXpinRepository xpinRepository;
    private final CorporateBillRepository billRepository;
    private final BranchBillActionRepository actionRepository;
    private final PartyRepository partyRepository;

    public BranchOpsService(CorporateXpinRepository xpinRepository,
                            CorporateBillRepository billRepository,
                            BranchBillActionRepository actionRepository,
                            PartyRepository partyRepository) {
        this.xpinRepository = xpinRepository;
        this.billRepository = billRepository;
        this.actionRepository = actionRepository;
        this.partyRepository = partyRepository;
    }

    private void requireBranch(AccountPrincipal principal) {
        if (principal.getRole() != Role.BRANCH_USER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Branch user access only");
        }
    }

    @Transactional
    public XpinVerifyResponse verifyXpin(AccountPrincipal principal, XpinVerifyRequest req) {
        requireBranch(principal);
        String code = req.getXpin().trim();
        CorporateXpin xpin = xpinRepository.findByPinCodeAndStatus(code, "ACTIVE")
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "XPin not found or inactive"));

        if (xpin.getValidUntil().isBefore(Instant.now())) {
            xpin.setStatus("EXPIRED");
            xpinRepository.save(xpin);
            throw new ApiException(HttpStatus.BAD_REQUEST, "XPin expired");
        }

        Party company = partyRepository.findById(xpin.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));

        String companyName = company.getBusinessName() != null && !company.getBusinessName().isBlank()
                ? company.getBusinessName() : company.getFullName();

        BranchBillAction action = new BranchBillAction();
        action.setPublicId(UUID.randomUUID().toString());
        action.setBranchAccountId(principal.getAccountId());
        action.setPartyId(company.getId());
        action.setXpinId(xpin.getId());
        action.setActionType("XPIN_VERIFY");
        action.setAmount(xpin.getAmount());
        action.setNotes("Verified XPin " + code);
        actionRepository.save(action);

        return new XpinVerifyResponse(
                true,
                xpin.getPinCode(),
                xpin.getPersonName(),
                xpin.getPhone(),
                xpin.getEmail(),
                xpin.getAmount(),
                xpin.getValidUntil(),
                company.getPublicId(),
                companyName,
                company.getEmail(),
                company.getPhone(),
                company.getNtnNumber(),
                "XPin valid. Company info shown. No funds deducted."
        );
    }

    @Transactional(readOnly = true)
    public List<BranchCompanyResponse> listCompanies(AccountPrincipal principal) {
        requireBranch(principal);
        List<Long> partyIds = billRepository.findDistinctActivePartyIds();
        List<BranchCompanyResponse> out = new ArrayList<>();
        for (Long partyId : partyIds) {
            partyRepository.findById(partyId).ifPresent(p -> {
                long count = billRepository.findByPartyIdAndActiveTrueOrderByCreatedAtDesc(partyId).size();
                String name = p.getBusinessName() != null && !p.getBusinessName().isBlank()
                        ? p.getBusinessName() : p.getFullName();
                out.add(new BranchCompanyResponse(p.getPublicId(), name, p.getNtnNumber(), p.getEmail(), count));
            });
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<CorporateBillResponse> listConsumers(AccountPrincipal principal, String companyPublicId) {
        requireBranch(principal);
        Party company = partyRepository.findByPublicId(companyPublicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));
        return billRepository.findByPartyIdAndActiveTrueOrderByCreatedAtDesc(company.getId()).stream()
                .map(b -> new CorporateBillResponse(
                        b.getPublicId(),
                        b.getConsumerNo(),
                        b.getConsumerName(),
                        b.getAmount(),
                        b.getAmountAfterDueDate(),
                        b.getDueDate(),
                        b.getBatchId(),
                        b.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public BranchPayBillResponse payOrSkip(AccountPrincipal principal, BranchPayBillRequest req) {
        requireBranch(principal);
        String action = req.getAction().trim().toUpperCase();
        if (!List.of("PAY", "SKIP", "FINISH").contains(action)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "action must be PAY, SKIP, or FINISH");
        }

        Party company = partyRepository.findByPublicId(req.getCompanyPublicId().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Company not found"));
        String companyName = company.getBusinessName() != null && !company.getBusinessName().isBlank()
                ? company.getBusinessName() : company.getFullName();

        CorporateBill bill = billRepository
                .findByPartyIdAndConsumerNoAndActiveTrue(company.getId(), req.getConsumerNo().trim())
                .orElse(null);

        if (bill == null && !"FINISH".equals(action)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Consumer not found in uploaded bills for this company");
        }

        BranchBillAction row = new BranchBillAction();
        row.setPublicId(UUID.randomUUID().toString());
        row.setBranchAccountId(principal.getAccountId());
        row.setPartyId(company.getId());
        row.setBillId(bill != null ? bill.getId() : null);
        row.setActionType(action);
        row.setConsumerNo(req.getConsumerNo().trim());
        row.setAmount(bill != null ? bill.getAmount() : null);
        row.setNotes(req.getNotes());
        actionRepository.save(row);

        String msg = switch (action) {
            case "PAY" -> "Payment recorded at branch (no wallet deduct — API not available).";
            case "SKIP" -> "Bill skipped.";
            default -> "Batch finished.";
        };

        return new BranchPayBillResponse(
                row.getPublicId(),
                action,
                companyName,
                bill != null ? bill.getConsumerNo() : req.getConsumerNo(),
                bill != null ? bill.getConsumerName() : null,
                bill != null ? bill.getAmount() : null,
                msg
        );
    }
}
