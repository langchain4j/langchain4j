package dev.langchain4j.model.scoring.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringRequestTest {

    @Test
    void builds_and_exposes_fields() {
        ScoringRequestParameters parameters =
                ScoringRequestParameters.builder().modelName("m").build();

        ScoringRequest request = ScoringRequest.builder()
                .documents(List.of("a", "b"))
                .query("q")
                .parameters(parameters)
                .build();

        assertThat(request.documents()).containsExactly("a", "b");
        assertThat(request.query()).isEqualTo("q");
        assertThat(request.parameters()).isEqualTo(parameters);
        assertThat(request.modelName()).isEqualTo("m");
    }

    @Test
    void defaults_parameters_to_empty() {
        ScoringRequest request =
                ScoringRequest.builder().documents(List.of("a")).query("q").build();

        assertThat(request.parameters()).isEqualTo(ScoringRequestParameters.EMPTY);
        assertThat(request.modelName()).isNull();
    }

    @Test
    void requires_documents_and_query() {
        assertThatThrownBy(() -> ScoringRequest.builder().query("q").build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScoringRequest.builder().documents(List.of("a")).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equals_hashCode_and_toString() {
        ScoringRequest a =
                ScoringRequest.builder().documents(List.of("a")).query("q").build();
        ScoringRequest b =
                ScoringRequest.builder().documents(List.of("a")).query("q").build();
        ScoringRequest different =
                ScoringRequest.builder().documents(List.of("x")).query("q").build();

        assertThat(a).isEqualTo(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(different);
        assertThat(a.equals(null)).isFalse();
        assertThat(a.equals("not a request")).isFalse();
        assertThat(a).hasToString(b.toString());
        assertThat(a.toString()).contains("ScoringRequest", "documents", "query");
    }

    @Test
    void parameters_overrideWith_empty_and_defaults() {
        ScoringRequestParameters base = ScoringRequestParameters.builder().modelName("base").build();
        ScoringRequestParameters override = ScoringRequestParameters.builder().modelName("override").build();

        assertThat(base.overrideWith(override).modelName()).isEqualTo("override");
        assertThat(base.overrideWith(ScoringRequestParameters.EMPTY).modelName()).isEqualTo("base");
        assertThat(base.overrideWith(null)).isSameAs(base);

        assertThat(ScoringRequestParameters.EMPTY.modelName()).isNull();
    }

    @Test
    void parameters_equals_hashCode_and_toString() {
        ScoringRequestParameters a = ScoringRequestParameters.builder().modelName("m").build();
        ScoringRequestParameters b = ScoringRequestParameters.builder().modelName("m").build();
        ScoringRequestParameters different = ScoringRequestParameters.builder().modelName("other").build();

        assertThat(a).isEqualTo(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(different);
        assertThat(a.equals(null)).isFalse();
        assertThat(a.equals("not params")).isFalse();
        assertThat(a.toString()).contains("modelName", "m");
    }
}
