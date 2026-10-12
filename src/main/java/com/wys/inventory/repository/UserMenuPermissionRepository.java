package com.wys.inventory.repository;

import com.wys.inventory.model.UserMenuPermission;
import com.wys.inventory.model.UserMenuPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserMenuPermissionRepository
        extends JpaRepository<UserMenuPermission, UserMenuPermissionId> {

    List<UserMenuPermission> findByUserId(Long userId);

    void deleteByUserIdAndMenuId(Long userId, Long menuId);
}
