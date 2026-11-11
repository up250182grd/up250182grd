package com.englishos.learning.domain;

import com.englishos.shared.text.AnswerNormalizer;
import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExerciseEvaluatorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode j(String json) {
        return JSON.readTree(json.replace('\'', '"'));
    }

    private static ExerciseEvaluator.Evaluation eval(ExerciseType type, String prompt, String solution, String answer) {
        return ExerciseEvaluator.evaluate(type, j(prompt), j(solution), j(answer));
    }

    @Nested
    class MultipleChoice {
        String prompt = "{'question':'She ___ a teacher.','options':[{'id':'a','text':'am'},{'id':'b','text':'is'}]}";
        String solution = "{'correctOptionId':'b'}";

        @Test
        void correctAndIncorrect() {
            assertThat(eval(ExerciseType.MULTIPLE_CHOICE, prompt, solution, "{'optionId':'b'}").correct()).isTrue();
            var wrong = eval(ExerciseType.MULTIPLE_CHOICE, prompt, solution, "{'optionId':'a'}");
            assertThat(wrong.correct()).isFalse();
            assertThat(wrong.score()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void rejectsUnknownOrMissingOption() {
            assertThatThrownBy(() -> eval(ExerciseType.MULTIPLE_CHOICE, prompt, solution, "{'optionId':'z'}"))
                    .isInstanceOf(InvalidExerciseException.class);
            assertThatThrownBy(() -> eval(ExerciseType.MULTIPLE_CHOICE, prompt, solution, "{}"))
                    .isInstanceOf(InvalidExerciseException.class);
        }
    }

    @Test
    void trueFalseRequiresBoolean() {
        String p = "{'statement':'x'}";
        assertThat(eval(ExerciseType.TRUE_FALSE, p, "{'value':false}", "{'value':false}").correct()).isTrue();
        assertThat(eval(ExerciseType.TRUE_FALSE, p, "{'value':false}", "{'value':true}").correct()).isFalse();
        assertThatThrownBy(() -> eval(ExerciseType.TRUE_FALSE, p, "{'value':false}", "{'value':'false'}"))
                .isInstanceOf(InvalidExerciseException.class);
    }

    @Nested
    class FillBlank {
        String prompt = "{'text':'She ___ English and I ___ in an office.'}";
        String solution = "{'answers':[['studies'],['work']]}";

        @Test
        void allBlanksCorrect() {
            var r = eval(ExerciseType.FILL_BLANK, prompt, solution, "{'values':['studies','work']}");
            assertThat(r.correct()).isTrue();
            assertThat(r.score()).isEqualByComparingTo("1");
        }

        @Test
        void partialCreditPerBlank() {
            var r = eval(ExerciseType.FILL_BLANK, prompt, solution, "{'values':['study','work']}");
            assertThat(r.correct()).isFalse();
            assertThat(r.score()).isEqualByComparingTo("0.5000");
        }

        @Test
        void acceptsListedVariantsAndIgnoresCaseSpacesApostrophesAndFinalPunctuation() {
            // JSON literal (sin el atajo de comillas simples) porque las respuestas contienen apóstrofos.
            JsonNode p = JSON.readTree("{\"text\":\"They ___ breakfast.\"}");
            JsonNode s = JSON.readTree("{\"answers\":[[\"didn't eat\",\"did not eat\"]]}");
            assertThat(ExerciseEvaluator.evaluate(ExerciseType.FILL_BLANK, p, s,
                    JSON.readTree("{\"values\":[\"  Didn’t   EAT. \"]}")).correct()).isTrue();
            assertThat(ExerciseEvaluator.evaluate(ExerciseType.FILL_BLANK, p, s,
                    JSON.readTree("{\"values\":[\"did not eat\"]}")).correct()).isTrue();
            assertThat(ExerciseEvaluator.evaluate(ExerciseType.FILL_BLANK, p, s,
                    JSON.readTree("{\"values\":[\"didnt eat\"]}")).correct()).isFalse();
        }

        @Test
        void wrongNumberOfValuesIsInvalid() {
            assertThatThrownBy(() -> eval(ExerciseType.FILL_BLANK, prompt, solution, "{'values':['studies']}"))
                    .isInstanceOf(InvalidExerciseException.class);
        }
    }

    @Nested
    class WordOrder {
        String prompt = "{'tokens':['you','are','Where','from','?']}";
        String solution = "{'sentences':[['Where','are','you','from','?']]}";

        @Test
        void correctOrder() {
            assertThat(eval(ExerciseType.WORD_ORDER, prompt, solution, "{'tokens':['Where','are','you','from','?']}")
                    .correct()).isTrue();
            assertThat(eval(ExerciseType.WORD_ORDER, prompt, solution, "{'tokens':['you','are','Where','from','?']}")
                    .correct()).isFalse();
        }

        @Test
        void mustUseExactlyTheGivenTokens() {
            assertThatThrownBy(() -> eval(ExerciseType.WORD_ORDER, prompt, solution,
                    "{'tokens':['Where','are','you','from']}")).isInstanceOf(InvalidExerciseException.class);
            assertThatThrownBy(() -> eval(ExerciseType.WORD_ORDER, prompt, solution,
                    "{'tokens':['Where','is','you','from','?']}")).isInstanceOf(InvalidExerciseException.class);
        }
    }

    @Test
    void matchingGivesPartialCreditAndRejectsUnknownTargets() {
        String p = "{'left':[{'id':'l1','text':'go'},{'id':'l2','text':'eat'}],"
                + "'right':[{'id':'r1','text':'went'},{'id':'r2','text':'ate'},{'id':'r3','text':'goed'}]}";
        String s = "{'pairs':{'l1':'r1','l2':'r2'}}";
        assertThat(eval(ExerciseType.MATCHING, p, s, "{'pairs':{'l1':'r1','l2':'r2'}}").correct()).isTrue();
        var half = eval(ExerciseType.MATCHING, p, s, "{'pairs':{'l1':'r3','l2':'r2'}}");
        assertThat(half.correct()).isFalse();
        assertThat(half.score()).isEqualByComparingTo("0.5");
        assertThat(eval(ExerciseType.MATCHING, p, s, "{'pairs':{}}").score()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> eval(ExerciseType.MATCHING, p, s, "{'pairs':{'l1':'zz'}}"))
                .isInstanceOf(InvalidExerciseException.class);
    }

    @Test
    void errorSpottingChecksIndexAndRange() {
        String p = "{'tokens':['My','brothers','is','at','home','.']}";
        String s = "{'errorIndex':2,'correction':'are'}";
        assertThat(eval(ExerciseType.ERROR_SPOTTING, p, s, "{'index':2}").correct()).isTrue();
        assertThat(eval(ExerciseType.ERROR_SPOTTING, p, s, "{'index':1}").correct()).isFalse();
        assertThatThrownBy(() -> eval(ExerciseType.ERROR_SPOTTING, p, s, "{'index':6}"))
                .isInstanceOf(InvalidExerciseException.class);
        assertThatThrownBy(() -> eval(ExerciseType.ERROR_SPOTTING, p, s, "{'index':'2'}"))
                .isInstanceOf(InvalidExerciseException.class);
    }

    @Test
    void answerMustBeAnObject() {
        assertThatThrownBy(() -> ExerciseEvaluator.evaluate(ExerciseType.TRUE_FALSE, j("{'statement':'x'}"),
                j("{'value':true}"), j("[true]"))).isInstanceOf(InvalidExerciseException.class);
    }

    @Nested
    class DefinitionValidation {

        @Test
        void detectsBrokenDefinitions() {
            assertThat(ExerciseEvaluator.validateDefinition(ExerciseType.MULTIPLE_CHOICE,
                    j("{'question':'q','options':[{'id':'a','text':'x'},{'id':'b','text':'y'}]}"),
                    j("{'correctOptionId':'c'}"))).isNotEmpty();
            assertThat(ExerciseEvaluator.validateDefinition(ExerciseType.FILL_BLANK,
                    j("{'text':'no blanks here'}"), j("{'answers':[['x']]}"))).isNotEmpty();
            assertThat(ExerciseEvaluator.validateDefinition(ExerciseType.WORD_ORDER,
                    j("{'tokens':['a','b']}"), j("{'sentences':[['a','c']]}"))).isNotEmpty();
            assertThat(ExerciseEvaluator.validateDefinition(ExerciseType.ERROR_SPOTTING,
                    j("{'tokens':['a','b']}"), j("{'errorIndex':5,'correction':'x'}"))).isNotEmpty();
            assertThat(ExerciseEvaluator.validateDefinition(ExerciseType.MATCHING,
                    j("{'left':[{'id':'l1','text':'a'},{'id':'l2','text':'b'}],'right':[{'id':'r1','text':'c'}]}"),
                    j("{'pairs':{'l1':'r1','l2':'r1'}}"))).isNotEmpty();
        }

        @Test
        void acceptsValidDefinition() {
            assertThat(ExerciseEvaluator.validateDefinition(ExerciseType.TRUE_FALSE, j("{'statement':'x'}"),
                    j("{'value':true}"))).isEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "  Hello   World  |hello world",
            "It’s fine.|it's fine",
            "Really?!|really",
            "ＡＢＣ|abc"
    })
    void normalizer(String raw, String expected) {
        assertThat(AnswerNormalizer.normalize(raw)).isEqualTo(expected);
    }
}
