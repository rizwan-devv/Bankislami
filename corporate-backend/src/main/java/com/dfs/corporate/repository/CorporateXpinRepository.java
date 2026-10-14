package com.dfs.corporate.repository;

import com.dfs.corporate.domain.CorporateXpin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorporateXpinRepository extends JpaRepository<CorporateXpin, Long> {
    List<CorporateXpin> findByPartyIdOrderByCreatedAtDesc(Long partyId);
    Optional<CorporateXpin> findByPinCodeAndStatus(String pinCode, String status);
    Optional<CorporateXpin> findByPublicId(String publicId);
    boolean existsByPinCodeAndStatus(String pinCode, String status);
}
