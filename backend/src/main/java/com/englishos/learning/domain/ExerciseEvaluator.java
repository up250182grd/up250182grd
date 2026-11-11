package com.englishos.learning.domain;

import com.englishos.shared.text.AnswerNormalizer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.JsonNode;

/**
 * Evaluación determinista de ejercicios (RF-EXR-2). Funciones puras: misma definición + misma respuesta → mismo
 * resultado, sin IA ni estado. También valida definiciones al crear/editar contenido.
 *
 * <p>Formato JSON por tipo (prompt / solution / answer):
 * <ul>
 *   <li>MULTIPLE_CHOICE: {question, options:[{id,text}]} / {correctOptionId} / {optionId}</li>
 *   <li>TRUE_FALSE: {statement} / {value} / {value}</li>
 *   <li>FILL_BLANK: {text con "___" por hueco} / {answers:[[variantes por hueco]]} / {values:[...]}</li>
 *   <li>WORD_ORDER: {tokens:[desordenados]} / {sentences:[[orden válido]]} / {tokens:[...]}</li>
 *   <li>MATCHING: {left:[{id,text}], right:[{id,text}]} / {pairs:{leftId:rightId}} / {pairs:{...}}</li>
 *   <li>ERROR_SPOTTING: {tokens:[...]} / {errorIndex, correction} / {index}</li>
 * </ul>
 */
public final class ExerciseEvaluator {

    public static final String BLANK = "___";

    private ExerciseEvaluator() {
    }

    /** Resultado: correcto si score = 1. Puntaje parcial en FILL_BLANK (por hueco) y MATCHING (por par). */
    public record Evaluation(boolean correct, BigDecimal score) {

        static Evaluation of(int right, int total) {
            BigDecimal score = total == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(right).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
            return new Evaluation(right == total && total > 0, score);
        }

        static Evaluation binary(boolean ok) {
            return of(ok ? 1 : 0, 1);
        }
    }

    public static Evaluation evaluate(ExerciseType type, JsonNode prompt, JsonNode solution, JsonNode answer) {
        if (answer == null || !answer.isObject()) {
            throw new InvalidExerciseException("La respuesta debe ser un objeto JSON");
        }
        return switch (type) {
            case MULTIPLE_CHOICE -> multipleChoice(prompt, solution, answer);
            case TRUE_FALSE -> trueFalse(solution, answer);
            case FILL_BLANK -> fillBlank(prompt, solution, answer);
            case WORD_ORDER -> wordOrder(prompt, solution, answer);
            case MATCHING -> matching(prompt, solution, answer);
            case ERROR_SPOTTING -> errorSpotting(prompt, solution, answer);
        };
    }

    // ---- Evaluadores ---------------------------------------------------------------------------

    private static Evaluation multipleChoice(JsonNode prompt, JsonNode solution, JsonNode answer) {
        String chosen = requiredText(answer, "optionId");
        if (!optionIds(prompt).contains(chosen)) {
            throw new InvalidExerciseException("Opción inexistente");
        }
        return Evaluation.binary(chosen.equals(solution.path("correctOptionId").asString()));
    }

    private static Evaluation trueFalse(JsonNode solution, JsonNode answer) {
        JsonNode value = answer.get("value");
        if (value == null || !value.isBoolean()) {
            throw new InvalidExerciseException("Se esperaba value: true|false");
        }
        return Evaluation.binary(value.booleanValue() == solution.path("value").booleanValue());
    }

    private static Evaluation fillBlank(JsonNode prompt, JsonNode solution, JsonNode answer) {
        int blanks = countBlanks(prompt.path("text").asString());
        JsonNode values = answer.get("values");
        if (values == null || !values.isArray() || values.size() != blanks) {
            throw new InvalidExerciseException("Se esperaban " + blanks + " respuestas");
        }
        JsonNode accepted = solution.path("answers");
        int right = 0;
        for (int i = 0; i < blanks; i++) {
            String given = AnswerNormalizer.normalize(values.get(i).asString());
            for (JsonNode variant : accepted.get(i)) {
                if (AnswerNormalizer.normalize(variant.asString()).equals(given)) {
                    right++;
                    break;
                }
            }
        }
        return Evaluation.of(right, blanks);
    }

    private static Evaluation wordOrder(JsonNode prompt, JsonNode solution, JsonNode answer) {
        List<String> given = textList(answer.get("tokens"), "tokens");
        if (!sameMultiset(given, textList(prompt.get("tokens"), "tokens"))) {
            throw new InvalidExerciseException("La respuesta debe usar exactamente las palabras dadas");
        }
        List<String> normalized = given.stream().map(AnswerNormalizer::normalize).toList();
        for (JsonNode sentence : solution.path("sentences")) {
            List<String> valid = new ArrayList<>();
            sentence.forEach(t -> valid.add(AnswerNormalizer.normalize(t.asString())));
            if (valid.equals(normalized)) {
                return Evaluation.binary(true);
            }
        }
        return Evaluation.binary(false);
    }

    private static Evaluation matching(JsonNode prompt, JsonNode solution, JsonNode answer) {
        JsonNode pairs = answer.get("pairs");
        if (pairs == null || !pairs.isObject()) {
            throw new InvalidExerciseException("Se esperaba pairs: {izquierda: derecha}");
        }
        Set<String> left = ids(prompt.path("left"));
        Set<String> right = ids(prompt.path("right"));
        int correct = 0;
        for (String leftId : left) {
            JsonNode chosen = pairs.get(leftId);
            if (chosen != null && !chosen.isNull() && !right.contains(chosen.asString())) {
                throw new InvalidExerciseException("Elemento de la derecha inexistente");
            }
            if (chosen != null && chosen.asString().equals(solution.path("pairs").path(leftId).asString())) {
                correct++;
            }
        }
        return Evaluation.of(correct, left.size());
    }

