package com.englishos.metering.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.json.JsonMapper;

import com.englishos.audit.AuditService;
import com.englishos.identity.api.CurrentUser;
import com.englishos.metering.api.MeteringApi.SettlementView;
import com.englishos.metering.domain.AiModel;
import com.englishos.metering.domain.ChargeCalculator;
import com.englishos.metering.domain.CommercialPolicy;
import com.englishos.metering.domain.FxRate;
import com.englishos.metering.domain.PricingVersion;
import com.englishos.metering.domain.UsageSettlement;
import com.englishos.metering.infrastructure.AiModelRepository;
import com.englishos.metering.infrastructure.CommercialPolicyRepository;
import com.englishos.metering.infrastructure.FxRateRepository;
import com.englishos.metering.infrastructure.PricingVersionRepository;
import com.englishos.metering.infrastructure.UsageSettlementRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuración económica protegida (RF-ADM-3, RF-ADM-4, RF-ADM-6): modelos, tarifas versionadas, factores
 * comerciales y tipos de cambio. Nada se edita en el lugar: cada cambio crea una versión nueva, con vigencia
 * no retroactiva, y queda en la auditoría.
 */
@Service
public class BillingAdminService {

    static final Set<String> UNIT_TYPES = Set.of("text_in", "text_in_cached", "text_out", "audio_in",
            "audio_in_cached", "audio_out");
    private static final Set<String> PROVIDERS = Set.of("OPENAI");

    private final AiModelRepository models;
    private final PricingVersionRepository pricing;
    private final CommercialPolicyRepository policies;
    private final FxRateRepository fxRates;
    private final UsageSettlementRepository settlements;
    private final MeteringService metering;
    private final AuditService audit;
    private final JsonMapper json;
    private final Clock clock;
    private final com.englishos.wallet.api.WalletApi wallet;

