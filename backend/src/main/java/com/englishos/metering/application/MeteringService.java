package com.englishos.metering.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.englishos.metering.api.MeteringApi;
import com.englishos.metering.domain.AiModel;
import com.englishos.metering.domain.ChargeCalculator;
import com.englishos.metering.domain.CommercialPolicy;
import com.englishos.metering.domain.FxRate;
import com.englishos.metering.domain.PricingVersion;
import com.englishos.metering.domain.UsageRecord;
import com.englishos.metering.domain.UsageSettlement;
import com.englishos.metering.infrastructure.AiModelRepository;
import com.englishos.metering.infrastructure.CommercialPolicyRepository;
import com.englishos.metering.infrastructure.FxRateRepository;
import com.englishos.metering.infrastructure.PricingVersionRepository;
import com.englishos.metering.infrastructure.UsageRecordRepository;
import com.englishos.metering.infrastructure.UsageSettlementRepository;
import com.englishos.shared.error.ApiException;
import com.englishos.wallet.api.WalletApi;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MeteringService implements MeteringApi {

    /** Antigüedad máxima del tipo de cambio aceptado (RN-14: ~5 días hábiles). */
    static final int FX_MAX_AGE_DAYS = 7;

    private final AiModelRepository models;
    private final PricingVersionRepository pricing;
    private final CommercialPolicyRepository policies;
    private final FxRateRepository fxRates;
    private final UsageRecordRepository usage;
    private final UsageSettlementRepository settlements;
    private final WalletApi wallet;
    private final JsonMapper json;
    private final Clock clock;

    MeteringService(AiModelRepository models, PricingVersionRepository pricing, CommercialPolicyRepository policies,
            FxRateRepository fxRates, UsageRecordRepository usage, UsageSettlementRepository settlements,
            WalletApi wallet, JsonMapper json, Clock clock) {
        this.models = models;
        this.pricing = pricing;
        this.policies = policies;
        this.fxRates = fxRates;
        this.usage = usage;
        this.settlements = settlements;
        this.wallet = wallet;
        this.json = json;
        this.clock = clock;
    }

    static String roleFor(boolean admin) {
        return admin ? "ADMIN" : "STUDENT";
    }

    @Override
    @Transactional(readOnly = true)
    public SessionPricing pricingFor(Long userId, boolean admin, String provider, String modelCode) {
        Instant now = Instant.now(clock);
        AiModel model = models.findByProviderAndModelCode(provider, modelCode)
                .filter(AiModel::isEnabled)
                .orElseThrow(() -> notConfigured("El modelo " + modelCode + " no está registrado o está desactivado."));
        PricingVersion version = pricing
                .findFirstByModelIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(model.getId(), now)
                .orElseThrow(() -> notConfigured("El modelo " + modelCode + " no tiene una tarifa vigente."));
        String role = roleFor(admin);
        CommercialPolicy policy = currentPolicy(role, now);
        return new SessionPricing(model.getId(), model.getProvider(), model.getModelCode(), model.isSimulated(),
                version.getId(), version.getCurrency(), rates(version), policy.getId(), role, policy.getFactor());
    }

    @Override
    @Transactional
    public BigDecimal recordUsage(UsageInput in) {
        String key = in.subjectType() + ":" + in.subjectId() + ":" + in.providerRequestId();
        Optional<UsageRecord> existing = usage.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return existing.get().getProviderCost(); // el mismo evento del proveedor nunca se cuenta dos veces
        }
        PricingVersion version = pricing.findById(in.pricingVersionId()).orElseThrow();
        BigDecimal cost;
        try {
            cost = ChargeCalculator.providerCost(in.units(), rates(version));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Uso no tarificable: " + ex.getMessage(), ex);
        }
        usage.save(UsageRecord.of(in.userId(), in.subjectType(), in.subjectId(), in.modelId(), version.getId(),
                in.providerRequestId(), json.writeValueAsString(new LinkedHashMap<>(in.units())), cost,
                version.getCurrency(), in.dataSource(), key, Instant.now(clock)));
        return cost;
    }

    @Override
    @Transactional(readOnly = true)
    public CostEstimate measuredSoFar(String subjectType, Long subjectId, Long pricingVersionId, Long policyId) {
        BigDecimal cost = usage.findBySubjectTypeAndSubjectId(subjectType, subjectId).stream()
                .map(UsageRecord::getProviderCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = pricing.findById(pricingVersionId).orElseThrow().getCurrency();
        return price(cost, currency, policies.findById(policyId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public CostEstimate estimate(Long pricingVersionId, Long policyId, Map<String, Long> units) {
        PricingVersion version = pricing.findById(pricingVersionId).orElseThrow();
        BigDecimal cost = ChargeCalculator.providerCost(units, rates(version));
        return price(cost, version.getCurrency(), policies.findById(policyId).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public ModelRef model(Long modelId) {
        AiModel m = models.findById(modelId).orElseThrow();
        return new ModelRef(m.getId(), m.getProvider(), m.getModelCode(), m.getDisplayName(), m.isSimulated());
    }

    @Override
    @Transactional
    public SettlementView settle(Long userId, String subjectType, Long subjectId, Long pricingVersionId,
            Long policyId) {
        Optional<UsageSettlement> existing = settlements.findBySubjectTypeAndSubjectId(subjectType, subjectId);
        if (existing.isPresent()) {
            return view(existing.get()); // idempotente: liquidar dos veces devuelve la misma liquidación
        }
        Instant now = Instant.now(clock);
        CommercialPolicy policy = policies.findById(policyId).orElseThrow();
        String roleCode = policy.getRoleCode();
        String currency = pricing.findById(pricingVersionId).orElseThrow().getCurrency();
        BigDecimal providerCost = usage.findBySubjectTypeAndSubjectId(subjectType, subjectId).stream()
                .map(UsageRecord::getProviderCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(ChargeCalculator.COST_SCALE);

        Optional<FxRate> fx = fxFor(currency);
        if (fx.isEmpty() && !"MXN".equals(currency)) {
            // RN-14/RN-17: sin tipo de cambio confiable no se inventa el cargo; queda pendiente y la reserva retenida.
            UsageSettlement pending = settlements.save(UsageSettlement.pending(userId, roleCode, subjectType, subjectId,
                    providerCost, currency, policy, "Sin tipo de cambio " + currency + "/MXN vigente.", now));
            wallet.openIncident("MISSING_FX_RATE", userId, subjectType, String.valueOf(subjectId),
                    "No hay tipo de cambio " + currency + "/MXN de los últimos " + FX_MAX_AGE_DAYS
                            + " días. Captúralo y concilia esta liquidación.");
            return view(pending);
        }
        BigDecimal rate = fx.map(FxRate::getRate).orElse(BigDecimal.ONE);
        String fxSource = fx.map(f -> f.getSource() + " " + f.getRateDate()).orElse("NO_APLICA (MXN)");
        ChargeCalculator.Charge charge = ChargeCalculator.charge(providerCost, rate, policy.getFactor(),
                policy.getTaxMode(), policy.getTaxRate());

        Long ledgerTx;
        if ("ADMIN".equals(roleCode)) {
            ledgerTx = wallet.recordInternalUsage(userId, subjectType, subjectId, charge.directCostMxn());
            wallet.settleCustomer(userId, subjectType, subjectId,
                    new WalletApi.ChargeLines(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        } else {
            ledgerTx = wallet.settleCustomer(userId, subjectType, subjectId, new WalletApi.ChargeLines(
                    charge.directCostMxn(), charge.markupMxn(), charge.taxMxn(), charge.totalMxn()))
                    .captureTransactionId();
        }
        return view(settlements.save(UsageSettlement.settled(userId, roleCode, subjectType, subjectId, providerCost,
                currency, rate, fxSource, policy, charge, ledgerTx, now)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SettlementView> settlementOf(String subjectType, Long subjectId) {
        return settlements.findBySubjectTypeAndSubjectId(subjectType, subjectId).map(MeteringService::view);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal settledThisMonth(Long userId, ZoneId zone) {
        Instant since = LocalDate.now(clock.withZone(zone)).withDayOfMonth(1).atStartOfDay(zone).toInstant();
        return settlements.totalSince(userId, since);
    }

    // ---- Apoyo --------------------------------------------------------------------------------

    CommercialPolicy currentPolicy(String role, Instant now) {
        return policies.findFirstByRoleCodeAndEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(role, now)
                .orElseThrow(() -> notConfigured("No hay política comercial vigente para el rol " + role + "."));
    }

    Optional<FxRate> fxFor(String currency) {
        if ("MXN".equals(currency)) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        return fxRates.findFirstByBaseAndQuoteAndRateDateBetweenOrderByRateDateDescIdDesc(currency, "MXN",
                today.minusDays(FX_MAX_AGE_DAYS), today);
    }

    private CostEstimate price(BigDecimal providerCost, String currency, CommercialPolicy policy) {
        Optional<FxRate> fx = fxFor(currency);
        if (fx.isEmpty() && !"MXN".equals(currency)) {
            return new CostEstimate(false, providerCost, currency, null);
        }
        BigDecimal rate = fx.map(FxRate::getRate).orElse(BigDecimal.ONE);
        ChargeCalculator.Charge c = ChargeCalculator.charge(providerCost, rate, policy.getFactor(), policy.getTaxMode(),
                policy.getTaxRate());
        return new CostEstimate(true, providerCost, currency, c.totalMxn());
    }

    Map<String, BigDecimal> rates(PricingVersion v) {
        JsonNode node = json.readTree(v.getRatesJson());
        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        node.properties().forEach(e -> rates.put(e.getKey(), e.getValue().decimalValue()));
        return rates;
    }

    static SettlementView view(UsageSettlement s) {
        return new SettlementView(s.getPublicId(), s.getStatus().name(), s.getProviderCost(), s.getProviderCurrency(),
                s.getFxRate(), s.getFxSource(), s.getDirectCostMxn(), s.getFactorApplied(), s.getMarkupMxn(),
                s.getTaxMxn(), s.getTotalMxn(), "ADMIN".equals(s.getRoleCode()), s.getNote());
    }

    private static ApiException notConfigured(String detail) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", detail);
    }
}
