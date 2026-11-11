package com.englishos.vocabulary.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Término del catálogo global (RF-SRS-1). Lo comparten todos los alumnos; el estado SM-2 vive en la tarjeta. */
@Entity
@Table(name = "vocabulary_items")
public class VocabularyItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 120)
    private String term;

    @Column(name = "part_of_speech", nullable = false, updatable = false, length = 24)
    private String partOfSpeech;

    @Column(length = 120)
    private String ipa;

    @Column(name = "translation_es", nullable = false)
    private String translationEs;

    @Column(name = "cefr_level", nullable = false, length = 2)
    private String cefrLevel;

    @Column(name = "source_lesson", length = 26)
    private String sourceLesson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VocabularyItem() {
    }

    public static VocabularyItem create(String term, String partOfSpeech, String ipa, String translationEs,
            String cefrLevel, String sourceLesson, Instant now) {
        VocabularyItem i = new VocabularyItem();
        i.term = term;
        i.partOfSpeech = partOfSpeech;
        i.ipa = ipa;
        i.translationEs = translationEs;
        i.cefrLevel = cefrLevel;
        i.sourceLesson = sourceLesson;
        i.createdAt = now;
        i.updatedAt = now;
        return i;
    }

    /** Si el contenido de la lección se corrigió (traducción o IPA), el catálogo se actualiza. */
    public boolean refresh(String newIpa, String newTranslation, Instant now) {
        boolean changed = !newTranslation.equals(translationEs) || !java.util.Objects.equals(newIpa, ipa);
        if (changed) {
            ipa = newIpa;
            translationEs = newTranslation;
            updatedAt = now;
        }
        return changed;
    }

    public Long getId() {
        return id;
    }

    public String getTerm() {
        return term;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public String getIpa() {
        return ipa;
    }

    public String getTranslationEs() {
        return translationEs;
    }

    public String getCefrLevel() {
        return cefrLevel;
    }
}
