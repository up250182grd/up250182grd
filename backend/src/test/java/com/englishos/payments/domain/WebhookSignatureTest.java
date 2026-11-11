package com.englishos.payments.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureTest {

    private static final String SECRET = "clave-secreta-de-prueba";

    @Test
    void buildsTheManifestInTheDocumentedOrderAndOmitsMissingParts() {
        assertThat(WebhookSignature.manifest("123", "req-1", "1700000000"))
                .isEqualTo("id:123;request-id:req-1;ts:1700000000;");
        assertThat(WebhookSignature.manifest("ABC9", null, "17")).isEqualTo("id:abc9;ts:17;");
    }

    @Test
    void hmacMatchesAKnownVector() {
        // Vector calculado con: printf 'id:123;request-id:req-1;ts:1700000000;' | openssl dgst -sha256 -hmac '<SECRET>'
        assertThat(WebhookSignature.hmacHex(SECRET, "id:123;request-id:req-1;ts:1700000000;"))
                .isEqualTo(KNOWN_VECTOR);
    }

    static final String KNOWN_VECTOR = "e54d827fd0e96ab25197882a17024bdc192a8295e70a771eefc950c5a3a6c92f";

    @Test
    void acceptsAValidSignatureAndRejectsTampering() {
        String v1 = WebhookSignature.hmacHex(SECRET, "id:123;request-id:req-1;ts:1700000000;");
        String header = "ts=1700000000,v1=" + v1;
        assertThat(WebhookSignature.isValid(SECRET, header, "req-1", "123")).isTrue();
        assertThat(WebhookSignature.isValid(SECRET, " ts=1700000000 , v1=" + v1.toUpperCase(), "req-1", "123")).isTrue();

        assertThat(WebhookSignature.isValid(SECRET, header, "req-1", "124")).as("otro pago").isFalse();
        assertThat(WebhookSignature.isValid(SECRET, header, "req-2", "123")).as("otra petición").isFalse();
        assertThat(WebhookSignature.isValid(SECRET, "ts=1700000001,v1=" + v1, "req-1", "123")).as("otra marca").isFalse();
        assertThat(WebhookSignature.isValid("otra-clave", header, "req-1", "123")).isFalse();
        assertThat(WebhookSignature.isValid(SECRET, null, "req-1", "123")).isFalse();
        assertThat(WebhookSignature.isValid(SECRET, "v1=" + v1, "req-1", "123")).as("sin ts").isFalse();
        assertThat(WebhookSignature.isValid("", header, "req-1", "123")).as("sin secreto configurado").isFalse();
    }
}
