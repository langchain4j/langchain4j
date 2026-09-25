package dev.langchain4j.model.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenDecisionTypesTest {

    @Test
    void a_provider_can_add_a_question_and_answer_without_changing_core() {
        Question question = () -> "Find all matching spans";
        DecisionAnswer answer = new CustomAnswer("first match");

        DecisionRequest request = DecisionRequest.builder()
                .state("some text")
                .question("matches", question)
                .build();
        DecisionResponse response =
                DecisionResponse.builder().answer("matches", answer).build();

        assertThat(request.questions().get("matches")).isSameAs(question);
        assertThat(response.answers().get("matches").value()).isEqualTo("first match");
    }

    private record CustomAnswer(String value) implements DecisionAnswer {
        @Override
        public Double confidence() {
            return null;
        }

        @Override
        public ConfidenceProvenance confidenceProvenance() {
            return null;
        }

        @Override
        public Map<String, Object> metadata() {
            return Map.of();
        }
    }
}
