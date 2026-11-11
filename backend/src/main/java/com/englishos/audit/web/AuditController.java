package com.englishos.audit.web;

import com.englishos.audit.AuditService;
import com.englishos.audit.AuditService.AuditPage;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Bitácora de auditoría consultable por el administrador (solo lectura). */
@RestController
class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/api/v1/admin/audit")
    AuditPage search(@RequestParam(required = false) String action, @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId, @RequestParam(required = false) Long actor,
            @RequestParam(required = false) Long before, @RequestParam(defaultValue = "50") int limit) {
        return audit.search(action, targetType, targetId, actor, before, limit);
    }
}
