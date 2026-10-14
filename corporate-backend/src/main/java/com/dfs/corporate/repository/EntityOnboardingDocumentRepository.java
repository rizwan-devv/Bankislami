package com.dfs.corporate.repository;

import com.dfs.corporate.domain.EntityOnboardingDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EntityOnboardingDocumentRepository extends JpaRepository<EntityOnboardingDocument, Long> {
    List<EntityOnboardingDocument> findByEntityTypeAndActiveTrueOrderBySortOrderAscIdAsc(String entityType);

    List<EntityOnboardingDocument> findByEntityTypeAndPhaseAndActiveTrueOrderBySortOrderAscIdAsc(
            String entityType, String phase);

    boolean existsByEntityType(String entityType);

    boolean existsByEntityTypeAndPhaseAndActiveTrue(String entityType, String phase);

    Optional<EntityOnboardingDocument> findByEntityTypeAndDocumentCode(String entityType, String documentCode);

    boolean existsByEntityTypeAndDocumentCodeAndActiveTrue(String entityType, String documentCode);

    Optional<EntityOnboardingDocument> findByEntityTypeAndPhaseAndDocumentCode(
            String entityType, String phase, String documentCode);

    Optional<EntityOnboardingDocument> findFirstByEntityTypeAndPhaseOrderBySortOrderDesc(
            String entityType, String phase);
}
