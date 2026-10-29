package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.BranchZoneRule;

import java.util.List;

public interface BranchZoneRuleRepository extends JpaRepository<BranchZoneRule, Long> {

    List<BranchZoneRule> findByBranchZoneIdAndBranchIdOrderByIdAsc(Long branchZoneId, Long branchId);

    @Modifying
    @Query("DELETE FROM BranchZoneRule r WHERE r.branchZone.id = :zoneId AND r.branch.id = :branchId")
    int deleteByZoneAndBranch(@Param("zoneId") Long branchZoneId, @Param("branchId") Long branchId);
}
