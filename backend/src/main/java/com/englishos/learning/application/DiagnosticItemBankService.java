package com.englishos.learning.application;

import java.util.List;

import tools.jackson.databind.JsonNode;

import com.englishos.learning.api.DiagnosticItemBank;
import com.englishos.learning.domain.ContentStatus;
import com.englishos.learning.domain.Exercise;
import com.englishos.learning.domain.ExerciseEvaluator;
import com.englishos.learning.domain.InvalidExerciseException;
import com.englishos.learning.infrastructure.ExerciseRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DiagnosticItemBankService implements DiagnosticItemBank {

    private final ExerciseRepository exercises;
    private final Json json;

    DiagnosticItemBankService(ExerciseRepository exercises, Json json) {
        this.exercises = exercises;
        this.json = json;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemRef> publishedItems() {
        return exercises.findByPurposeAndStatus(Exercise.Purpose.DIAGNOSTIC, ContentStatus.PUBLISHED).stream()
                .map(e -> new ItemRef(e.getId(), e.getSkill(), e.getLevel()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ItemView view(Long itemId) {
        Exercise e = item(itemId);
        return new ItemView(e.getPublicId(), e.getType().name(), e.getSkill(), e.getLevel(), e.getInstructions(),
                json.read(e.getPromptJson()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCorrect(Long itemId, JsonNode answer) {
        Exercise e = item(itemId);
        try {
            return ExerciseEvaluator.evaluate(e.getType(), json.read(e.getPromptJson()),
                    json.read(e.getSolutionJson()), answer).correct();
        } catch (InvalidExerciseException ex) {
            throw ApiException.badRequest("INVALID_ANSWER", "La respuesta no tiene el formato esperado: " + ex.getMessage());
        }
    }

    private Exercise item(Long id) {
        return exercises.findById(id)
                .filter(e -> e.getPurpose() == Exercise.Purpose.DIAGNOSTIC)
                .orElseThrow(() -> ApiException.notFound("ITEM_NOT_FOUND", "Ítem no encontrado."));
    }
}
