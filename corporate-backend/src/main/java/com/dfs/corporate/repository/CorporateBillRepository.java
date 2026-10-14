package com.dfs.corporate.repository;

import com.dfs.corporate.domain.CorporateBill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CorporateBillRepository extends JpaRepository<CorporateBill, Long> {
    List<CorporateBill> findByPartyIdAndActiveTrueOrderByCreatedAtDesc(Long partyId);

    Optional<CorporateBill> findByPartyIdAndConsumerNoAndActiveTrue(Long partyId, String consumerNo);

    @Query("select distinct b.partyId from CorporateBill b where b.active = true")
    List<Long> findDistinctActivePartyIds();
}
