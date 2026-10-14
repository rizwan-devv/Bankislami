package com.dfs.corporate.service;

import com.dfs.corporate.domain.*;
import com.dfs.corporate.repository.AssociatedPersonRepository;
import com.dfs.corporate.repository.PartnerAppUserRepository;
import com.dfs.corporate.repository.PartyDocumentRepository;
import com.dfs.corporate.repository.PartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps party status in sync with document completeness after submit.
 * <ul>
 *   <li>INCOMPLETE — required / one-of docs missing or rejected (hidden and optional catalog docs do not count)</li>
 *   <li>SUBMITTED — docs OK, identity KYC still required and not yet done by anyone</li>
 *   <li>PENDING_APPROVAL — docs OK, and KYC is off for this entity or one person / back office has completed it</li>
 * </ul>
 */
@Service
public class PartyStatusSyncService {

    private final PartyRepository partyRepository;
    private final PartyDocumentRepository documentRepository;
    private final AssociatedPersonRepository associatedPersonRepository;
    private final PartnerAppUserRepository appUserRepository;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final EntityKycPolicyService entityKycPolicyService;
    private final EntityOnboardingConfigService entityOnboardingConfigService;

    public PartyStatusSyncService(PartyRepository partyRepository,
                                  PartyDocumentRepository documentRepository,
                                  AssociatedPersonRepository associatedPersonRepository,
                                  PartnerAppUserRepository appUserRepository,
                                  SanctionsScreeningService sanctionsScreeningService,
                                  EntityKycPolicyService entityKycPolicyService,
                                  EntityOnboardingConfigService entityOnboardingConfigService) {
        this.partyRepository = partyRepository;
        this.documentRepository = documentRepository;
        this.associatedPersonRepository = associatedPersonRepository;
        this.appUserRepository = appUserRepository;
        this.sanctionsScreeningService = sanctionsScreeningService;
        this.entityKycPolicyService = entityKycPolicyService;
        this.entityOnboardingConfigService = entityOnboardingConfigService;
    }

    /**
     * Recompute status for post-submit parties. Does not touch DRAFT / ACTIVE / REJECTED / SUSPENDED.
     */
    @Transactional
    public Party syncAfterDocumentChange(Long partyId) {
        Party party = partyRepository.findById(partyId).orElse(null);
        if (party == null) {
            return null;
        }
        return sync(party);
    }

    @Transactional
    public Party sync(Party party) {
        PartyStatus current = party.getStatus();
        if (current != PartyStatus.SUBMITTED
                && current != PartyStatus.PENDING_APPROVAL
                && current != PartyStatus.INCOMPLETE) {
            return party;
        }

        boolean docsIncomplete = hasRejectedOrMissingDocs(party);
        if (docsIncomplete) {
            if (current != PartyStatus.INCOMPLETE) {
                party.setStatus(PartyStatus.INCOMPLETE);
                return partyRepository.save(party);
            }
            return party;
        }

        boolean allKyc = kycSatisfied(party.getId());
        if (allKyc) {
            sanctionsScreeningService.screen(party.getId(), false);
            party = partyRepository.findById(party.getId()).orElse(party);
        }
        PartyStatus target = allKyc ? PartyStatus.PENDING_APPROVAL : PartyStatus.SUBMITTED;
        if (party.getStatus() != target) {
            party.setStatus(target);
            return partyRepository.save(party);
        }
        return party;
    }

