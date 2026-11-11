package com.englishos.shared.text;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Escritura de CSV (RFC 4180) para exportaciones de administración. Protege contra "inyección de fórmulas": una
 * celda de texto que empieza con = + - @ (o tabulador/retorno) se antepone con un apóstrofo para que Excel u hojas de
 * cálculo no la ejecuten. Los números se escriben tal cual (sin notación científica).
 */
public final class Csv {

    private Csv() {
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Instant) {
            return value.toString();
        }
        String s = value.toString();
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    public static String row(List<?> values) {
        return values.stream().map(Csv::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    /** BOM de UTF-8: Excel lo necesita para mostrar bien los acentos. */
    public static final String BOM = "﻿";
}
