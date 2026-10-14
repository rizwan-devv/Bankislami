package com.dfs.corporate.repository;

import com.dfs.corporate.domain.BranchBillAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BranchBillActionRepository extends JpaRepository<BranchBillAction, Long> {
    List<BranchBillAction> findByBranchAccountIdOrderByCreatedAtDesc(Long branchAccountId);
}
