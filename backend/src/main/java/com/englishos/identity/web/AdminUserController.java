package com.englishos.identity.web;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.application.AdminUserService;
import com.englishos.identity.application.AdminUserService.AdminUserView;
import com.englishos.identity.domain.RoleCode;
import com.englishos.identity.domain.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController {

    private final AdminUserService service;

    AdminUserController(AdminUserService service) {
        this.service = service;
    }

    record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {
        static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                    p.getTotalPages());
        }
    }

    record ChangeRolesRequest(@NotEmpty Set<RoleCode> roles) {
    }

    @GetMapping
    PageResponse<AdminUserView> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.clamp(size, 1, 100);
        int safePage = Math.max(page, 0);
        return PageResponse.of(service.search(q, status,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PostMapping("/{publicId}/suspend")
    AdminUserView suspend(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId) {
        return service.suspend(actor, publicId);
    }

    @PostMapping("/{publicId}/reactivate")
    AdminUserView reactivate(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId) {
        return service.reactivate(actor, publicId);
    }

    @PutMapping("/{publicId}/roles")
    AdminUserView changeRoles(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId,
            @Valid @RequestBody ChangeRolesRequest req) {
        return service.changeRoles(actor, publicId, req.roles());
    }
}
