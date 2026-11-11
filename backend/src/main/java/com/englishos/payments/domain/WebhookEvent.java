package com.englishos.payments.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro de cada notificación recibida de la pasarela, válida o no. */
@Entity
@Table(name = "payment_webhook_events")
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 24)
    private String provider;

    @Column(name = "request_id", updatable = false, length = 100)
    private String requestId;

    @Column(updatable = false, length = 40)
    private String topic;

    @Column(updatable = false, length = 60)
    private String action;

    @Column(name = "data_id", updatable = false, length = 64)
    private String dataId;

    @Column(name = "signature_valid", nullable = false, updatable = false)
    private boolean signatureValid;

    @Column(nullable = false, length = 40)
    private String outcome;

    @Column(length = 500)
    private String detail;

    @Column(updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    protected WebhookEvent() {
    }

    public static WebhookEvent received(String provider, String requestId, String topic, String action, String dataId,
            boolean signatureValid, String payload, Instant now) {
        WebhookEvent e = new WebhookEvent();
        e.provider = provider;
        e.requestId = clip(requestId, 100);
        e.topic = clip(topic, 40);
        e.action = clip(action, 60);
        e.dataId = clip(dataId, 64);
        e.signatureValid = signatureValid;
        e.payload = clip(payload, 8000);
        e.outcome = "RECEIVED";
        e.receivedAt = now;
        return e;
    }

    public void outcome(String outcome, String detail) {
        this.outcome = outcome;
        this.detail = clip(detail, 500);
    }

    private static String clip(String s, int max) {
        return s == null ? null : s.length() > max ? s.substring(0, max) : s;
    }

    public Long getId() {
        return id;
    }

    public String getOutcome() {
        return outcome;
    }
}
