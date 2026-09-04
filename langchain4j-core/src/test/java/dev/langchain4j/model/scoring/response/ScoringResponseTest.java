package dev.langchain4j.model.scoring.response;

import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringResponseTest {

    @Test
    void builds_from_modelName_and_tokenUsage() {
        TokenUsage tokenUsage = new TokenUsage(5);

        ScoringResponse response = ScoringResponse.builder()
                .scores(List.of(0.5, 0.7))
                .modelName("m")
                .tokenUsage(tokenUsage)
                .build();

        assertThat(response.scores()).containsExactly(0.5, 0.7);
        assertThat(response.modelName()).isEqualTo("m");
        assertThat(response.tokenUsage()).isEqualTo(tokenUsage);
        assertThat(response.metadata().modelName()).isEqualTo("m");
        assertThat(response.metadata().tokenUsage()).isEqualTo(tokenUsage);
    }

    @Test
    void builds_from_explicit_metadata() {
        ScoringResponseMetadata metadata = ScoringResponseMetadata.builder()
                .modelName("m")
                .tokenUsage(new TokenUsage(1))
                .build();

        ScoringResponse response =
                ScoringResponse.builder().scores(List.of(0.5)).metadata(metadata).build();

        assertThat(response.metadata()).isEqualTo(metadata);
    }

    @Test
    void rejects_both_metadata_and_flat_fields() {
        ScoringResponseMetadata metadata = ScoringResponseMetadata.builder().build();

        assertThatThrownBy(() -> ScoringResponse.builder()
                        .scores(List.of(0.5))
                        .metadata(metadata)
                        .modelName("m")
                        .build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals_hashCode_and_toString() {
        ScoringResponse a = ScoringResponse.builder().scores(List.of(0.5)).modelName("m").build();
        ScoringResponse b = ScoringResponse.builder().scores(List.of(0.5)).modelName("m").build();
        ScoringResponse different = ScoringResponse.builder().scores(List.of(0.9)).modelName("m").build();

        assertThat(a).isEqualTo(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(different);
        assertThat(a.equals(null)).isFalse();
        assertThat(a.equals("not a response")).isFalse();
        assertThat(a).hasToString(b.toString());
        assertThat(a.toString()).contains("ScoringResponse", "scores", "metadata");
    }

    @Test
    void metadata_equals_hashCode_and_toString() {
        ScoringResponseMetadata a = ScoringResponseMetadata.builder().modelName("m").build();
        ScoringResponseMetadata b = ScoringResponseMetadata.builder().modelName("m").build();
        ScoringResponseMetadata different = ScoringResponseMetadata.builder().modelName("other").build();

        assertThat(a).isEqualTo(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(different);
        assertThat(a.equals(null)).isFalse();
        assertThat(a.equals("not metadata")).isFalse();
        assertThat(a.toString()).contains("modelName", "m");
    }
}