    BillingAdminService(AiModelRepository models, PricingVersionRepository pricing, CommercialPolicyRepository policies,
            FxRateRepository fxRates, UsageSettlementRepository settlements, MeteringService metering,
            AuditService audit, JsonMapper json, Clock clock, com.englishos.wallet.api.WalletApi wallet) {
        this.wallet = wallet;
        this.models = models;
        this.pricing = pricing;
        this.policies = policies;
        this.fxRates = fxRates;
        this.settlements = settlements;
        this.metering = metering;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    public record PricingView(Long id, String currency, Map<String, BigDecimal> ratesPerMillion, Instant effectiveFrom,
            String sourceUrl, LocalDate verifiedAt) {
    }

    public record ModelView(Long id, String provider, String modelCode, String modality, String displayName,
            boolean enabled, boolean simulated, PricingView currentPricing, List<PricingView> history) {
    }

    public record PolicyView(Long id, String roleCode, BigDecimal factor, String taxMode, BigDecimal taxRate,
            Instant effectiveFrom, String note, boolean current) {
    }

    public record FxView(Long id, String base, String quote, BigDecimal rate, String source, LocalDate rateDate) {
    }

    public record UsageItem(String settlementId, String subjectType, Long subjectId, Instant settledAt,
            SettlementView detail) {
    }

    public record RoleTotals(String roleCode, long settlements, BigDecimal directCostMxn, BigDecimal markupMxn,
            BigDecimal taxMxn, BigDecimal totalMxn) {
    }

    // ---- Modelos y tarifas --------------------------------------------------------------------

    @PreAuthorize("hasAuthority('billing:read:any')")
    @Transactional(readOnly = true)
    public List<ModelView> models() {
        Instant now = Instant.now(clock);
        return models.findAll().stream().map(m -> {
            List<PricingView> history = pricing.findByModelIdOrderByEffectiveFromDescIdDesc(m.getId()).stream()
                    .map(this::view).toList();
            PricingView current = history.stream().filter(p -> !p.effectiveFrom().isAfter(now)).findFirst()
                    .orElse(null);
            return new ModelView(m.getId(), m.getProvider(), m.getModelCode(), m.getModality(), m.getDisplayName(),
                    m.isEnabled(), m.isSimulated(), current, history);
        }).toList();
    }

    @PreAuthorize("hasAuthority('pricing:write')")
    @Transactional
    public ModelView createModel(CurrentUser actor, String provider, String modelCode, String modality,
            String displayName) {
        if (!PROVIDERS.contains(provider)) {
            throw ApiException.badRequest("UNSUPPORTED_PROVIDER", "Proveedor no soportado: " + provider);
        }
        if (models.findByProviderAndModelCode(provider, modelCode).isPresent()) {
            throw ApiException.conflict("MODEL_EXISTS", "El modelo ya está registrado.");
        }
        AiModel m = models.save(AiModel.create(provider, modelCode.trim(), modality, displayName.trim(),
                Instant.now(clock)));
        audit.record(actor.id(), "AI_MODEL_CREATED", "AI_MODEL", String.valueOf(m.getId()), null,
                Map.of("provider", provider, "model", modelCode));
        return models().stream().filter(v -> v.id().equals(m.getId())).findFirst().orElseThrow();
    }

    @PreAuthorize("hasAuthority('pricing:write')")
    @Transactional
    public PricingView addPricing(CurrentUser actor, Long modelId, String currency, Map<String, BigDecimal> rates,
            Instant effectiveFrom, String sourceUrl, LocalDate verifiedAt) {
        AiModel model = models.findById(modelId)
                .orElseThrow(() -> ApiException.notFound("MODEL_NOT_FOUND", "Modelo no encontrado."));
        if (model.isSimulated()) {
            throw ApiException.badRequest("SIMULATED_MODEL", "Las tarifas del proveedor simulado no se modifican.");
        }
        Instant now = Instant.now(clock);
        if (rates == null || rates.isEmpty() || !UNIT_TYPES.containsAll(rates.keySet())
                || rates.values().stream().anyMatch(r -> r == null || r.signum() < 0)) {
            throw ApiException.badRequest("INVALID_RATES", "Tarifas inválidas. Tipos permitidos: " + UNIT_TYPES);
        }
        if (sourceUrl == null || !sourceUrl.startsWith("https://")) {
            throw ApiException.badRequest("SOURCE_REQUIRED",
                    "Indica la URL oficial (https://) de la que tomaste la tarifa (RN-9).");
        }
        if (verifiedAt == null || verifiedAt.isAfter(LocalDate.now(clock))) {
            throw ApiException.badRequest("INVALID_VERIFIED_AT", "La fecha de verificación no puede ser futura.");
        }
        Instant from = effectiveFrom == null || effectiveFrom.isBefore(now) ? now : effectiveFrom;
        PricingVersion v = pricing.save(PricingVersion.create(modelId, currency, json.writeValueAsString(
                new LinkedHashMap<>(rates)), from, sourceUrl, verifiedAt, actor.id(), now));
        audit.record(actor.id(), "PRICING_VERSION_CREATED", "AI_MODEL", String.valueOf(modelId), null,
                Map.of("pricingVersion", v.getId(), "currency", currency, "rates", rates, "source", sourceUrl));
        return view(v);
    }

    // ---- Política comercial -------------------------------------------------------------------

    @PreAuthorize("hasAuthority('billing:read:any')")
    @Transactional(readOnly = true)
    public List<PolicyView> policies() {
        Instant now = Instant.now(clock);
        List<PolicyView> out = new ArrayList<>();
        for (String role : List.of("ADMIN", "STUDENT")) {
            Long currentId = metering.currentPolicy(role, now).getId();
            policies.findAllByOrderByRoleCodeAscEffectiveFromDescIdDesc().stream()
                    .filter(p -> p.getRoleCode().equals(role))
                    .forEach(p -> out.add(new PolicyView(p.getId(), p.getRoleCode(), p.getFactor(), p.getTaxMode().name(),
                            p.getTaxRate(), p.getEffectiveFrom(), p.getNote(), p.getId().equals(currentId))));
        }
        return out;
    }

    /** RN-3: nueva versión con vigencia ≥ ahora; las operaciones ya liquidadas conservan su factor. */
    @PreAuthorize("hasAuthority('pricing:write')")
    @Transactional
    public PolicyView createPolicy(CurrentUser actor, String roleCode, BigDecimal factor,
            ChargeCalculator.TaxMode taxMode, BigDecimal taxRate, Instant effectiveFrom, String note) {
        if (!Set.of("ADMIN", "STUDENT").contains(roleCode)) {
            throw ApiException.badRequest("INVALID_ROLE", "Rol no válido.");
        }
        if (factor == null || factor.compareTo(BigDecimal.ONE) < 0 || factor.compareTo(new BigDecimal("5")) > 0
                || factor.scale() > 4) {
            throw ApiException.badRequest("INVALID_FACTOR", "El factor debe estar entre 1.0000 y 5.0000.");
        }
        BigDecimal rate = taxMode == ChargeCalculator.TaxMode.NONE ? BigDecimal.ZERO : taxRate;
        if (rate == null || rate.signum() < 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
            throw ApiException.badRequest("INVALID_TAX_RATE", "La tasa de impuesto debe estar entre 0 y 1.");
        }
        if (note == null || note.isBlank()) {
            throw ApiException.badRequest("NOTE_REQUIRED", "Explica el motivo del cambio (queda en la auditoría).");
        }
        Instant now = Instant.now(clock);
        Instant from = effectiveFrom == null || effectiveFrom.isBefore(now) ? now : effectiveFrom;
        CommercialPolicy before = metering.currentPolicy(roleCode, now);
        CommercialPolicy p = policies.save(CommercialPolicy.create(roleCode, factor.setScale(4), taxMode,
                rate.setScale(4, java.math.RoundingMode.HALF_UP), from, note.trim(), actor.id(), now));
        audit.record(actor.id(), "COMMERCIAL_POLICY_CREATED", "COMMERCIAL_POLICY", roleCode,
                Map.of("factor", before.getFactor().toPlainString(), "taxMode", before.getTaxMode().name()),
                Map.of("factor", p.getFactor().toPlainString(), "taxMode", p.getTaxMode().name(),
                        "effectiveFrom", p.getEffectiveFrom().toString(), "note", p.getNote()));
        return new PolicyView(p.getId(), roleCode, p.getFactor(), p.getTaxMode().name(), p.getTaxRate(),
                p.getEffectiveFrom(), p.getNote(), !from.isAfter(now));
    }

    // ---- Tipos de cambio ----------------------------------------------------------------------

    @PreAuthorize("hasAuthority('billing:read:any')")
    @Transactional(readOnly = true)
    public List<FxView> fxRates() {
        return fxRates.findTop30ByOrderByRateDateDescIdDesc().stream()
                .map(f -> new FxView(f.getId(), f.getBase(), "MXN", f.getRate(), f.getSource(), f.getRateDate()))
                .toList();
    }

    @PreAuthorize("hasAuthority('pricing:write')")
    @Transactional
    public FxView addFxRate(CurrentUser actor, String base, BigDecimal rate, LocalDate date) {
        if (!"USD".equals(base)) {
            throw ApiException.badRequest("UNSUPPORTED_CURRENCY", "Por ahora solo se admite USD/MXN.");
        }
        if (rate == null || rate.signum() <= 0 || rate.scale() > 6 || date == null
                || date.isAfter(LocalDate.now(clock))) {
            throw ApiException.badRequest("INVALID_FX", "Tipo de cambio o fecha inválidos.");
        }
        try {
            FxRate f = fxRates.saveAndFlush(FxRate.manual(base, rate, date, actor.id(), Instant.now(clock)));
            audit.record(actor.id(), "FX_RATE_CREATED", "FX_RATE", base + "/MXN", null,
                    Map.of("rate", rate.toPlainString(), "date", date.toString(), "source", "MANUAL"));
            return new FxView(f.getId(), base, "MXN", f.getRate(), f.getSource(), f.getRateDate());
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw ApiException.conflict("FX_EXISTS", "Ya hay un tipo de cambio manual para esa fecha.");
        }
    }

    // ---- Consumo e ingresos -------------------------------------------------------------------

    /** Consumo de un usuario. El propio usuario puede verlo; el desglose de costo lo ven todos (transparencia). */
    @Transactional(readOnly = true)
    public List<UsageItem> usageOf(Long userId, int limit) {
        return settlements.findRecentByUser(userId, PageRequest.of(0, Math.clamp(limit, 1, 200))).stream()
                .map(s -> new UsageItem(s.getPublicId(), s.getSubjectType(), s.getSubjectId(),
                        s.getSettledAt() == null ? s.getCreatedAt() : s.getSettledAt(), MeteringService.view(s)))
                .toList();
    }

    @PreAuthorize("hasAuthority('billing:read:any')")
    @Transactional(readOnly = true)
    public List<RoleTotals> totalsByRole(Instant from, Instant to) {
        return settlements.totalsByRole(from, to).stream()
                .map(r -> new RoleTotals((String) r[0], (Long) r[1], (BigDecimal) r[2], (BigDecimal) r[3],
                        (BigDecimal) r[4], (BigDecimal) r[5]))
                .toList();
    }

    /** Liquidaciones del periodo (incluye las pendientes de conciliación, marcadas en "estado"). */
    @PreAuthorize("hasAuthority('billing:read:any')")
    @Transactional(readOnly = true)
    public String settlementsCsv(Instant from, Instant to) {
        StringBuilder sb = new StringBuilder(com.englishos.shared.text.Csv.BOM);
        sb.append(com.englishos.shared.text.Csv.row(List.of("liquidada_utc", "liquidacion", "usuario_id", "rol",
                "sujeto", "sujeto_id", "estado", "costo_proveedor", "moneda_proveedor", "tipo_cambio",
                "fuente_tipo_cambio", "costo_directo_mxn", "factor", "recargo_mxn", "impuestos_mxn", "total_mxn",
                "nota")));
        for (UsageSettlement s : settlements.findBySettledAtGreaterThanEqualAndSettledAtLessThanOrderByIdAsc(from, to)) {
            sb.append(com.englishos.shared.text.Csv.row(java.util.Arrays.asList(s.getSettledAt(), s.getPublicId(),
                    s.getUserId(), s.getRoleCode(), s.getSubjectType(), s.getSubjectId(), s.getStatus().name(),
                    s.getProviderCost(), s.getProviderCurrency(), s.getFxRate(), s.getFxSource(), s.getDirectCostMxn(),
                    s.getFactorApplied(), s.getMarkupMxn(), s.getTaxMxn(), s.getTotalMxn(), s.getNote())));
        }
        return sb.toString();
    }

    /** Cierra una incidencia de facturación con su motivo (queda auditado). */
    @PreAuthorize("hasAuthority('payments:refund')")
    @Transactional
    public void resolveIncident(CurrentUser actor, Long incidentId, String note) {
        if (note == null || note.strip().length() < 5 || note.length() > 500) {
            throw ApiException.badRequest("NOTE_REQUIRED", "Explica cómo se resolvió (5 a 500 caracteres).");
        }
        if (!wallet.resolveIncident(incidentId, note.strip(), actor.id())) {
            throw ApiException.notFound("INCIDENT_NOT_FOUND", "La incidencia no existe o ya estaba resuelta.");
        }
    }

    @PreAuthorize("hasAuthority('billing:read:any')")
    @Transactional(readOnly = true)
    public long pendingReconciliations() {
        return settlements.countByStatus(UsageSettlement.Status.PENDING_RECONCILIATION);
    }

    private PricingView view(PricingVersion v) {
        return new PricingView(v.getId(), v.getCurrency(), metering.rates(v), v.getEffectiveFrom(), v.getSourceUrl(),
                v.getVerifiedAt());
    }
}
