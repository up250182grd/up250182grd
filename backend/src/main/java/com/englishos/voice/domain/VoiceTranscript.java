package com.englishos.voice.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Línea de transcripción (texto). El audio nunca se almacena. */
@Entity
@Table(name = "voice_transcripts")
public class VoiceTranscript {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(nullable = false, updatable = false, length = 16)
    private String role;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected VoiceTranscript() {
    }

    public VoiceTranscript(Long sessionId, String role, String content, Instant now) {
        this.sessionId = sessionId;
        this.role = role;
        this.content = content.length() > 4000 ? content.substring(0, 4000) : content;
        this.createdAt = now;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
