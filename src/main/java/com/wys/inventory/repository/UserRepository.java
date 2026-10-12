package com.wys.inventory.repository;

import com.wys.inventory.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :term, '%')) OR CAST(u.id AS string) LIKE CONCAT('%', :term, '%') ORDER BY u.username")
    List<User> searchUsers(String term);

    long countByRole_RoleCode(String roleCode);
}
