package com.englishos.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.json.JsonMapper;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * API pública del módulo de auditoría. Se ejecuta dentro de la transacción del llamador (MANDATORY):
 * si la acción auditada se revierte, su registro de auditoría también, y viceversa.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    AuditService(AuditLogRepository repository, JsonMapper jsonMapper, Clock clock) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long actorUserId, String action, String targetType, String targetId,
            Map<String, ?> before, Map<String, ?> after) {
        repository.save(new AuditLog(actorUserId, action, targetType, targetId, toJson(before), toJson(after),
                Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> findByTarget(String targetType, String targetId) {
        return repository.findByTargetTypeAndTargetIdOrderByIdAsc(targetType, targetId).stream()
                .map(a -> new AuditEntry(a.getId(), a.getActorUserId(), a.getAction(), a.getTargetType(),
                        a.getTargetId(), a.getBeforeState(), a.getAfterState(), a.getCreatedAt()))
                .toList();
    }

    private String toJson(Map<String, ?> state) {
        return state == null ? null : jsonMapper.writeValueAsString(state);
    }

    public record AuditEntry(Long id, Long actorUserId, String action, String targetType, String targetId,
            String beforeState, String afterState, Instant createdAt) {
    }
}
