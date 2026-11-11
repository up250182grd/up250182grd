package com.englishos.wallet.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.api.LearnerProfiles;
import com.englishos.wallet.application.WalletService;
import com.englishos.wallet.application.WalletService.Movement;
import com.englishos.wallet.application.WalletService.WalletSummary;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/wallet")
class WalletController {

    private final WalletService wallet;
    private final LearnerProfiles profiles;

    WalletController(WalletService wallet, LearnerProfiles profiles) {
        this.wallet = wallet;
        this.profiles = profiles;
    }

    record TopupRequest(@NotNull @Digits(integer = 7, fraction = 2) BigDecimal amount, @NotBlank String idempotencyKey) {
    }

    record PreferencesRequest(
            @DecimalMin("0") @Digits(integer = 7, fraction = 2) BigDecimal monthlyLimit,
            @NotNull @DecimalMin("0") @Digits(integer = 7, fraction = 2) BigDecimal lowBalanceThreshold) {
    }

    @GetMapping
    WalletSummary summary(@AuthenticationPrincipal CurrentUser user) {
        return wallet.summary(user.id(), profiles.zoneOf(user.id()));
    }

    @GetMapping("/movements")
    List<Movement> movements(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return wallet.movements(user.id(), Math.max(page, 0), Math.clamp(size, 1, 100));
    }

    @PostMapping("/simulated-topups")
    WalletSummary simulatedTopup(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody TopupRequest req) {
        return wallet.simulatedTopup(user.id(), req.amount(), req.idempotencyKey(), profiles.zoneOf(user.id()));
    }

    @PutMapping("/preferences")
    WalletSummary preferences(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody PreferencesRequest req) {
        return wallet.updatePreferences(user.id(), req.monthlyLimit(), req.lowBalanceThreshold(),
                profiles.zoneOf(user.id()));
    }
}
