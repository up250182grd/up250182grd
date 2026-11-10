package com.englishos.identity.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Registro inmutable de la aceptación de un documento legal en una versión concreta. */
@Entity
@Table(name = "consents")
public class Consent {

    public enum DocumentType { TERMS, PRIVACY, TRANSCRIPTS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "document_type", nullable = false, length = 32, updatable = false)
    private String documentType;

    @Column(name = "document_version", nullable = false, length = 32, updatable = false)
    private String documentVersion;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private Instant acceptedAt;

    protected Consent() {
    }

    public static Consent accept(Long userId, DocumentType type, String version, Instant now) {
        Consent c = new Consent();
        c.userId = userId;
        c.documentType = type.name();
        c.documentVersion = version;
        c.acceptedAt = now;
        return c;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentVersion() {
        return documentVersion;
    }
}
