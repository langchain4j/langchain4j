package dev.langchain4j.model.structureddecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredDecisionReceiptTest {

    @Test
    void captures_ordered_schema_and_answer_provenance_without_recording_state() {
        StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                .state("Private message")
                .question("department", ChoiceQuestion.builder()
                        .instructions("Choose a department")
                        .option("sales", OptionCriteria.builder().what("Sales").build())
                        .option("support", OptionCriteria.builder().what("Support").build())
                        .build())
                .question("risk", ScoreQuestion.builder()
                        .instructions("Rate risk")
                        .level("low", OptionCriteria.builder().what("Low").build())
                        .level("high", OptionCriteria.builder().what("High").build())
                        .build())
                .build();
        StructuredDecisionResponse response = StructuredDecisionResponse.builder()
                .answer("department", new ChoiceAnswer("support", 0.7,
                        ConfidenceProvenance.PROVIDER_REPORTED, Map.of("probabilities", Map.of("support", 0.7))))
                .build();

        StructuredDecisionReceipt receipt = StructuredDecisionReceipt.from(
                "routing", "v3", request, response);

        assertThat(receipt.schemaId()).isEqualTo("routing");
        assertThat(receipt.schemaVersion()).isEqualTo("v3");
        assertThat(receipt.questions()).extracting(StructuredDecisionReceipt.QuestionSnapshot::name)
                .containsExactly("department", "risk");
        assertThat(receipt.questions().get(0).optionIds()).containsExactly("sales", "support");
        assertThat(receipt.questions().get(1).optionIds()).containsExactly("low", "high");
        assertThat(receipt.answers()).containsKey("department").doesNotContainKey("risk");
        assertThat(receipt.answers().get("department").value()).isEqualTo("support");
        assertThat(receipt.answers().get("department").confidenceProvenance())
                .isEqualTo(ConfidenceProvenance.PROVIDER_REPORTED);
        assertThat(receipt.toString()).doesNotContain("Private message");
    }

    @Test
    void rejects_answers_not_in_the_request() {
        StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                .state("x")
                .question("known", NoulQuestion.builder().instructions("Valid?").build())
                .build();
        StructuredDecisionResponse response = StructuredDecisionResponse.builder()
                .answer("unknown", new NoulAnswer(0.5))
                .build();

        assertThatThrownBy(() -> StructuredDecisionReceipt.from("schema", "v1", request, response))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
        assertThatThrownBy(() -> StructuredDecisionReceipt.from(" ", "v1", request, response))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void receipt_copies_mutable_provider_answer_values() {
        StructuredDecisionRequest request = StructuredDecisionRequest.builder()
                .state("private")
                .question("custom", () -> "Find values")
                .build();
        List<String> values = new ArrayList<>(List.of("first"));
        StructuredDecisionAnswer answer = new StructuredDecisionAnswer() {
            @Override public Object value() { return values; }
            @Override public Double confidence() { return null; }
            @Override public ConfidenceProvenance confidenceProvenance() { return null; }
            @Override public Map<String, Object> metadata() { return Map.of(); }
        };
        StructuredDecisionResponse response = StructuredDecisionResponse.builder()
                .answer("custom", answer).build();

        StructuredDecisionReceipt receipt = StructuredDecisionReceipt.from("custom", "v1", request, response);
        values.add("second");

        List<String> captured = (List<String>) receipt.answers().get("custom").value();
        assertThat(captured).containsExactly("first");
        assertThatThrownBy(() -> captured.add("third")).isInstanceOf(UnsupportedOperationException.class);
    }
}
