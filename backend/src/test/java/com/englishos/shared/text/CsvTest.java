package com.englishos.shared.text;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTest {

    @Test
    void escapesQuotesCommasAndNewlines() {
        assertThat(Csv.row(List.of("a", "b,c", "di\"jo", "dos\nlíneas")))
                .isEqualTo("a,\"b,c\",\"di\"\"jo\",\"dos\nlíneas\"\r\n");
    }

    @Test
    void neutralizesSpreadsheetFormulasInText() {
        assertThat(Csv.cell("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(Csv.cell("+1")).isEqualTo("'+1");
        assertThat(Csv.cell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(Csv.cell("-5")).isEqualTo("'-5");
    }

    @Test
    void numbersStayNumericAndPlain() {
        assertThat(Csv.cell(new BigDecimal("-12.50"))).isEqualTo("-12.50");
        assertThat(Csv.cell(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(Csv.row(Arrays.asList(null, 3L, true))).isEqualTo(",3,true\r\n");
    }
}
