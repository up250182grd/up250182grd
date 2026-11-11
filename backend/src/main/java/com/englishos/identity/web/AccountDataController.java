package com.englishos.identity.web;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.application.AccountDataService;
import com.englishos.identity.application.AccountDataService.DeletionResult;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Derechos del titular: descargar mis datos y eliminar mi cuenta. */
@RestController
class AccountDataController {

    private final AccountDataService account;

    AccountDataController(AccountDataService account) {
        this.account = account;
    }

    record DeleteAccountRequest(@NotBlank @Size(max = 200) String password, @NotBlank @Size(max = 20) String confirmation,
            Boolean acceptBalanceLoss) {
    }

    @GetMapping("/api/v1/me/data-export")
    ResponseEntity<Map<String, Object>> export(@AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("mis-datos-englishos.json").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(account.export(user));
    }

    @PostMapping("/api/v1/me/account/delete")
    DeletionResult delete(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody DeleteAccountRequest req,
            HttpServletRequest http) {
        DeletionResult result = account.delete(user, req.password(), req.confirmation(),
                Boolean.TRUE.equals(req.acceptBalanceLoss()));
        // Cierra también la sesión de esta petición.
        var session = http.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return result;
    }
}
