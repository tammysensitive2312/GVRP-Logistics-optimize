package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.BranchZone;
import org.truong.gvrp_entry_api.entity.enums.BranchZoneMode;

import java.util.List;
import java.util.Optional;

/** Branch-owned zones; every lookup is scoped to a branch (branch_id is NOT NULL). */
public interface BranchZoneRepository extends JpaRepository<BranchZone, Long> {

    List<BranchZone> findByBranchIdOrderByIdAsc(Long branchId);

    /** Another branch's zone answers like a missing one. */
    Optional<BranchZone> findByIdAndBranchId(Long id, Long branchId);

    boolean existsByBranchIdAndSourceTemplateIdAndMode(Long branchId, Long sourceTemplateId, BranchZoneMode mode);
}
