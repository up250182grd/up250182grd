package com.englishos.metering.domain;

import java.math.BigDecimal;
import java.util.Map;

import com.englishos.metering.domain.ChargeCalculator.Charge;
import com.englishos.metering.domain.ChargeCalculator.TaxMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Algoritmo normativo §5.2 y factores obligatorios RN-1 (admin 1.00, alumno 1.30). */
class ChargeCalculatorTest {

    private static final BigDecimal ADMIN = new BigDecimal("1.00");
    private static final BigDecimal STUDENT = new BigDecimal("1.30");

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    @Test
    void referenceExampleFromTheBusinessRules() {
        // Costo del proveedor equivalente a $100.00 MXN.
        Charge admin = ChargeCalculator.charge(d("100"), BigDecimal.ONE, ADMIN, TaxMode.NONE, BigDecimal.ZERO);
        Charge student = ChargeCalculator.charge(d("100"), BigDecimal.ONE, STUDENT, TaxMode.NONE, BigDecimal.ZERO);

        assertThat(admin.priceBeforeTaxMxn()).isEqualByComparingTo("100.00");
        assertThat(admin.markupMxn()).isEqualByComparingTo("0.00");
        assertThat(admin.totalMxn()).isEqualByComparingTo("100.00");

        assertThat(student.directCostMxn()).isEqualByComparingTo("100.00");
        assertThat(student.priceBeforeTaxMxn()).isEqualByComparingTo("130.00");
        assertThat(student.markupMxn()).isEqualByComparingTo("30.00");
        assertThat(student.totalMxn()).isEqualByComparingTo("130.00");
    }

    @Test
    void providerCostFromTokensWithoutIntermediateRounding() {
        // 1 234 tokens de entrada a 2.50 USD/M + 567 de salida a 10 USD/M = 0.003085 + 0.00567 = 0.008755 USD
        BigDecimal cost = ChargeCalculator.providerCost(
                Map.of("text_in", 1234L, "text_out", 567L),
                Map.of("text_in", d("2.50"), "text_out", d("10.00")));
        assertThat(cost).isEqualByComparingTo("0.008755");
        assertThat(cost.scale()).isEqualTo(ChargeCalculator.COST_SCALE);
    }

    @Test
    void convertsCurrencyThenAppliesFactorOnUnroundedCost() {
        // 0.008755 USD × 18.123456 MXN/USD = 0.15867085728 → directo 0.16; × 1.30 = 0.2062721144 → 0.21
        Charge c = ChargeCalculator.charge(d("0.0087550000"), d("18.123456"), STUDENT, TaxMode.NONE, BigDecimal.ZERO);
        assertThat(c.directCostMxn()).isEqualByComparingTo("0.16");
        assertThat(c.priceBeforeTaxMxn()).isEqualByComparingTo("0.21");
        assertThat(c.markupMxn()).isEqualByComparingTo("0.05");
    }

    @ParameterizedTest(name = "costo {0} → admin {1}, alumno {2}")
    @CsvSource({
            "0.004, 0.00, 0.01",   // 0.004 → 0.00 directo; × 1.30 = 0.0052 → 0.01
            "0.005, 0.01, 0.01",   // HALF_UP en el punto financiero
            "10.00, 10.00, 13.00",
            "33.333333, 33.33, 43.33"
    })
    void roundingOnlyAtFinancialPoints(String cost, String admin, String student) {
        assertThat(ChargeCalculator.charge(d(cost), BigDecimal.ONE, ADMIN, TaxMode.NONE, BigDecimal.ZERO).totalMxn())
                .isEqualByComparingTo(admin);
        assertThat(ChargeCalculator.charge(d(cost), BigDecimal.ONE, STUDENT, TaxMode.NONE, BigDecimal.ZERO).totalMxn())
                .isEqualByComparingTo(student);
    }

    @Test
    void taxIsAddedOnTopOfThePriceAndNeverPartOfTheMarkup() {
        Charge c = ChargeCalculator.charge(d("100"), BigDecimal.ONE, STUDENT, TaxMode.ADDED, d("0.16"));
        assertThat(c.priceBeforeTaxMxn()).isEqualByComparingTo("130.00");
        assertThat(c.markupMxn()).isEqualByComparingTo("30.00");
        assertThat(c.taxMxn()).isEqualByComparingTo("20.80");
        assertThat(c.totalMxn()).isEqualByComparingTo("150.80");
    }

    @Test
    void adminMarkupIsExactlyZeroForAnyCost() {
        for (String cost : new String[] {"0.0049999999", "1.2345678901", "999.9999999999"}) {
            assertThat(ChargeCalculator.charge(d(cost), d("17.5"), ADMIN, TaxMode.NONE, BigDecimal.ZERO).markupMxn())
                    .isEqualByComparingTo("0");
        }
    }

    @Test
    void rejectsInvalidInputsInsteadOfGuessing() {
        assertThatThrownBy(() -> ChargeCalculator.providerCost(Map.of("audio_out", 10L), Map.of("text_out", d("1"))))
                .hasMessageContaining("audio_out");
        assertThatThrownBy(() -> ChargeCalculator.providerCost(Map.of("text_in", -1L), Map.of("text_in", d("1"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeCalculator.charge(d("1"), BigDecimal.ZERO, STUDENT, TaxMode.NONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChargeCalculator.charge(d("1"), BigDecimal.ONE, d("0.9"), TaxMode.NONE, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zeroUnitsCostNothingEvenWithoutARate() {
        assertThat(ChargeCalculator.providerCost(Map.of("audio_in", 0L), Map.of())).isEqualByComparingTo("0");
    }
}
