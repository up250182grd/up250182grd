package com.englishos.metering.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Cálculo normativo del cargo (docs/architecture/05-reglas-negocio.md §5.2). Función pura, sin estado ni Spring.
 *
 * <pre>
 * 1. providerCost     = Σ units[k] × rate[k] / 1 000 000          (escala 10, sin redondeo intermedio)
 * 2. directCostRaw    = providerCost × fx                          (escala 10)
 * 3. directCostMxn    = round(directCostRaw, 2, HALF_UP)           ← punto financiero
 * 4. priceBeforeTax   = round(directCostRaw × factor, 2, HALF_UP)  ← punto financiero
 * 5. markup           = priceBeforeTax − directCostMxn             (0.00 exacto si factor = 1.00)
 * 6. tax              = round(priceBeforeTax × taxRate, 2, HALF_UP) (0 si taxMode = NONE)
 * 7. total            = priceBeforeTax + tax
 * </pre>
 */
public final class ChargeCalculator {

    public static final int COST_SCALE = 10;
    public static final int MONEY_SCALE = 2;
    private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

    private ChargeCalculator() {
    }

    public enum TaxMode { NONE, ADDED }

    public record Charge(BigDecimal providerCost, BigDecimal directCostMxn, BigDecimal priceBeforeTaxMxn,
            BigDecimal markupMxn, BigDecimal taxMxn, BigDecimal totalMxn) {
    }

    /** Paso 1: costo del proveedor en su moneda. Unidades sin tarifa definida son un error (no se ignoran). */
    public static BigDecimal providerCost(Map<String, Long> units, Map<String, BigDecimal> ratesPerMillion) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Long> e : units.entrySet()) {
            long qty = e.getValue() == null ? 0 : e.getValue();
            if (qty < 0) {
                throw new IllegalArgumentException("Unidades negativas: " + e.getKey());
            }
            if (qty == 0) {
                continue;
            }
            BigDecimal rate = ratesPerMillion.get(e.getKey());
            if (rate == null) {
                throw new IllegalArgumentException("No hay tarifa para el tipo de unidad " + e.getKey());
            }
            total = total.add(BigDecimal.valueOf(qty).multiply(rate));
        }
        return total.divide(MILLION, COST_SCALE, RoundingMode.HALF_UP);
    }

    /** Pasos 2–7. {@code fx} = MXN por 1 unidad de la moneda del proveedor (1 si ya es MXN). */
    public static Charge charge(BigDecimal providerCost, BigDecimal fx, BigDecimal factor, TaxMode taxMode,
            BigDecimal taxRate) {
        if (providerCost.signum() < 0 || fx.signum() <= 0 || factor.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Parámetros de cargo inválidos");
        }
        BigDecimal directRaw = providerCost.multiply(fx).setScale(COST_SCALE, RoundingMode.HALF_UP);
        BigDecimal direct = directRaw.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal price = directRaw.multiply(factor).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal markup = price.subtract(direct);
        BigDecimal tax = taxMode == TaxMode.ADDED
                ? price.multiply(taxRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(MONEY_SCALE);
        return new Charge(providerCost, direct, price, markup, tax, price.add(tax));
    }
}