    private static Evaluation errorSpotting(JsonNode prompt, JsonNode solution, JsonNode answer) {
        JsonNode index = answer.get("index");
        int size = prompt.path("tokens").size();
        if (index == null || !index.isInt() || index.intValue() < 0 || index.intValue() >= size) {
            throw new InvalidExerciseException("Índice fuera de rango");
        }
        return Evaluation.binary(index.intValue() == solution.path("errorIndex").intValue());
    }

    // ---- Validación de definiciones (contenido creado por el administrador o semillas) ----------

    /** @return lista de problemas; vacía si la definición es válida. */
    public static List<String> validateDefinition(ExerciseType type, JsonNode prompt, JsonNode solution) {
        List<String> errors = new ArrayList<>();
        if (prompt == null || !prompt.isObject() || solution == null || !solution.isObject()) {
            errors.add("prompt y solution deben ser objetos JSON");
            return errors;
        }
        switch (type) {
            case MULTIPLE_CHOICE -> {
                require(errors, prompt.path("question").isString(), "prompt.question es obligatorio");
                Set<String> opts = optionIds(prompt);
                require(errors, opts.size() >= 2 && opts.size() == prompt.path("options").size(),
                        "prompt.options necesita ≥ 2 opciones con id único");
                require(errors, opts.contains(solution.path("correctOptionId").asString()),
                        "solution.correctOptionId debe existir en las opciones");
            }
            case TRUE_FALSE -> {
                require(errors, prompt.path("statement").isString(), "prompt.statement es obligatorio");
                require(errors, solution.path("value").isBoolean(), "solution.value debe ser true o false");
            }
            case FILL_BLANK -> {
                int blanks = countBlanks(prompt.path("text").asString());
                require(errors, blanks >= 1, "prompt.text debe contener al menos un hueco " + BLANK);
                JsonNode answers = solution.path("answers");
                require(errors, answers.isArray() && answers.size() == blanks,
                        "solution.answers debe tener una lista de variantes por hueco");
                answers.forEach(a -> require(errors, a.isArray() && !a.isEmpty(), "cada hueco necesita ≥ 1 variante"));
            }
            case WORD_ORDER -> {
                JsonNode tokens = prompt.path("tokens");
                require(errors, tokens.isArray() && tokens.size() >= 2, "prompt.tokens necesita ≥ 2 palabras");
                JsonNode sentences = solution.path("sentences");
                require(errors, sentences.isArray() && !sentences.isEmpty(), "solution.sentences es obligatorio");
                if (tokens.isArray()) {
                    List<String> given = textList(tokens, "tokens");
                    sentences.forEach(s -> require(errors, s.isArray() && sameMultiset(textList(s, "sentence"), given),
                            "cada oración válida debe usar exactamente las palabras de prompt.tokens"));
                }
            }
            case MATCHING -> {
                Set<String> left = ids(prompt.path("left"));
                Set<String> right = ids(prompt.path("right"));
                require(errors, left.size() >= 2 && right.size() >= left.size(), "se necesitan ≥ 2 pares");
                JsonNode pairs = solution.path("pairs");
                require(errors, pairs.isObject() && pairs.size() == left.size(), "solution.pairs: un par por elemento");
                left.forEach(l -> require(errors, right.contains(pairs.path(l).asString()),
                        "solution.pairs." + l + " debe apuntar a un elemento de la derecha"));
            }
            case ERROR_SPOTTING -> {
                int size = prompt.path("tokens").size();
                int idx = solution.path("errorIndex").asInt(-1);
                require(errors, size >= 2, "prompt.tokens necesita ≥ 2 palabras");
                require(errors, solution.path("errorIndex").isInt() && idx >= 0 && idx < size,
                        "solution.errorIndex fuera de rango");
                require(errors, solution.path("correction").isString(), "solution.correction es obligatorio");
            }
        }
        return errors;
    }

    // ---- Utilidades ----------------------------------------------------------------------------

    static int countBlanks(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(BLANK, from)) >= 0) {
            count++;
            from += BLANK.length();
        }
        return count;
    }

    private static Set<String> optionIds(JsonNode prompt) {
        return ids(prompt.path("options"));
    }

    private static Set<String> ids(JsonNode array) {
        Set<String> ids = new HashSet<>();
        if (array.isArray()) {
            array.forEach(o -> {
                if (o.path("id").isString()) {
                    ids.add(o.path("id").asString());
                }
            });
        }
        return ids;
    }

    private static List<String> textList(JsonNode node, String field) {
        if (node == null || !node.isArray()) {
            throw new InvalidExerciseException("Se esperaba una lista en " + field);
        }
        List<String> list = new ArrayList<>();
        node.forEach(n -> {
            if (!n.isString()) {
                throw new InvalidExerciseException("Elemento no textual en " + field);
            }
            list.add(n.asString());
        });
        return list;
    }

    private static boolean sameMultiset(List<String> a, List<String> b) {
        if (a.size() != b.size()) {
            return false;
        }
        Map<String, Integer> counts = new HashMap<>();
        a.forEach(s -> counts.merge(s, 1, Integer::sum));
        for (String s : b) {
            Integer c = counts.get(s);
            if (c == null || c == 0) {
                return false;
            }
            counts.put(s, c - 1);
        }
        return true;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isString()) {
            throw new InvalidExerciseException("Falta " + field);
        }
        return v.asString();
    }

    private static void require(List<String> errors, boolean condition, String message) {
        if (!condition) {
            errors.add(message);
        }
    }
}
