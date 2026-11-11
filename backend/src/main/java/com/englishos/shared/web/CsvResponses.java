package com.englishos.shared.web;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Respuestas de descarga CSV y periodo mensual en la zona del administrador. */
public final class CsvResponses {

    private CsvResponses() {
    }

    public record Period(YearMonth month, Instant from, Instant to) {

        public static Period of(String month, ZoneId zone) {
            YearMonth ym;
            try {
                ym = month == null || month.isBlank() ? YearMonth.now(zone) : YearMonth.parse(month);
            } catch (java.time.format.DateTimeParseException e) {
                throw com.englishos.shared.error.ApiException.badRequest("INVALID_MONTH", "Usa el formato AAAA-MM.");
            }
            return new Period(ym, ym.atDay(1).atStartOfDay(zone).toInstant(),
                    ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant());
        }
    }

    public static ResponseEntity<byte[]> csv(String filename, String body) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
