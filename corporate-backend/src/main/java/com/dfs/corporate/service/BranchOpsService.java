package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.BranchCashDfsClient;
import com.dfs.corporate.repository.*;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.util.IdentityFormats;
import com.dfs.corporate.web.dto.*;
import com.dfs.corporate.web.error.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BranchOpsService {

    private final CorporateXpinRepository xpinRepository;
    private final CorporateBillRepository billRepository;
    private final BranchBillActionRepository actionRepository;
    private final PartyRepository partyRepository;
    private final AccountRepository accountRepository;
    private final BranchCashDfsClient cashClient;
    private final ObjectMapper objectMapper;

    public BranchOpsService(CorporateXpinRepository xpinRepository,
                            CorporateBillRepository billRepository,
                            BranchBillActionRepository actionRepository,
                            PartyRepository partyRepository,
                            AccountRepository accountRepository,
                            BranchCashDfsClient cashClient,
                            ObjectMapper objectMapper) {
        this.xpinRepository = xpinRepository;
        this.billRepository = billRepository;
        this.actionRepository = actionRepository;
        this.partyRepository = partyRepository;
        this.accountRepository = accountRepository;
        this.cashClient = cashClient;
        this.objectMapper = objectMapper;
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

    public Map<String, Object> wallet(AccountPrincipal principal) {
        requireBranch(principal);
        return walletView(branchAccount(principal));
    }

    @Transactional
    public Map<String, Object> saveWallet(AccountPrincipal principal, String mobile, String cnic, String mpin, String imei) {
        requireBranch(principal);
        String phone = IdentityFormats.phoneDigits(mobile);
        String nid = IdentityFormats.cnicDigits(cnic);
        String pin = IdentityFormats.pinPlain(mpin);
        if (phone == null || phone.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DFS mobile is required");
        }
        if (nid == null || nid.length() < 13) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DFS CNIC must be 13 digits");
        }
        if (pin == null || pin.length() < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DFS MPIN is required");
        }
        Account account = branchAccount(principal);
        account.setDfsMobile(phone);
        account.setDfsCnic(nid);
        account.setDfsMpin(pin);
        account.setDfsImei(imei != null && !imei.isBlank() ? imei.trim() : cashClient.defaultImei());
        accountRepository.save(account);
        return walletView(account);
    }

    public Map<String, Object> cashInInquiry(AccountPrincipal principal, String accountNo, String depositorNid) {
        Account agent = requireWallet(principal);
        String customer = requireAccount(accountNo);
        return view(cashClient.cashInInquiry(imei(agent), customer, nidOrBlank(depositorNid)));
    }

    public Map<String, Object> cashIn(AccountPrincipal principal, String accountNo, String amount,
                                      String narration, String depositorNid, String depositorName, String depositorDob) {
        Account agent = requireWallet(principal);
        String customer = requireAccount(accountNo);
        String amt = money(amount);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("nidNo", agent.getDfsCnic());
        payload.put("mobileNumber", agent.getDfsMobile());
        payload.put("accountNo", customer);
        payload.put("amount", amt);
        payload.put("transPurposeId", cashClient.transPurposeId());
        payload.put("mpin", agent.getDfsMpin());
        payload.put("narration", narration == null ? "" : narration.trim());
        payload.put("bvs", "N");
        payload.put("cashInType", "W");
        payload.put("depositorNid", requireNid(depositorNid));
        payload.put("depositorName", depositorName == null ? "" : depositorName.trim());
        payload.put("depositorDob", depositorDob == null ? "" : depositorDob.trim());
        Map<String, Object> out = view(cashClient.internalCashIn(imei(agent), payload));
        out.put("fromAccountNo", agent.getDfsMobile());
        out.put("product", "CASH_IN");
        return out;
    }

    public Map<String, Object> cashOutOtp(AccountPrincipal principal, String mobileNumber) {
        Account agent = requireWallet(principal);
        String customer = requireAccount(mobileNumber);
        Map<String, Object> out = view(cashClient.generateOtp(imei(agent), customer));
        Object data = out.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), v));
            copy.remove("otpCode");
            copy.remove("otpPin");
            out.put("data", copy);
        }
        return out;
    }

    public Map<String, Object> cashOut(AccountPrincipal principal, String accountNo, String amount,
                                       String narration, String otpId, String otpPin) {
        Account agent = requireWallet(principal);
        String customer = requireAccount(accountNo);
        if (otpId == null || otpId.isBlank() || otpPin == null || otpPin.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer OTP is required for cash-out");
        }
        ObjectNode fund = objectMapper.createObjectNode();
        fund.put("nidNo", agent.getDfsCnic());
        fund.put("mobileNumber", agent.getDfsMobile());
        fund.put("accountNo", customer);
        fund.put("amount", money(amount));
        fund.put("transPurposeId", cashClient.transPurposeId());
        fund.put("narration", narration == null ? "" : narration.trim());
        fund.put("bvs", "N");
        ObjectNode otp = objectMapper.createObjectNode();
        otp.put("otpId", otpId.trim());
        otp.put("otpPin", otpPin.trim());
        otp.put("otpType", "FT");
        otp.put("userType", "C");
        otp.put("mobileNumber", customer);
        otp.put("email", "");
        Map<String, Object> out = view(cashClient.internalCashOut(imei(agent), fund, otp));
        out.put("fromAccountNo", customer);
        out.put("product", "CASH_OUT");
        return out;
    }

    private Account requireWallet(AccountPrincipal principal) {
        requireBranch(principal);
        Account account = branchAccount(principal);
        if (account.getDfsMobile() == null || account.getDfsCnic() == null || account.getDfsMpin() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Save the branch DFS wallet before cash-in or cash-out");
        }
        return account;
    }

    private Account branchAccount(AccountPrincipal principal) {
        return accountRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch account not found"));
    }

    private Map<String, Object> walletView(Account account) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean ready = account.getDfsMobile() != null && account.getDfsCnic() != null && account.getDfsMpin() != null;
        out.put("configured", ready);
        out.put("mobile", account.getDfsMobile());
        out.put("cnic", account.getDfsCnic());
        out.put("imei", account.getDfsImei());
        return out;
    }

    private String imei(Account account) {
        return account.getDfsImei() != null && !account.getDfsImei().isBlank()
                ? account.getDfsImei() : cashClient.defaultImei();
    }

    private static String nidOrBlank(String raw) {
        String digits = IdentityFormats.cnicDigits(raw);
        return digits == null ? "" : digits;
    }

    private static String requireNid(String raw) {
        String digits = IdentityFormats.cnicDigits(raw);
        if (digits == null || digits.length() != 13) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Depositor CNIC must be 13 digits");
        }
        return digits;
    }

    private static String requireAccount(String raw) {
        String digits = IdentityFormats.phoneDigits(raw);
        if (digits == null || digits.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer account / mobile is required");
        }
        return digits;
    }

    private static String money(String amount) {
        if (amount == null || amount.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "amount is required");
        }
        try {
            BigDecimal v = new BigDecimal(amount.trim());
            if (v.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "amount must be greater than zero");
            }
            return v.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "amount must be numeric");
        }
    }

    private Map<String, Object> view(JsonNode root) {
        JsonNode data = root != null && root.has("data") && root.get("data").isObject() ? root.get("data") : root;
        String code = text(root, "responsecode", "responseCode");
        if (code == null && data != null) {
            code = text(data, "responseCode", "responsecode");
        }
        String message = text(root, "messages", "message", "responseDescr", "responseDescription");
        if (message == null && data != null) {
            message = text(data, "responseDescr", "responseDescription", "messages");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("responsecode", code != null ? code : "");
        out.put("messages", message != null ? message : "");
        if (data != null && !data.isNull()) {
            out.put("data", objectMapper.convertValue(data, Map.class));
        }
        return out;
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null || node.isNull()) return null;
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                String v = node.get(key).asText("").trim();
                if (!v.isEmpty()) return v;
            }
        }
        return null;
    }
}
