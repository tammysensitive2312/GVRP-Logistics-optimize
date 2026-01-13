package org.truong.gvrp_entry_api.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.truong.gvrp_entry_api.entity.User;
import org.truong.gvrp_entry_api.entity.enums.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username
     * @param username Username
     * @return Optional User
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email
     * @param email Email address
     * @return Optional User
     */
    Optional<User> findByEmail(String email);

    /**
     * Find all users by branch ID
     * @param branchId Branch ID
     * @return List of users
     */
    List<User> findByBranchId(Long branchId);

    /**
     * Find users by branch ID and role
     * @param branchId Branch ID
     * @param role User role
     * @return List of users
     */
    List<User> findByBranchIdAndRole(Long branchId, UserRole role);

    /**
     * Check if username exists
     * @param username Username
     * @return true if exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists
     * @param email Email address
     * @return true if exists
     */
    boolean existsByEmail(String email);

    /**
     * Find user by username and branch ID
     * @param username Username
     * @param branchId Branch ID
     * @return Optional User
     */
    @Query("SELECT u FROM User u JOIN FETCH u.branch WHERE u.username = :username AND u.branch.id = :branchId")
    Optional<User> findByUsernameAndBranchId(@Param("username") String username, @Param("branchId") Long branchId);
}
