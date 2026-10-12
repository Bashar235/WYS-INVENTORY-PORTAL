package com.wys.inventory.repository;

import com.wys.inventory.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByProductIdOrderByTimestampDesc(Long productId);
    List<AuditLog> findTop10ByOrderByTimestampDesc();
}
