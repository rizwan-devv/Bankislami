package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.CorporateBillRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.BillUploadResult;
import com.dfs.corporate.web.dto.CorporateBillResponse;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CorporateBillService {

    private final CorporateBillRepository billRepository;
    private final PartyRepository partyRepository;

    public CorporateBillService(CorporateBillRepository billRepository, PartyRepository partyRepository) {
        this.billRepository = billRepository;
        this.partyRepository = partyRepository;
    }

    private Party requireActiveMerchant(AccountPrincipal principal) {
        if (principal.getRole() != Role.PARTY_USER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Corporate client access only");
        }
        Party party = partyRepository.findById(principal.getPartyId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Party not found"));
        if (party.getStatus() != PartyStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Party must be ACTIVE");
        }
        return party;
    }

    @Transactional(readOnly = true)
    public List<CorporateBillResponse> list(AccountPrincipal principal) {
        Party party = requireActiveMerchant(principal);
        return billRepository.findByPartyIdAndActiveTrueOrderByCreatedAtDesc(party.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public BillUploadResult uploadCsv(AccountPrincipal principal, MultipartFile file) {
        Party party = requireActiveMerchant(principal);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CSV file required");
        }
        String batchId = UUID.randomUUID().toString();
        int imported = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (first && looksLikeHeader(line)) {
                    first = false;
                    continue;
                }
                first = false;
                String[] cols = splitCsv(line);
                if (cols.length < 3) {
                    skipped++;
                    errors.add("Line " + lineNo + ": need consumerNo,name,amount");
                    continue;
                }
                try {
                    String consumerNo = cols[0].trim();
                    String name = cols[1].trim();
                    BigDecimal amount = new BigDecimal(cols[2].trim());
                    BigDecimal afterDue = cols.length > 3 && !cols[3].isBlank()
                            ? new BigDecimal(cols[3].trim()) : null;
                    LocalDate due = cols.length > 4 && !cols[4].isBlank()
                            ? LocalDate.parse(cols[4].trim()) : null;

                    if (consumerNo.isEmpty() || name.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    CorporateBill bill = new CorporateBill();
                    bill.setPublicId(UUID.randomUUID().toString());
                    bill.setPartyId(party.getId());
                    bill.setConsumerNo(consumerNo);
                    bill.setConsumerName(name);
                    bill.setAmount(amount);
                    bill.setAmountAfterDueDate(afterDue);
                    bill.setDueDate(due);
                    bill.setBatchId(batchId);
                    bill.setActive(true);
                    bill.setCreatedBy(principal.getUsername());
                    billRepository.save(bill);
                    imported++;
                } catch (Exception ex) {
                    skipped++;
                    errors.add("Line " + lineNo + ": " + ex.getMessage());
                }
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Failed to read CSV: " + e.getMessage());
        }

        String msg = imported + " bill(s) imported";
        if (skipped > 0) {
            msg += ", " + skipped + " skipped";
            if (!errors.isEmpty()) {
                msg += " (" + String.join("; ", errors.stream().limit(3).toList()) + ")";
            }
        }
        return new BillUploadResult(batchId, imported, skipped, msg);
    }

    private boolean looksLikeHeader(String line) {
        String lower = line.toLowerCase();
        return lower.contains("consumer") || lower.contains("name") && lower.contains("amount");
    }

    private String[] splitCsv(String line) {
        return line.split(",", -1);
    }

    private CorporateBillResponse toResponse(CorporateBill b) {
        return new CorporateBillResponse(
                b.getPublicId(),
                b.getConsumerNo(),
                b.getConsumerName(),
                b.getAmount(),
                b.getAmountAfterDueDate(),
                b.getDueDate(),
                b.getBatchId(),
                b.getCreatedAt()
        );
    }
}
