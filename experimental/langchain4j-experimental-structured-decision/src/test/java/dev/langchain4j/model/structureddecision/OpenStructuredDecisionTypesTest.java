package dev.langchain4j.model.structureddecision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenStructuredDecisionTypesTest {

    @Test
    void a_provider_can_add_a_question_and_answer_without_changing_core() {
        Question question = () -> "Find all matching spans";
        StructuredDecisionAnswer answer = new CustomAnswer("first match");

        StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                .state("some text")
                .question("matches", question)
                .build();
        StructuredDecisionResponse response =
                StructuredDecisionResponse.builder().answer("matches", answer).build();

        assertThat(request.questions().get("matches")).isSameAs(question);
        assertThat(response.answers().get("matches").value()).isEqualTo("first match");
    }

    private record CustomAnswer(String value) implements StructuredDecisionAnswer {
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