    public boolean hasRejectedOrMissingDocs(Party party) {
        List<PartyDocument> docs = documentRepository.findByPartyIdOrderByUploadedAtDesc(party.getId());
        Set<String> uploadedOk = docs.stream()
                .filter(d -> d.getStatus() != DocumentStatus.REJECTED)
                .map(PartyDocument::getDocumentCode)
                .collect(Collectors.toSet());

        if (party.getEntityType() == null) {
            return docs.isEmpty();
        }

        boolean unreg = Boolean.TRUE.equals(party.getPartnershipUnregistered());

        // Align with submit / checklist: hidden (active=false) and OPTIONAL docs never force INCOMPLETE.
        // Rejected required/one-of rows simply leave that code out of uploadedOk.
        if (party.getPartyType() != PartyType.SUB_MERCHANT
                && entityOnboardingConfigService.hasCatalog(party.getEntityType())) {
            if (entityOnboardingConfigService.submissionGap(party.getEntityType(), unreg, uploadedOk) != null) {
                return true;
            }
        } else {
            Set<String> mandatory = ConsolidatedKycRules.mandatoryDocuments(
                    party.getPartyType(), party.getEntityType(), unreg);
            for (String code : mandatory) {
                if (!uploadedOk.contains(code)) {
                    return true;
                }
            }
            if (ConsolidatedKycRules.oneOfGap(party.getEntityType(), uploadedOk) != null) {
                return true;
            }
        }

        if (ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            for (AssociatedPerson p : associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId())) {
                if (p.getRoleType() != AssociatedPersonRole.PARTNER) {
                    continue;
                }
                List<String> need = List.of(
                        ConsolidatedKycRules.partnerCnicFront(p.getId()),
                        ConsolidatedKycRules.partnerCnicBack(p.getId()),
                        ConsolidatedKycRules.partnerAgreement(p.getId())
                );
                for (String code : need) {
                    if (!uploadedOk.contains(code)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /** True when rejecting this code should drive INCOMPLETE / customer re-upload mail. */
    public boolean countsTowardIncomplete(Party party, String documentCode) {
        if (party == null || documentCode == null || documentCode.isBlank()) {
            return false;
        }
        String code = documentCode.trim();
        boolean unreg = Boolean.TRUE.equals(party.getPartnershipUnregistered());
        if (party.getPartyType() != PartyType.SUB_MERCHANT
                && entityOnboardingConfigService.hasCatalog(party.getEntityType())) {
            return entityOnboardingConfigService.countsTowardIncomplete(party.getEntityType(), unreg, code);
        }
        Set<String> mandatory = ConsolidatedKycRules.mandatoryDocuments(
                party.getPartyType(), party.getEntityType(), unreg);
        if (mandatory.contains(code)) {
            return true;
        }
        if (ConsolidatedKycRules.oneOfCodes(party.getEntityType()).contains(code)) {
            return true;
        }
        if (ConsolidatedKycRules.needsPartnerRoster(party.getEntityType())) {
            for (AssociatedPerson p : associatedPersonRepository.findByPartyIdOrderByIdAsc(party.getId())) {
                if (p.getRoleType() != AssociatedPersonRole.PARTNER) continue;
                if (code.equals(ConsolidatedKycRules.partnerCnicFront(p.getId()))
                        || code.equals(ConsolidatedKycRules.partnerCnicBack(p.getId()))
                        || code.equals(ConsolidatedKycRules.partnerAgreement(p.getId()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * KYC gate for approval. Off for the entity type means satisfied.
     * On means any one app user finished KYC, or the manual ID pack is on file.
     * Not every partner has to complete the app.
     */
    public boolean kycSatisfied(Long partyId) {
        Party party = partyRepository.findById(partyId).orElse(null);
        if (party == null) return false;
        if (ConsolidatedKycRules.backOfficeKycOnly(party.getEntityType())) {
            return entityKycPolicyService.manualPackComplete(partyId);
        }
        if (!entityKycPolicyService.isKycRequired(party.getEntityType())) {
            return true;
        }
        boolean anyApp = appUserRepository.findByPartyIdOrderByIdAsc(partyId).stream()
                .anyMatch(u -> u.getStatus() == PartnerAppKycStatus.KYC_COMPLETED);
        return anyApp || entityKycPolicyService.manualPackComplete(partyId);
    }

    public boolean allPartnerKycCompleted(Long partyId) {
        return kycSatisfied(partyId);
    }
}
