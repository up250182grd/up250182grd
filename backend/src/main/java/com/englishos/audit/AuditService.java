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

    public record AuditPage(List<AuditEntry> items, Long nextBefore, List<String> actions) {
    }

    /** Bitácora consultable (Fase 7). Paginación por cursor: {@code before} = id del último registro visto. */
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('audit:read')")
    @Transactional(readOnly = true)
    public AuditPage search(String action, String targetType, String targetId, Long actor, Long before, int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        List<AuditEntry> items = repository.search(blankToNull(action), blankToNull(targetType),
                        blankToNull(targetId), actor, before, org.springframework.data.domain.PageRequest.of(0, size + 1))
                .stream()
                .map(a -> new AuditEntry(a.getId(), a.getActorUserId(), a.getAction(), a.getTargetType(),
                        a.getTargetId(), a.getBeforeState(), a.getAfterState(), a.getCreatedAt()))
                .toList();
        boolean more = items.size() > size;
        List<AuditEntry> page = more ? items.subList(0, size) : items;
        return new AuditPage(page, more ? page.getLast().id() : null, repository.distinctActions());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private String toJson(Map<String, ?> state) {
        return state == null ? null : jsonMapper.writeValueAsString(state);
    }

    public record AuditEntry(Long id, Long actorUserId, String action, String targetType, String targetId,
            String beforeState, String afterState, Instant createdAt) {
    }
}
