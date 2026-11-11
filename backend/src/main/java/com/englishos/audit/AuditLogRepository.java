package com.englishos.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTargetTypeAndTargetIdOrderByIdAsc(String targetType, String targetId);

    /** Búsqueda con filtros opcionales (null = sin filtro), de lo más reciente a lo más antiguo. */
    @org.springframework.data.jpa.repository.Query("""
            select a from AuditLog a
            where (:action is null or a.action = :action)
              and (:targetType is null or a.targetType = :targetType)
              and (:targetId is null or a.targetId = :targetId)
              and (:actor is null or a.actorUserId = :actor)
              and (:before is null or a.id < :before)
            order by a.id desc""")
    List<AuditLog> search(@org.springframework.data.repository.query.Param("action") String action,
            @org.springframework.data.repository.query.Param("targetType") String targetType,
            @org.springframework.data.repository.query.Param("targetId") String targetId,
            @org.springframework.data.repository.query.Param("actor") Long actor,
            @org.springframework.data.repository.query.Param("before") Long before,
            org.springframework.data.domain.Pageable page);

    @org.springframework.data.jpa.repository.Query("select distinct a.action from AuditLog a order by a.action")
    List<String> distinctActions();
}
