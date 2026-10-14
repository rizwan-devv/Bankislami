package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.CorporateXpinRepository;
import com.dfs.corporate.repository.PartyRepository;
import com.dfs.corporate.security.AccountPrincipal;
import com.dfs.corporate.web.dto.GenerateXpinRequest;
import com.dfs.corporate.web.dto.XpinResponse;
import com.dfs.corporate.web.error.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class CorporateXpinService {

    private static final Logger log = LoggerFactory.getLogger(CorporateXpinService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CorporateXpinRepository xpinRepository;
    private final PartyRepository partyRepository;
    private final MailService mailService;

    public CorporateXpinService(CorporateXpinRepository xpinRepository,
                                PartyRepository partyRepository,
                                MailService mailService) {
        this.xpinRepository = xpinRepository;
        this.partyRepository = partyRepository;
        this.mailService = mailService;
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

    @Transactional
    public XpinResponse generate(AccountPrincipal principal, GenerateXpinRequest req) {
        Party party = requireActiveMerchant(principal);
        String pin = nextUniquePin();
        Instant validUntil = Instant.now().plus(req.getValidityDays(), ChronoUnit.DAYS);

        CorporateXpin row = new CorporateXpin();
        row.setPublicId(UUID.randomUUID().toString());
        row.setPartyId(party.getId());
        row.setPinCode(pin);
        row.setPersonName(req.getName().trim());
        row.setPhone(req.getPhone().trim());
        row.setEmail(req.getEmail().trim().toLowerCase());
        row.setAmount(req.getAmount());
        row.setValidUntil(validUntil);
        row.setStatus("ACTIVE");
        row.setCreatedBy(principal.getUsername());
        xpinRepository.save(row);

        String company = party.getBusinessName() != null && !party.getBusinessName().isBlank()
                ? party.getBusinessName() : party.getFullName();
        String brand = mailService.brand();
        String body = """
                Your XPin for %s

                Name: %s
                XPin: %s
                Amount: %s
                Valid until: %s

                Present this PIN at the branch. No funds are deducted automatically.

                — %s
                """.formatted(company, row.getPersonName(), pin, row.getAmount(), validUntil, brand);

        mailService.send(row.getEmail(), "Your " + brand + " XPin", body);
        log.info("[SMS-DEV] XPin {} to phone={} (amount={})", pin, row.getPhone(), row.getAmount());

        return toResponse(row);
    }

    @Transactional(readOnly = true)
    public List<XpinResponse> list(AccountPrincipal principal) {
        Party party = requireActiveMerchant(principal);
        return xpinRepository.findByPartyIdOrderByCreatedAtDesc(party.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    private String nextUniquePin() {
        for (int i = 0; i < 40; i++) {
            String pin = String.format("%06d", RANDOM.nextInt(1_000_000));
            if (!xpinRepository.existsByPinCodeAndStatus(pin, "ACTIVE")) {
                return pin;
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, "Could not allocate unique XPin");
    }

    private XpinResponse toResponse(CorporateXpin row) {
        return new XpinResponse(
                row.getPublicId(),
                row.getPinCode(),
                row.getPersonName(),
                row.getPhone(),
                row.getEmail(),
                row.getAmount(),
                row.getValidUntil(),
                row.getStatus(),
                row.getCreatedAt()
        );
    }
}
