package com.englishos.metering.api;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Map;

/** Medición de consumo y liquidación (RF-MTR). La usan el tutor por texto y, en la Fase 5, la voz. */
public interface MeteringApi {

    /**
     * Unidad de audio facturado por duración, en millonésimas de minuto: como las tarifas son "por millón de
     * unidades", la tarifa de esta unidad es el precio por minuto que publica el proveedor.
     */
    String AUDIO_MICROMINUTES = "audio_microminutes";

    /** Tarifa y política vigentes al iniciar una sesión. Se conservan en la sesión (RN-4). */
    record SessionPricing(Long modelId, String provider, String modelCode, boolean simulated, Long pricingVersionId,
            String currency, Map<String, BigDecimal> ratesPerMillion, Long policyId, String roleCode,
            BigDecimal factor) {
    }

    record UsageInput(Long userId, String subjectType, Long subjectId, Long modelId, Long pricingVersionId,
            String providerRequestId, Map<String, Long> units, String dataSource) {
    }

    /** Estimación en MXN; {@code available = false} si falta tipo de cambio (no se inventa una cifra). */
    record CostEstimate(boolean available, BigDecimal providerCost, String currency, BigDecimal totalMxn) {
    }

    record SettlementView(String id, String status, BigDecimal providerCost, String providerCurrency,
            BigDecimal fxRate, String fxSource, BigDecimal directCostMxn, BigDecimal factorApplied,
            BigDecimal markupMxn, BigDecimal taxMxn, BigDecimal totalMxn, boolean internal, String note) {
    }

    SessionPricing pricingFor(Long userId, boolean admin, String provider, String modelCode);

    /** Registra el uso informado por el proveedor; idempotente por {@code providerRequestId}. Devuelve su costo. */
    BigDecimal recordUsage(UsageInput input);

    /** Costo acumulado (medido, aún sin liquidar) de un sujeto, convertido a precio para el usuario. */
    CostEstimate measuredSoFar(String subjectType, Long subjectId, Long pricingVersionId, Long policyId);

    /** Precio de un uso hipotético con la tarifa y política fijadas en la sesión (para decidir reservas). */
    CostEstimate estimate(Long pricingVersionId, Long policyId, Map<String, Long> units);

    /**
     * Precio de un uso hipotético repartido entre varias tarifas (p. ej. modelo realtime + transcripción). Se suma el
     * costo del proveedor y se convierte una sola vez, igual que en la liquidación. Todas las tarifas deben estar en
     * la misma moneda.
     */
    CostEstimate estimate(Long policyId, Map<Long, Map<String, Long>> unitsByPricingVersion);

    /** Tarifas por millón de unidades de una versión (para saber qué unidades factura un modelo). */
    Map<String, BigDecimal> ratesOf(Long pricingVersionId);

    /** Datos del modelo fijado en una sesión. */
    record ModelRef(Long id, String provider, String modelCode, String displayName, boolean simulated) {
    }

    ModelRef model(Long modelId);

    /**
     * Liquida el sujeto con la tarifa y la política fijadas al iniciarlo: calcula el cargo y lo contabiliza en la
     * billetera (o como gasto interno si la política es la del administrador). Idempotente.
     */
    SettlementView settle(Long userId, String subjectType, Long subjectId, Long pricingVersionId, Long policyId);

    /** Liquidación existente de un sujeto, si ya se liquidó (solo lectura). */
    java.util.Optional<SettlementView> settlementOf(String subjectType, Long subjectId);

    /** Total liquidado en el mes (para el tope del administrador y el límite mensual). */
    BigDecimal settledThisMonth(Long userId, ZoneId zone);
}
