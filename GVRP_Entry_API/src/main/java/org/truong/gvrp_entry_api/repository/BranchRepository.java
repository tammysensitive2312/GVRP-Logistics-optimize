package org.truong.gvrp_entry_api.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.truong.gvrp_entry_api.entity.Branch;

import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {

    /**
     * Find branch by name
     * @param name Branch name
     * @return Optional Branch
     */
    Optional<Branch> findByName(String name);

    /**
     * Check if branch exists by name
     * @param name Branch name
     * @return true if exists
     */
    boolean existsByName(String name);
}