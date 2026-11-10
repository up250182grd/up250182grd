package com.englishos.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTargetTypeAndTargetIdOrderByIdAsc(String targetType, String targetId);
}
