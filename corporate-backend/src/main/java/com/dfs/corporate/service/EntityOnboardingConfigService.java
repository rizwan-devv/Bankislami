package com.dfs.corporate.service;

import com.dfs.corporate.domain.CorporateEntityType;
import com.dfs.corporate.domain.DocumentPhase;
import com.dfs.corporate.domain.EntityOnboardingDocument;
import com.dfs.corporate.domain.PartyType;
import com.dfs.corporate.domain.RequiredDocument;
import com.dfs.corporate.repository.EntityOnboardingDocumentRepository;
import com.dfs.corporate.repository.RequiredDocumentRepository;
import com.dfs.corporate.web.dto.PartyResponse;
import com.dfs.corporate.web.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Back-office catalogs of documents per entity type and phase
 * ({@link DocumentPhase#REGISTRATION} vs {@link DocumentPhase#ONBOARDING}).
 */
@Service
public class EntityOnboardingConfigService {

    private final EntityOnboardingDocumentRepository documentRepository;
    private final RequiredDocumentRepository requiredDocumentRepository;

    public EntityOnboardingConfigService(EntityOnboardingDocumentRepository documentRepository,
                                         RequiredDocumentRepository requiredDocumentRepository) {
        this.documentRepository = documentRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
    }

    public boolean hasCatalog(CorporateEntityType entityType) {
        return entityType != null && documentRepository.existsByEntityType(entityType.name());
    }

    public boolean hasCatalog(CorporateEntityType entityType, DocumentPhase phase) {
        return entityType != null && phase != null
                && documentRepository.existsByEntityTypeAndPhaseAndActiveTrue(entityType.name(), phase.name());
    }

    public boolean acceptsUpload(CorporateEntityType entityType, String code) {
        if (entityType == null || code == null || code.isBlank()) return false;
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return documentRepository.existsByEntityTypeAndDocumentCodeAndActiveTrue(entityType.name(), normalized);
    }

    public List<EntityOnboardingDocument> activeRows(CorporateEntityType entityType,
                                                     DocumentPhase phase,
                                                     boolean partnershipUnregistered) {
        if (entityType == null || phase == null) return List.of();
        List<EntityOnboardingDocument> rows =
                documentRepository.findByEntityTypeAndPhaseAndActiveTrueOrderBySortOrderAscIdAsc(
                        entityType.name(), phase.name());
        if (phase == DocumentPhase.ONBOARDING
                && entityType == CorporateEntityType.PARTNERSHIP
                && partnershipUnregistered) {
            return rows.stream().filter(r -> !"PARTNERSHIP_REG_CERT".equals(r.getDocumentCode())).toList();
        }
        return rows;
    }

    /** @deprecated Prefer {@link #activeRows(CorporateEntityType, DocumentPhase, boolean)}. */
    public List<EntityOnboardingDocument> activeRows(CorporateEntityType entityType, boolean partnershipUnregistered) {
        return activeRows(entityType, DocumentPhase.ONBOARDING, partnershipUnregistered);
    }

    public List<PartyResponse.RequiredItem> checklist(CorporateEntityType entityType,
                                                      DocumentPhase phase,
                                                      boolean partnershipUnregistered,
                                                      Set<String> uploaded) {
        List<PartyResponse.RequiredItem> required = new ArrayList<>();
        for (EntityOnboardingDocument row : activeRows(entityType, phase, partnershipUnregistered)) {
            required.add(new PartyResponse.RequiredItem(
                    row.getDocumentCode(),
                    row.getDocumentLabel(),
                    "REQUIRED".equals(row.getRequirement()),
                    uploaded.contains(row.getDocumentCode()),
                    phase.name()));
        }
        return required;
    }

    public List<PartyResponse.RequiredItem> checklist(CorporateEntityType entityType,
                                                      boolean partnershipUnregistered,
                                                      Set<String> uploaded) {
        List<PartyResponse.RequiredItem> all = new ArrayList<>();
        all.addAll(checklist(entityType, DocumentPhase.REGISTRATION, partnershipUnregistered, uploaded));
        all.addAll(checklist(entityType, DocumentPhase.ONBOARDING, partnershipUnregistered, uploaded));
        return all;
    }

    /** Null when the uploaded set satisfies this entity/phase catalog. */
    public String submissionGap(CorporateEntityType entityType,
                                DocumentPhase phase,
                                boolean partnershipUnregistered,
                                Set<String> uploaded) {
        List<EntityOnboardingDocument> rows = activeRows(entityType, phase, partnershipUnregistered);
        if (rows.isEmpty()) return null;
        List<String> missing = new ArrayList<>();
        for (EntityOnboardingDocument row : rows) {
            if ("REQUIRED".equals(row.getRequirement()) && !uploaded.contains(row.getDocumentCode())) {
                missing.add(row.getDocumentLabel());
            }
        }
        Map<Integer, List<EntityOnboardingDocument>> groups = new LinkedHashMap<>();
        for (EntityOnboardingDocument row : rows) {
            if (!"ONE_OF".equals(row.getRequirement())) continue;
            groups.computeIfAbsent(row.getOneOfGroup(), k -> new ArrayList<>()).add(row);
        }
        List<String> oneOfMissing = new ArrayList<>();
        for (List<EntityOnboardingDocument> group : groups.values()) {
            boolean any = group.stream().anyMatch(r -> uploaded.contains(r.getDocumentCode()));
            if (!any) {
                oneOfMissing.add(group.stream().map(EntityOnboardingDocument::getDocumentLabel)
                        .reduce((a, b) -> a + "; " + b).orElse(""));
            }
        }
        if (missing.isEmpty() && oneOfMissing.isEmpty()) return null;
        String phaseLabel = phase == DocumentPhase.REGISTRATION ? "registration" : "onboarding";
        StringBuilder msg = new StringBuilder();
        if (!missing.isEmpty()) {
            msg.append("Missing required ").append(phaseLabel).append(" documents: ")
                    .append(String.join(", ", missing));
        }
        if (!oneOfMissing.isEmpty()) {
            if (!msg.isEmpty()) msg.append(". ");
            msg.append("Upload at least one ").append(phaseLabel).append(" option: ")
                    .append(String.join(" | ", oneOfMissing));
        }
        return msg.toString();
    }

    public String submissionGap(CorporateEntityType entityType, boolean partnershipUnregistered, Set<String> uploaded) {
        String reg = submissionGap(entityType, DocumentPhase.REGISTRATION, partnershipUnregistered, uploaded);
        String onb = submissionGap(entityType, DocumentPhase.ONBOARDING, partnershipUnregistered, uploaded);
        if (reg == null) return onb;
        if (onb == null) return reg;
        return reg + ". " + onb;
    }

    public List<Map<String, Object>> list(String entityType, String phaseRaw) {
        return list(entityType, phaseRaw, false);
    }

    /** Admin catalogue — set includeHidden to show soft-hidden docs so they can be restored. */
    public List<Map<String, Object>> list(String entityType, String phaseRaw, boolean includeHidden) {
        CorporateEntityType type = parseType(entityType);
        DocumentPhase phase = parsePhase(phaseRaw);
        List<EntityOnboardingDocument> rows = includeHidden
                ? documentRepository.findByEntityTypeAndPhaseOrderBySortOrderAscIdAsc(type.name(), phase.name())
                : documentRepository.findByEntityTypeAndPhaseAndActiveTrueOrderBySortOrderAscIdAsc(
                        type.name(), phase.name());
        List<Map<String, Object>> out = new ArrayList<>();
        for (EntityOnboardingDocument row : rows) {
            out.add(toView(row));
        }
        return out;
    }

    public List<Map<String, Object>> list(String entityType) {
        return list(entityType, DocumentPhase.ONBOARDING.name(), false);
    }

    /**
     * Update label / rule (REQUIRED | OPTIONAL | ONE_OF) / visibility.
     * Hidden docs stay in DB (active=false) and drop off applicant checklists.
     */
    @Transactional
    public Map<String, Object> update(Long id, String label, String requirementRaw,
                                      Boolean mandatory, Boolean active, Integer oneOfGroup) {
        EntityOnboardingDocument row = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        if (label != null && !label.isBlank()) {
            row.setDocumentLabel(cleanLabel(label));
        }
        if (requirementRaw != null && !requirementRaw.isBlank()) {
            applyRequirement(row, requirementRaw, oneOfGroup);
        } else if (mandatory != null) {
            applyRequirement(row, mandatory ? "REQUIRED" : "OPTIONAL", oneOfGroup);
        }
        if (active != null) {
            row.setActive(active);
        }
        documentRepository.save(row);
        syncUploadCatalog(row);
        return toView(row);
    }

    /** @deprecated Prefer {@link #update(Long, String, String, Boolean, Boolean, Integer)}. */
    @Transactional
    public Map<String, Object> update(Long id, String label, Boolean mandatory) {
        return update(id, label, null, mandatory, null, null);
    }

    @Transactional
    public Map<String, Object> add(String entityType, String phaseRaw, String label, String requirementRaw) {
        CorporateEntityType type = parseType(entityType);
        DocumentPhase phase = parsePhase(phaseRaw);
        String nextLabel = cleanLabel(label);
        String code = uniqueCode(type, phase, nextLabel);
        EntityOnboardingDocument existing =
                documentRepository.findByEntityTypeAndPhaseAndDocumentCode(type.name(), phase.name(), code).orElse(null);
        EntityOnboardingDocument row = existing != null ? existing : new EntityOnboardingDocument();
        row.setEntityType(type.name());
        row.setPhase(phase.name());
        row.setDocumentCode(code);
        row.setDocumentLabel(nextLabel);
        applyRequirement(row, requirementRaw != null ? requirementRaw : "REQUIRED", null);
        row.setActive(true);
        if (existing == null) {
            int next = documentRepository.findFirstByEntityTypeAndPhaseOrderBySortOrderDesc(type.name(), phase.name())
                    .map(r -> r.getSortOrder() + 1)
                    .orElse(1);
            row.setSortOrder(next);
        }
        documentRepository.save(row);
        syncUploadCatalog(row);
        return toView(row);
    }

    @Transactional
    public Map<String, Object> add(String entityType, String phaseRaw, String label, boolean mandatory) {
        return add(entityType, phaseRaw, label, mandatory ? "REQUIRED" : "OPTIONAL");
    }

    @Transactional
    public Map<String, Object> add(String entityType, String label, boolean mandatory) {
        return add(entityType, DocumentPhase.ONBOARDING.name(), label, mandatory);
    }

    /** Soft-hide from applicant onboarding (any seeded or custom row). */
    @Transactional
    public void remove(Long id) {
        EntityOnboardingDocument row = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        row.setActive(false);
        documentRepository.save(row);
    }

    @Transactional
    public Map<String, Object> restore(Long id) {
        EntityOnboardingDocument row = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Document not found"));
        row.setActive(true);
        documentRepository.save(row);
        syncUploadCatalog(row);
        return toView(row);
    }

    private void applyRequirement(EntityOnboardingDocument row, String requirementRaw, Integer oneOfGroup) {
        String req = requirementRaw.trim().toUpperCase(Locale.ROOT);
        if (!"REQUIRED".equals(req) && !"OPTIONAL".equals(req) && !"ONE_OF".equals(req)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "requirement must be REQUIRED, OPTIONAL, or ONE_OF");
        }
        row.setRequirement(req);
        row.setMandatory("REQUIRED".equals(req));
        if ("ONE_OF".equals(req)) {
            int group = oneOfGroup != null && oneOfGroup > 0 ? oneOfGroup : Math.max(1, row.getOneOfGroup());
            if (group <= 0) group = 1;
            row.setOneOfGroup(group);
        } else {
            row.setOneOfGroup(0);
        }
    }

    private void syncUploadCatalog(EntityOnboardingDocument row) {
        RequiredDocument catalog = requiredDocumentRepository
                .findByPartyTypeAndDocumentCode(PartyType.MERCHANT, row.getDocumentCode())
                .orElseGet(RequiredDocument::new);
        catalog.setPartyType(PartyType.MERCHANT);
        catalog.setDocumentCode(row.getDocumentCode());
        catalog.setDocumentLabel(row.getDocumentLabel());
        catalog.setMandatory("REQUIRED".equals(row.getRequirement()));
        requiredDocumentRepository.save(catalog);
    }

    private String uniqueCode(CorporateEntityType type, DocumentPhase phase, String label) {
        String slug = label.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        slug = slug.replaceAll("^_+|_+$", "");
        if (slug.length() > 40) slug = slug.substring(0, 40).replaceAll("_+$", "");
        if (slug.isBlank()) slug = "DOC";
        String code = "CUSTOM_" + slug;
        Set<String> taken = new LinkedHashSet<>();
        documentRepository.findByEntityTypeAndPhaseAndActiveTrueOrderBySortOrderAscIdAsc(type.name(), phase.name())
                .forEach(r -> taken.add(r.getDocumentCode()));
        if (!taken.contains(code)) return code;
        for (int i = 2; i < 50; i++) {
            String candidate = code + "_" + i;
            if (candidate.length() > 64) candidate = candidate.substring(0, 64);
            if (!taken.contains(candidate)) return candidate;
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "Could not create a document code for that name");
    }

    private static String cleanLabel(String label) {
        String next = label == null ? "" : label.trim();
        if (next.length() < 2 || next.length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Document name must be 2–200 characters");
        }
        return next;
    }

    private static CorporateEntityType parseType(String entityType) {
        try {
            return CorporateEntityType.valueOf(entityType.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown entity type");
        }
    }

    private static DocumentPhase parsePhase(String phaseRaw) {
        try {
            return DocumentPhase.parse(phaseRaw);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "phase must be REGISTRATION or ONBOARDING");
        }
    }

    private static Map<String, Object> toView(EntityOnboardingDocument row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", row.getId());
        m.put("entityType", row.getEntityType());
        m.put("phase", row.getPhase() != null ? row.getPhase() : DocumentPhase.ONBOARDING.name());
        m.put("documentCode", row.getDocumentCode());
        m.put("documentLabel", row.getDocumentLabel());
        m.put("mandatory", "REQUIRED".equals(row.getRequirement()));
        m.put("requirement", row.getRequirement());
        m.put("oneOfGroup", row.getOneOfGroup());
        m.put("active", row.isActive());
        m.put("hidden", !row.isActive());
        m.put("custom", row.getDocumentCode() != null && (row.getDocumentCode().startsWith("CUSTOM_")
                || row.getDocumentCode().startsWith("REG_")));
        return m;
    }
}
