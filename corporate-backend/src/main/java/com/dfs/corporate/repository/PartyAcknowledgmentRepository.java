package com.dfs.corporate.repository;

import com.dfs.corporate.domain.PartyAcknowledgment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PartyAcknowledgmentRepository extends JpaRepository<PartyAcknowledgment, Long> {
    Optional<PartyAcknowledgment> findByPartyId(Long partyId);
    Optional<PartyAcknowledgment> findByToken(String token);
}
