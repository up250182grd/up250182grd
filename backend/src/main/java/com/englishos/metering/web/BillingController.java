package com.englishos.metering.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.api.LearnerProfiles;
import com.englishos.metering.application.BillingAdminService;
import com.englishos.metering.application.BillingAdminService.FxView;
import com.englishos.metering.application.BillingAdminService.ModelView;
import com.englishos.metering.application.BillingAdminService.PolicyView;
import com.englishos.metering.application.BillingAdminService.PricingView;
import com.englishos.metering.application.BillingAdminService.RoleTotals;
import com.englishos.metering.application.BillingAdminService.UsageItem;
import com.englishos.metering.domain.ChargeCalculator;
import com.englishos.wallet.api.WalletApi;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class BillingController {

    private final BillingAdminService billing;
    private final WalletApi wallet;
    private final LearnerProfiles profiles;

    BillingController(BillingAdminService billing, WalletApi wallet, LearnerProfiles profiles) {
        this.billing = billing;
        this.wallet = wallet;
        this.profiles = profiles;
    }

    record CreateModelRequest(@NotBlank String provider, @NotBlank @Size(max = 100) String modelCode,
            @NotBlank @Pattern(regexp = "TEXT|REALTIME") String modality, @NotBlank @Size(max = 120) String displayName) {
    }

    record PricingRequest(@NotBlank @Pattern(regexp = "USD|MXN") String currency,
            @NotEmpty Map<String, BigDecimal> ratesPerMillion, Instant effectiveFrom,
            @NotBlank @Size(max = 500) String sourceUrl, @NotNull LocalDate verifiedAt) {
    }

    record PolicyRequest(@NotBlank String roleCode, @NotNull BigDecimal factor, @NotNull ChargeCalculator.TaxMode taxMode,
            BigDecimal taxRate, Instant effectiveFrom, @NotBlank @Size(max = 500) String note) {
    }

    record FxRequest(@NotBlank String base, @NotNull BigDecimal rate, @NotNull LocalDate date) {
    }

    record ResolveIncidentRequest(@NotBlank @Size(max = 500) String note) {
    }

    @PostMapping("/api/v1/admin/billing/incidents/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resolveIncident(@AuthenticationPrincipal CurrentUser actor, @PathVariable Long id,
            @Valid @RequestBody ResolveIncidentRequest req) {
        billing.resolveIncident(actor, id, req.note());
    }

    @GetMapping(path = "/api/v1/admin/reports/settlements.csv", produces = "text/csv")
    org.springframework.http.ResponseEntity<byte[]> settlementsCsv(@AuthenticationPrincipal CurrentUser actor,
            @RequestParam(required = false) String month) {
        var p = com.englishos.shared.web.CsvResponses.Period.of(month, profiles.zoneOf(actor.id()));
        return com.englishos.shared.web.CsvResponses.csv("liquidaciones-" + p.month() + ".csv",
                billing.settlementsCsv(p.from(), p.to()));
    }

    record FinanceSummary(String month, List<RoleTotals> totalsByRole, Map<String, BigDecimal> businessAccounts,
            long pendingReconciliations, List<WalletApi.IncidentView> openIncidents, List<String> ledgerProblems,
            String note) {
    }

    // ---- Usuario --------------------------------------------------------------------------------

    @GetMapping("/api/v1/me/usage")
    List<UsageItem> myUsage(@AuthenticationPrincipal CurrentUser user, @RequestParam(defaultValue = "50") int limit) {
        return billing.usageOf(user.id(), limit);
    }

    // ---- Administración -------------------------------------------------------------------------

    @GetMapping("/api/v1/admin/billing/models")
    List<ModelView> models() {
        return billing.models();
    }

    @PostMapping("/api/v1/admin/billing/models")
    @ResponseStatus(HttpStatus.CREATED)
    ModelView createModel(@AuthenticationPrincipal CurrentUser actor, @Valid @RequestBody CreateModelRequest req) {
        return billing.createModel(actor, req.provider(), req.modelCode(), req.modality(), req.displayName());
    }

    @PostMapping("/api/v1/admin/billing/models/{modelId}/pricing")
    @ResponseStatus(HttpStatus.CREATED)
    PricingView addPricing(@AuthenticationPrincipal CurrentUser actor, @PathVariable Long modelId,
            @Valid @RequestBody PricingRequest req) {
        return billing.addPricing(actor, modelId, req.currency(), req.ratesPerMillion(), req.effectiveFrom(),
                req.sourceUrl(), req.verifiedAt());
    }

    @GetMapping("/api/v1/admin/billing/policies")
    List<PolicyView> policies() {
        return billing.policies();
    }

    @PostMapping("/api/v1/admin/billing/policies")
    @ResponseStatus(HttpStatus.CREATED)
    PolicyView createPolicy(@AuthenticationPrincipal CurrentUser actor, @Valid @RequestBody PolicyRequest req) {
        return billing.createPolicy(actor, req.roleCode(), req.factor(), req.taxMode(), req.taxRate(),
                req.effectiveFrom(), req.note());
    }

    @GetMapping("/api/v1/admin/billing/fx-rates")
    List<FxView> fxRates() {
        return billing.fxRates();
    }

    @PostMapping("/api/v1/admin/billing/fx-rates")
    @ResponseStatus(HttpStatus.CREATED)
    FxView addFx(@AuthenticationPrincipal CurrentUser actor, @Valid @RequestBody FxRequest req) {
        return billing.addFxRate(actor, req.base(), req.rate(), req.date());
    }

    /** "Mi consumo": sesiones del administrador al costo directo, sin recargo (RF-ADM-5). */
    @GetMapping("/api/v1/admin/billing/my-usage")
    List<UsageItem> adminUsage(@AuthenticationPrincipal CurrentUser actor) {
        return billing.usageOf(actor.id(), 100);
    }

    @GetMapping("/api/v1/admin/billing/summary")
    FinanceSummary summary(@AuthenticationPrincipal CurrentUser actor, @RequestParam(required = false) String month) {
        ZoneId zone = profiles.zoneOf(actor.id());
        YearMonth ym = month == null ? YearMonth.now(zone) : YearMonth.parse(month);
        Instant from = ym.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        return new FinanceSummary(ym.toString(), billing.totalsByRole(from, to), wallet.businessBalances(),
                billing.pendingReconciliations(), wallet.openIncidents(), wallet.verifyLedger(),
                "Ingreso bruto adicional = recargo cobrado. La utilidad neta requiere restar las comisiones de la "
                        + "pasarela (aún no se registran automáticamente), los impuestos y la infraestructura.");
    }
}
