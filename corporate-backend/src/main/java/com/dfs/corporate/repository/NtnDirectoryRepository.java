package com.dfs.corporate.repository;

import com.dfs.corporate.domain.NtnDirectoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NtnDirectoryRepository extends JpaRepository<NtnDirectoryEntry, Long> {
    Optional<NtnDirectoryEntry> findByNtnNumberIgnoreCase(String ntnNumber);
}
