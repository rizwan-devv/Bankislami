package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.integration.dfs.CorporatePortalAppClient;
import com.dfs.corporate.integration.dfs.CorporatePortalTxnClient;
import com.dfs.corporate.repository.AccountRepository;
import com.dfs.corporate.repository.MockTransferRepository;
import com.dfs.corporate.repository.PartyRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live corporate transfers against DFS Transactions + App (X-Portal-Key).
 * Portal JWT auth selects the party; portal key never leaves the server.
 */
@Service
public class LiveTransferService {

    private final CorporatePortalTxnClient txnClient;
    private final CorporatePortalAppClient appClient;
    private final PartyRepository partyRepository;
    private final AccountRepository accountRepository;
    private final MockTransferRepository transferRepository;
    private final ObjectMapper objectMapper;

    public LiveTransferService(CorporatePortalTxnClient txnClient,
                               CorporatePortalAppClient appClient,
                               PartyRepository partyRepository,
                               AccountRepository accountRepository,
                               MockTransferRepository transferRepository,
                               ObjectMapper objectMapper) {
        this.txnClient = txnClient;
        this.appClient = appClient;
        this.partyRepository = partyRepository;
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> status(AccountPrincipal principal) {
        Party party = requireActiveParty(principal);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("liveEnabled", txnClient.isEnabled());
        out.put("txnConfigured", txnClient.isConfigured());
        out.put("appConfigured", appClient.isEnabled());
        String from = mobileOrNull(principal, party);
        out.put("fromAccountNo", from);
        out.put("hasNid", nidOrNull(principal, party) != null);
        out.put("hasDfsAppUserId", resolveAppUserId(party, null) != null);
        out.put("levelCode", levelOf(party));
        out.put("note",
                "IBFT bankList downstream is POST (Postman). Portal exposes GET /api/transfers/live/ibft/banks for convenience. "
                        + "getbiller is GET. Branch on responsecode=000.");
        out.put("products", List.of("FT", "IBFT", "UBP"));
        // Receive QR via accountDetails; outbound Raast pay still mock
        out.put("raastLive", appClient.isEnabled());
        out.put("raastQrAvailable", appClient.isEnabled());
        return out;
    }

    /**
     * Live DFS accountDetails for the party's wallet — includes Raast {@code qrCode}.
     */
    public RaastAccountDetailsResponse raastAccountDetails(AccountPrincipal principal) {
        if (!appClient.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Live DFS app disabled. Set DFS_PORTAL_API_ENABLED and CORPORATE_PORTAL_API_KEY.");
        }
        Party party = requireActiveParty(principal);
        String mobile = requireMobile(principal, party);
        JsonNode root = call(() -> appClient.accountDetails(mobile));
        RaastAccountDetailsResponse r = new RaastAccountDetailsResponse();
        if (root == null) {
            r.setResponsecode("999");
            r.setMessages("Empty DFS response");
            return r;
        }
        r.setResponsecode(text(root, "responsecode", "responseCode"));
        r.setMessages(text(root, "messages", "message"));
        JsonNode data = root.has("data") && !root.get("data").isNull() ? root.get("data") : root;
        r.setAccountNo(text(data, "accountNo", "accountNumber"));
        r.setMobileNo(text(data, "mobileNo", "mobileNumber"));
        r.setNidNo(text(data, "nidNo"));
        r.setGender(text(data, "gender"));
        r.setSegmentDescr(text(data, "segmentDescr"));
        r.setIban(text(data, "iban"));
        r.setQrCode(text(data, "qrCode", "qrString", "raastQr"));
        r.setAccountTitle(text(data, "accountTitle"));
        r.setAccountStatusDescr(text(data, "accountStatusDescr"));
        if (data.has("currentBalance") && !data.get("currentBalance").isNull()) {
            try {
                r.setCurrentBalance(data.get("currentBalance").decimalValue());
            } catch (Exception ignored) {
                try {
                    r.setCurrentBalance(new BigDecimal(data.get("currentBalance").asText()));
                } catch (Exception ignored2) { /* leave null */ }
            }
        }
        String code = r.getResponsecode();
        if (code != null && !code.isBlank() && !"000".equals(code) && !"00".equals(code)) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    r.getMessages() != null ? r.getMessages() : ("DFS accountDetails failed (" + code + ")"));
        }
        if (r.getQrCode() == null || r.getQrCode().isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "DFS accountDetails returned no qrCode");
        }
        return r;
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null || node.isNull()) return null;
        for (String k : keys) {
            if (node.has(k) && !node.get(k).isNull()) {
                String v = node.get(k).asText(null);
                if (v != null && !v.isBlank()) return v.trim();
            }
        }
        return null;
    }

    public DfsPortalTxnResponse ibftBanks(AccountPrincipal principal) {
        requireActiveParty(principal);
        return wrap("IBFT", null, call(() -> txnClient.ibftBankList()));
    }

    public DfsPortalTxnResponse ibftTitleFetch(AccountPrincipal principal, LiveIbftTitleRequest req) {
        Party party = requireActiveParty(principal);
        String from = requireMobile(principal, party);
        String nid = requireNid(principal, party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("fromAccountNo", from);
        payload.put("fromAccountNid", nid);
        payload.put("beneficiaryAccountNo", req.getBeneficiaryAccountNo().trim());
        payload.put("beneficiaryBankImd", req.getBeneficiaryBankImd().trim());
        payload.put("amount", normalizeAmount(req.getAmount()));
        return wrap("IBFT", from, call(() -> txnClient.ibftTitleFetch(payload)));
    }

    @Transactional
    public DfsPortalTxnResponse ibftAdvice(AccountPrincipal principal, LiveIbftAdviceRequest req) {
        Party party = requireActiveParty(principal);
        String from = requireMobile(principal, party);
        String nid = requireNid(principal, party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("fromAccountNo", from);
        payload.put("fromAccountNid", nid);
        payload.put("beneficiaryAccountNo", req.getBeneficiaryAccountNo().trim());
        payload.put("beneficiaryBankImd", req.getBeneficiaryBankImd().trim());
        payload.put("amount", normalizeAmount(req.getAmount()));
        String purpose = req.getPurposeOfPayment() != null ? req.getPurposeOfPayment().trim() : "";
        if (purpose.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Purpose of payment is required for live IBFT");
        }
        payload.put("purposeOfPayment", purpose);
        payload.put("transactionReference", blankToEmpty(req.getTransactionReference()));

        DfsPortalTxnResponse resp = wrap("IBFT", from, call(() -> txnClient.ibftAdvice(payload)));
        persist(party, principal, MockTransferProduct.IBFT, req.getAmount(),
                req.getBeneficiaryAccountNo(), null, req.getBeneficiaryName(),
                req.getNotes(), resp, from);
        return resp;
    }

    public DfsPortalTxnResponse ubpBillers(AccountPrincipal principal) {
        requireActiveParty(principal);
        return wrap("UBP", null, call(() -> txnClient.getBillers()));
    }

    public DfsPortalTxnResponse ubpInquiry(AccountPrincipal principal, LiveBillInquiryRequest req) {
        Party party = requireActiveParty(principal);
        String from = requireMobile(principal, party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("fromAccountNo", from);
        payload.put("utilityCompanyCode", req.getUtilityCompanyCode().trim());
        payload.put("consumerNo", req.getConsumerNo().trim());
        return wrap("UBP", from, call(() -> txnClient.billInquiry(payload)));
    }

    @Transactional
    public DfsPortalTxnResponse ubpPay(AccountPrincipal principal, LiveBillPaymentRequest req) {
        Party party = requireActiveParty(principal);
        String from = requireMobile(principal, party);
        String nid = requireNid(principal, party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("fromAccountNo", from);
        payload.put("fromAccountNid", nid);
        payload.put("utilityCompanyCode", req.getUtilityCompanyCode().trim());
        payload.put("consumerNo", req.getConsumerNo().trim());
        payload.put("amount", normalizeAmount(req.getAmount()));
        payload.put("transactionReference", blankToEmpty(req.getTransactionReference()));

        DfsPortalTxnResponse resp = wrap("UBP", from, call(() -> txnClient.billPayment(payload)));
        MockTransfer t = base(party, principal, MockTransferProduct.UBP);
        t.setAmount(parseAmount(req.getAmount()));
        t.setUbpCompany(req.getUtilityCompanyCode());
        t.setConsumerNumber(req.getConsumerNo());
        t.setMobile(from);
        t.setAccountNumber(req.getConsumerNo().trim());
        t.setBeneficiaryName(trim(req.getBeneficiaryName()));
        t.setNotes(trim(req.getNotes()));
        applyStatus(t, resp);
        transferRepository.save(t);
        return resp;
    }

    /**
     * Customer pays the saved branch wallet. The customer mobile, CNIC, and MPIN are the payer.
     * The branch wallet is only the beneficiary.
     */
    @Transactional
    public DfsPortalTxnResponse cashOutToBranch(AccountPrincipal principal, String customerMobile, String customerNid,
                                                String customerMpin, String amount, String narration) {
        Party party = requireActiveParty(principal);
        Account wallet = branchWallet(principal);
        String branchMobile = wallet == null ? null : IdentityFormats.phoneDigits(wallet.getDfsMobile());
        if (branchMobile == null || branchMobile.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Save the branch DFS wallet (mobile) before transfers");
        }
        String payerMobile = IdentityFormats.phoneDigits(customerMobile);
        String payerNid = IdentityFormats.cnicDigits(customerNid);
        String mpin = IdentityFormats.pinPlain(customerMpin);
        if (payerMobile == null || payerMobile.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer mobile is required");
        }
        if (payerNid == null || payerNid.length() != 13) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer CNIC must be 13 digits");
        }
        if (mpin == null || mpin.length() < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Customer MPIN is required");
        }
        String amt = normalizeAmount(amount);
        String note = narration == null || narration.isBlank() ? "Cash out" : narration.trim();

        ObjectNode init = objectMapper.createObjectNode();
        init.put("mobileNumber", payerMobile);
        init.put("nidNo", payerNid);
        init.put("accountNo", branchMobile);
        init.put("amount", amt);
        init.put("accountType", "W");
        DfsPortalTxnResponse started = wrap("CASH_OUT", payerMobile, call(() -> txnClient.initiateLocalFt(init)));
        if (!"000".equals(started.getResponsecode())) {
            return started;
        }

        ObjectNode conf = objectMapper.createObjectNode();
        conf.put("mobileNumber", payerMobile);
        conf.put("nidNo", payerNid);
        conf.put("accountNo", branchMobile);
        conf.put("accountType", "W");
        conf.put("amount", amt);
        conf.put("mpin", mpin);
        conf.put("transPurposeId", "1");
        conf.put("narration", note);
        DfsPortalTxnResponse done = wrap("CASH_OUT", payerMobile, call(() -> txnClient.fundsTransferLocal(conf)));
        String title = titleOf(started);
        persist(party, principal, MockTransferProduct.CASH_OUT, amt, branchMobile, null, title, note, done, payerMobile);
        done.setProduct("CASH_OUT");
        ObjectNode data = done.getData() != null && done.getData().isObject()
                ? (ObjectNode) done.getData()
                : objectMapper.createObjectNode();
        data.put("accountNo", branchMobile);
        if (title != null) {
            data.put("beneficiaryName", title);
        }
        done.setData(data);
        return done;
    }

    public DfsPortalTxnResponse ftInitiate(AccountPrincipal principal, LiveFtInitiateRequest req) {
        Party party = requireActiveParty(principal);
        String mobile = requireMobile(principal, party);
        String nid = requireNid(principal, party);
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobile);
        payload.put("nidNo", nid);
        payload.put("accountNo", IdentityFormats.phoneDigits(req.getAccountNo()));
        payload.put("amount", normalizeAmount(req.getAmount()));
        payload.put("accountType", blankTo(req.getAccountType(), "W"));
        // Never send type=QR from portal (Postman warning)
        return wrap("FT", mobile, call(() -> txnClient.initiateLocalFt(payload)));
    }

    @Transactional
    public DfsPortalTxnResponse ftConfirm(AccountPrincipal principal, LiveFtConfirmRequest req) {
        return ftConfirm(principal, req, MockTransferProduct.FT);
    }

    @Transactional
    public DfsPortalTxnResponse ftConfirm(AccountPrincipal principal, LiveFtConfirmRequest req, MockTransferProduct product) {
        Party party = requireActiveParty(principal);
        String mobile = requireMobile(principal, party);
        String nid = requireNid(principal, party);
        String mpin = IdentityFormats.pinPlain(req.getMpin());
        if (mpin == null || mpin.length() < 4) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Valid customer MPIN is required");
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("mobileNumber", mobile);
        payload.put("nidNo", nid);
        payload.put("accountNo", IdentityFormats.phoneDigits(req.getAccountNo()));
        payload.put("accountType", blankTo(req.getAccountType(), "W"));
        payload.put("amount", normalizeAmount(req.getAmount()));
        payload.put("mpin", mpin);
        payload.put("transPurposeId", blankTo(req.getTransPurposeId(), "1"));
        payload.put("narration", blankToEmpty(req.getNarration()));

        DfsPortalTxnResponse resp = wrap("FT", mobile, call(() -> txnClient.fundsTransferLocal(payload)));
        persist(party, principal, product, req.getAmount(),
                req.getAccountNo(), null, req.getBeneficiaryName(), req.getNarration(), resp, mobile);
        return resp;
    }

    public DfsPortalTxnResponse verifyMpin(AccountPrincipal principal, LiveMpinVerifyRequest req) {
        Party party = requireActiveParty(principal);
        String mobile = requireMobile(principal, party);
        String mpin = IdentityFormats.pinPlain(req.getMpin());
        if (mpin == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "mpin is required");
        }
        return wrap("MPIN", mobile, call(() -> appClient.verifyMpin(mobile, mpin)));
    }

    private DfsPortalTxnResponse wrap(String product, String from, JsonNode root) {
        DfsPortalTxnResponse r = DfsPortalTxnResponse.from(root);
        r.setProduct(product);
        r.setFromAccountNo(from);
        return r;
    }

    private JsonNode call(ThrottledCall call) {
        try {
            return call.run();
        } catch (IllegalStateException ex) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface ThrottledCall {
        JsonNode run();
    }

    private void persist(Party party, AccountPrincipal principal, MockTransferProduct product,
                         String amount, String accountNumber, String bankName,
                         String beneficiaryName, String notes, DfsPortalTxnResponse resp, String fromMobile) {
        MockTransfer t = base(party, principal, product);
        t.setAmount(parseAmount(amount));
        t.setAccountNumber(trim(accountNumber));
        t.setMobile(trim(fromMobile));
        t.setBankName(trim(bankName));
        t.setBeneficiaryName(trim(beneficiaryName));
        t.setNotes(trim(notes));
        applyStatus(t, resp);
        transferRepository.save(t);
    }

    private void applyStatus(MockTransfer t, DfsPortalTxnResponse resp) {
        String code = resp.getResponsecode();
        boolean ok = "000".equals(code);
        t.setStatus(ok ? "LIVE_SUCCESS" : "LIVE_FAIL_" + (code != null ? code : "UNKNOWN"));
        t.setMockTxnRef("LIVE-" + (code != null ? code : "NA") + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        resp.setPortalTxnRef(t.getMockTxnRef());
        String msg = resp.getMessages();
        if (msg != null) {
            t.setNotes((t.getNotes() != null ? t.getNotes() + " · " : "") + msg);
        }
    }

    private MockTransfer base(Party party, AccountPrincipal principal, MockTransferProduct product) {
        MockTransfer t = new MockTransfer();
        t.setPublicId(UUID.randomUUID().toString());
        t.setPartyId(party.getId());
        t.setProductType(product);
        t.setMode(MockTransferMode.SINGLE);
        t.setCreatedBy(principal.getUsername());
        t.setMockTxnRef("LIVE-PENDING");
        t.setStatus("LIVE_PENDING");
        return t;
    }

    private Party requireActiveParty(AccountPrincipal principal) {
        if (principal.getPartyId() == null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Merchant party required");
        }
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Transfers available after entity is ACTIVE");
        }
        return party;
    }

    /** Branch desk pays from the linked DFS wallet. Merchants keep the party phone / CNIC. */
    private Account branchWallet(AccountPrincipal principal) {
        if (principal.getRole() != Role.BRANCH_USER) {
            return null;
        }
        return accountRepository.findByEmailIgnoreCase(principal.getUsername()).orElse(null);
    }

    private String mobileOrNull(AccountPrincipal principal, Party party) {
        Account wallet = branchWallet(principal);
        if (wallet != null) {
            return IdentityFormats.phoneDigits(wallet.getDfsMobile());
        }
        return IdentityFormats.phoneDigits(party.getPhone());
    }

    private String nidOrNull(AccountPrincipal principal, Party party) {
        Account wallet = branchWallet(principal);
        if (wallet != null) {
            return IdentityFormats.cnicDigits(wallet.getDfsCnic());
        }
        return IdentityFormats.cnicDigits(party.getCnicNumber());
    }

    private String requireMobile(AccountPrincipal principal, Party party) {
        String mobile = mobileOrNull(principal, party);
        if (mobile == null || mobile.length() < 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    principal.getRole() == Role.BRANCH_USER
                            ? "Save the branch DFS wallet (mobile) before transfers"
                            : "Party has no valid mobile (fromAccountNo / mobileNumber) for DFS transfers");
        }
        return mobile;
    }

    private String requireNid(AccountPrincipal principal, Party party) {
        String nid = nidOrNull(principal, party);
        if (nid == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    principal.getRole() == Role.BRANCH_USER
                            ? "Save the branch DFS wallet (CNIC) before transfers"
                            : "Party CNIC / NID is required (fromAccountNid / nidNo)");
        }
        return nid;
    }

    private String requireNidOptional(Party party) {
        return IdentityFormats.cnicDigits(party.getCnicNumber());
    }

    private String resolveAppUserId(Party party, String override) {
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        return DfsWalletIdentityService.requireDfsAppUserId(party);
    }

    private String levelOf(Party party) {
        return party.getLevelCode() != null && !party.getLevelCode().isBlank()
                ? party.getLevelCode().trim() : "L4";
    }

    private static String normalizeAmount(String amount) {
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

    private static BigDecimal parseAmount(String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String blankTo(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String titleOf(DfsPortalTxnResponse response) {
        JsonNode data = response.getData();
        if (data == null || !data.isObject()) {
            return null;
        }
        for (String key : new String[]{"accountTitle", "beneficiaryTitle", "title", "name"}) {
            if (data.has(key) && !data.get(key).isNull()) {
                String value = data.get(key).asText("").trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
