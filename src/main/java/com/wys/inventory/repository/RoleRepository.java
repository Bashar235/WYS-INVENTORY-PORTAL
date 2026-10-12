package com.wys.inventory.repository;

import com.wys.inventory.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleCode(String roleCode);

    /**
     * Fetch all roles with their menus and actions eagerly in one pass.
     * Uses two separate LEFT JOIN FETCH queries (Hibernate requires it for
     * multiple bag/set collections to avoid a MultipleBagFetchException).
     */
    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.menus")
    List<Role> findAllWithMenus();

    /**
     * Eagerly fetch a single role with its menus and actions.
     * Used by AccessControlService to avoid LazyInitializationException.
     */
    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.menus LEFT JOIN FETCH r.actions WHERE r.roleCode = :roleCode")
    Optional<Role> findByRoleCodeWithPermissions(String roleCode);
}
