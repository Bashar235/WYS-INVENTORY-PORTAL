package com.wys.inventory.repository;

import com.wys.inventory.model.UserActionPermission;
import com.wys.inventory.model.UserActionPermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserActionPermissionRepository
        extends JpaRepository<UserActionPermission, UserActionPermissionId> {

    List<UserActionPermission> findByUserId(Long userId);

    void deleteByUserIdAndActionId(Long userId, Long actionId);
}
