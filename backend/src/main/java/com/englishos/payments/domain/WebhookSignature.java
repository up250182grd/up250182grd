package com.englishos.payments.domain;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Validación de la firma de notificaciones de Mercado Pago.
 *
 * <p>🔍 Formato tomado de la guía de webhooks de Mercado Pago (no se pudo leer la página oficial desde el entorno de
 * desarrollo; se confirmó con resúmenes de búsqueda y SDK de terceros). Verificar antes de producción:
 * <ul>
 * <li>Cabecera {@code x-signature: ts=<marca>,v1=<hmac hex>} y cabecera {@code x-request-id}.</li>
 * <li>Mensaje firmado: {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;} — si falta un valor, se omite su
 * parte. {@code data.id} viene en la URL de la notificación; si es alfanumérico se usa en minúsculas.</li>
 * <li>HMAC-SHA256 en hexadecimal con la "clave secreta" del panel de la aplicación.</li>
 * </ul>
 *
 * <p>La firma solo autentica al remitente: el dinero NUNCA se acredita con datos de la notificación, sino tras
 * consultar el pago en la API del proveedor (ver {@code PaymentService}).
 */
public final class WebhookSignature {

    private WebhookSignature() {
    }

    public record Parsed(String ts, String v1) {
    }

    public static Parsed parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String ts = null;
        String v1 = null;
        for (String part : header.split(",")) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (key.equals("ts")) {
                ts = value;
            } else if (key.equals("v1")) {
                v1 = value;
            }
        }
        return ts == null || v1 == null ? null : new Parsed(ts, v1);
    }

    public static String manifest(String dataId, String requestId, String ts) {
        StringBuilder sb = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            sb.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            sb.append("request-id:").append(requestId).append(';');
        }
        if (ts != null && !ts.isBlank()) {
            sb.append("ts:").append(ts).append(';');
        }
        return sb.toString();
    }

    public static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Comparación en tiempo constante. Sin secreto configurado, ninguna firma es válida. */
    public static boolean isValid(String secret, String signatureHeader, String requestId, String dataId) {
        if (secret == null || secret.isBlank()) {
            return false;
        }
        Parsed p = parse(signatureHeader);
        if (p == null) {
            return false;
        }
        String expected = hmacHex(secret, manifest(dataId, requestId, p.ts()));
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                p.v1().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
}
