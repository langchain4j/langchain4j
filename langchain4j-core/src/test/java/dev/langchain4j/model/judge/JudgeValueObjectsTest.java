package dev.langchain4j.model.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JudgeValueObjectsTest {

    @Test
    void answer_equals_hashCode_and_toString_follow_value_semantics() {
        JudgeAnswer answer = JudgeAnswer.builder().noul(0.83).build();
        JudgeAnswer same = JudgeAnswer.builder().noul(0.83).build();
        JudgeAnswer differentNoul = JudgeAnswer.builder().noul(0.5).build();
        JudgeAnswer withConfidence =
                JudgeAnswer.builder().noul(0.83).confidence(0.9).build();
        JudgeAnswer choice =
                JudgeAnswer.builder().choice("billing").confidence(0.9).build();
        JudgeAnswer score = JudgeAnswer.builder().score(1.5).confidence(0.6).build();

        assertThat(answer).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(answer)
                .isNotEqualTo(differentNoul)
                .isNotEqualTo(withConfidence)
                .isNotEqualTo(choice)
                .isNotEqualTo(score)
                .isNotEqualTo(null)
                .isNotEqualTo("answer");
        assertThat(answer.toString()).contains("JudgeAnswer").contains("0.83");
        assertThat(choice.toString()).contains("billing");
    }

    @Test
    void request_equals_hashCode_and_toString_follow_value_semantics() {
        JudgeRequest request = sampleRequest();
        JudgeRequest same = sampleRequest();
        JudgeRequest otherParameters = JudgeRequest.builder()
                .state(Map.of("message", "hello"))
                .question("greeting", noulQuestion("Is this a greeting?"))
                .parameters(JudgeRequestParameters.builder()
                        .modelName("jev-preview")
                        .build())
                .build();
        JudgeRequest otherQuestions = JudgeRequest.builder()
                .state(Map.of("message", "hello"))
                .question("farewell", noulQuestion("Is this a farewell?"))
                .build();

        assertThat(request).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(request)
                .isNotEqualTo(otherParameters)
                .isNotEqualTo(otherQuestions)
                .isNotEqualTo(null);
        assertThat(request.toString()).contains("JudgeRequest").contains("greeting");
    }

    @Test
    void response_equals_hashCode_and_toString_follow_value_semantics() {
        JudgeResponse response = sampleResponse();
        JudgeResponse same = sampleResponse();
        JudgeResponse other = JudgeResponse.builder()
                .answer("greeting", JudgeAnswer.builder().noul(0.5).build())
                .build();

        assertThat(response).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(response).isNotEqualTo(other).isNotEqualTo(null);
        assertThat(response.toString()).contains("JudgeResponse").contains("refund");
    }

    @Test
    void questions_equals_hashCode_and_toString_follow_value_semantics() {
        NoulQuestion noul = noulQuestion("Is this a greeting?");
        NoulQuestion sameNoul = noulQuestion("Is this a greeting?");
        ChoiceQuestion choice = sampleChoiceQuestion();
        ChoiceQuestion sameChoice = sampleChoiceQuestion();
        ScoreQuestion score = sampleScoreQuestion();
        ScoreQuestion sameScore = sampleScoreQuestion();

        assertThat(noul).isEqualTo(sameNoul).hasSameHashCodeAs(sameNoul);
        assertThat(noul).isNotEqualTo(noulQuestion("Is this something else?")).isNotEqualTo(null);
        assertThat(noul.toString()).contains("NoulQuestion").contains("greeting");
        assertThat(choice).isEqualTo(sameChoice).hasSameHashCodeAs(sameChoice);
        assertThat(choice).isNotEqualTo(choiceQuestionWithDifferentOptions()).isNotEqualTo(null);
        assertThat(choice.toString()).contains("ChoiceQuestion").contains("billing");
        assertThat(score).isEqualTo(sameScore).hasSameHashCodeAs(sameScore);
        assertThat(score).isNotEqualTo(scoreQuestionWithDifferentLevels()).isNotEqualTo(null);
        assertThat(score.toString()).contains("ScoreQuestion").contains("low");
    }

    @Test
    void criteria_equals_and_toString_follow_value_semantics() {
        NoulCriteria noulCriteria = NoulCriteria.builder()
                .what("w")
                .notFor("n")
                .examples(List.of("e"))
                .build();
        OptionCriteria optionCriteria = OptionCriteria.builder()
                .what("w")
                .notFor("n")
                .examples(List.of("e"))
                .build();

        assertThat(noulCriteria)
                .isEqualTo(NoulCriteria.builder()
                        .what("w")
                        .notFor("n")
                        .examples(List.of("e"))
                        .build());
        assertThat(noulCriteria)
                .isNotEqualTo(NoulCriteria.builder().what("other").build());
        assertThat(noulCriteria.toString()).contains("NoulCriteria").contains("w");
        assertThat(optionCriteria)
                .isEqualTo(OptionCriteria.builder()
                        .what("w")
                        .notFor("n")
                        .examples(List.of("e"))
                        .build());
        assertThat(optionCriteria)
                .isNotEqualTo(OptionCriteria.builder().notFor("other").build());
        assertThat(optionCriteria.toString()).contains("OptionCriteria").contains("w");
        assertThat(optionCriteria.examples()).containsExactly("e");
    }

    @Test
    void parameters_equals_hashCode_and_toString_follow_value_semantics() {
        JudgeRequestParameters parameters =
                JudgeRequestParameters.builder().modelName("jev-latest").build();
        JudgeRequestParameters same =
                JudgeRequestParameters.builder().modelName("jev-latest").build();
        JudgeRequestParameters other =
                JudgeRequestParameters.builder().modelName("jev-preview").build();

        assertThat(parameters).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(parameters).isNotEqualTo(other).isNotEqualTo(null).isNotEqualTo("parameters");
        assertThat(parameters.toString())
                .contains("DefaultJudgeRequestParameters")
                .contains("jev-latest");
    }

    @Test
    void map_based_builder_overloads_match_the_repeated_add_forms() {
        Map<String, Question> questions = new LinkedHashMap<>();
        questions.put("greeting", noulQuestion("Is this a greeting?"));
        JudgeRequest fromMap = JudgeRequest.builder()
                .state(Map.of("message", "hello"))
                .questions(questions)
                .build();
        JudgeRequest repeated = JudgeRequest.builder()
                .state(Map.of("message", "hello"))
                .question("greeting", noulQuestion("Is this a greeting?"))
                .build();

        Map<String, OptionCriteria> options = new LinkedHashMap<>();
        options.put("billing", OptionCriteria.builder().what("Charges").build());
        options.put("support", OptionCriteria.builder().what("Product problems").build());
        ChoiceQuestion choiceFromMap = ChoiceQuestion.builder()
                .instructions("Which team?")
                .options(options)
                .build();
        ChoiceQuestion choiceRepeated = ChoiceQuestion.builder()
                .instructions("Which team?")
                .option("billing", OptionCriteria.builder().what("Charges").build())
                .option(
                        "support",
                        OptionCriteria.builder().what("Product problems").build())
                .build();

        Map<String, OptionCriteria> levels = new LinkedHashMap<>();
        levels.put("low", OptionCriteria.builder().what("Can wait").build());
        levels.put("high", OptionCriteria.builder().what("Now").build());
        ScoreQuestion scoreFromMap = ScoreQuestion.builder()
                .instructions("How urgent?")
                .levels(levels)
                .build();
        ScoreQuestion scoreRepeated = ScoreQuestion.builder()
                .instructions("How urgent?")
                .level("low", OptionCriteria.builder().what("Can wait").build())
                .level("high", OptionCriteria.builder().what("Now").build())
                .build();

        Map<String, JudgeAnswer> answers = new LinkedHashMap<>();
        answers.put("greeting", JudgeAnswer.builder().noul(0.9).build());
        JudgeResponse responseFromMap = JudgeResponse.builder().answers(answers).build();
        JudgeResponse responseRepeated = JudgeResponse.builder()
                .answer("greeting", JudgeAnswer.builder().noul(0.9).build())
                .build();

        assertThat(fromMap).isEqualTo(repeated);
        assertThat(choiceFromMap).isEqualTo(choiceRepeated);
        assertThat(scoreFromMap).isEqualTo(scoreRepeated);
        assertThat(responseFromMap).isEqualTo(responseRepeated);
    }

    @Test
    void map_based_builder_overloads_tolerate_null_maps() {
        JudgeRequest request = JudgeRequest.builder()
                .state(Map.of("message", "hello"))
                .questions(null)
                .question("greeting", noulQuestion("Is this a greeting?"))
                .build();
        ChoiceQuestion choice = ChoiceQuestion.builder()
                .instructions("Which team?")
                .options(null)
                .option("billing", OptionCriteria.builder().what("Charges").build())
                .build();
        ScoreQuestion score = ScoreQuestion.builder()
                .instructions("How urgent?")
                .levels(null)
                .level("low", OptionCriteria.builder().what("Can wait").build())
                .level("high", OptionCriteria.builder().what("Now").build())
                .build();
        JudgeResponse response = JudgeResponse.builder()
                .answers(null)
                .answer("greeting", JudgeAnswer.builder().noul(0.9).build())
                .build();

        assertThat(request.questions()).containsOnlyKeys("greeting");
        assertThat(choice.options()).containsOnlyKeys("billing");
        assertThat(score.levels()).containsOnlyKeys("low", "high");
        assertThat(response.answers()).containsOnlyKeys("greeting");
    }

    private JudgeRequest sampleRequest() {
        return JudgeRequest.builder()
                .state(Map.of("message", "hello"))
                .question("greeting", noulQuestion("Is this a greeting?"))
                .build();
    }

    private JudgeResponse sampleResponse() {
        return JudgeResponse.builder()
                .answer("refund", JudgeAnswer.builder().noul(0.83).build())
                .build();
    }

    private NoulQuestion noulQuestion(String instructions) {
        return NoulQuestion.builder().instructions(instructions).build();
    }

    private ChoiceQuestion sampleChoiceQuestion() {
        return choiceQuestionWith("billing", "Charges and refunds");
    }

    private ChoiceQuestion choiceQuestionWithDifferentOptions() {
        return choiceQuestionWith("sales", "Pricing questions");
    }

    private ChoiceQuestion choiceQuestionWith(String option, String what) {
        return ChoiceQuestion.builder()
                .instructions("Which team should handle this?")
                .option(option, OptionCriteria.builder().what(what).build())
                .build();
    }

    private ScoreQuestion sampleScoreQuestion() {
        return scoreQuestionWith("low", "Can wait");
    }

    private ScoreQuestion scoreQuestionWithDifferentLevels() {
        return scoreQuestionWith("urgent", "Needs attention now");
    }

    private ScoreQuestion scoreQuestionWith(String level, String what) {
        return ScoreQuestion.builder()
                .instructions("How urgent is this?")
                .level(level, OptionCriteria.builder().what(what).build())
                .level("high", OptionCriteria.builder().what("Very urgent").build())
                .build();
    }
}
