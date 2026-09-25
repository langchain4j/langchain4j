package dev.langchain4j.model.structureddecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StructuredDecisionResponseTest {

    @Test
    void exposes_vendor_response_metadata_without_modeling_vendor_fields() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("latency_ms", 42);
        StructuredDecisionResponse response = StructuredDecisionResponse.builder()
                .answer("q", new NoulAnswer(0.8))
                .metadata(metadata)
                .build();

        metadata.clear();
        assertThat(response.metadata()).containsExactly(Map.entry("latency_ms", 42));
        assertThatThrownBy(() -> response.metadata().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void maps_each_typed_answer_and_defensively_copies_the_answer_map() {
        StructuredDecisionAnswer noul = new NoulAnswer(0.83);
        StructuredDecisionAnswer choice = new ChoiceAnswer("billing", 0.91, ConfidenceProvenance.PROVIDER_REPORTED, Map.of());
        StructuredDecisionAnswer score = new ScoreAnswer(1.7, 0.64, ConfidenceProvenance.PROVIDER_REPORTED, Map.of());
        Map<String, StructuredDecisionAnswer> answers = new LinkedHashMap<>();
        answers.put("refund", noul);
        answers.put("team", choice);
        answers.put("urgency", score);

        StructuredDecisionResponse response =
                StructuredDecisionResponse.builder().answers(answers).build();
        answers.clear();

        assertThat(response.answers())
                .containsExactly(Map.entry("refund", noul), Map.entry("team", choice), Map.entry("urgency", score));
        assertThat(response.answers().get("refund").value()).isEqualTo(0.83);
        assertThat(response.answers().get("refund").confidence()).isNull();
        assertThat(response.answers().get("team").value()).isEqualTo("billing");
        assertThat(response.answers().get("urgency").value()).isEqualTo(1.7);
        assertThatThrownBy(() -> response.answers().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void answer_requires_a_value() {
        assertThatThrownBy(() -> new NoulAnswer(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChoiceAnswer(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validates_probabilities_and_response_entries() {
        assertThatThrownBy(() -> new NoulAnswer(1.01))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        new NoulAnswer(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChoiceAnswer("x", -0.01, ConfidenceProvenance.PROVIDER_REPORTED, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChoiceAnswer("x", Double.NaN, ConfidenceProvenance.PROVIDER_REPORTED, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                        StructuredDecisionResponse.builder().answers(Map.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("answers");
    }

    @Test
    void answer_metadata_is_copied_and_confidence_has_explicit_provenance() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("probabilities", Map.of("billing", 0.91));
        ChoiceAnswer answer = new ChoiceAnswer("billing", 0.91,
                ConfidenceProvenance.PROVIDER_REPORTED, metadata);
        metadata.clear();

        assertThat(answer.metadata()).containsKey("probabilities");
        assertThatThrownBy(() -> answer.metadata().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(answer.confidenceProvenance()).isEqualTo(ConfidenceProvenance.PROVIDER_REPORTED);
        assertThatThrownBy(() -> new ChoiceAnswer("billing", 0.91, null, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChoiceAnswer("billing", null,
                ConfidenceProvenance.PROVIDER_REPORTED, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nested_answer_metadata_is_an_immutable_snapshot() {
        Map<String, Object> probabilities = new LinkedHashMap<>();
        probabilities.put("billing", 0.8);
        ChoiceAnswer answer = new ChoiceAnswer("billing", null, null,
                Map.of("probabilities", probabilities));
        probabilities.put("billing", 0.1);

        Map<String, Object> captured = (Map<String, Object>) answer.metadata().get("probabilities");
        assertThat(captured).containsEntry("billing", 0.8);
        assertThatThrownBy(() -> captured.put("billing", 0.2))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
