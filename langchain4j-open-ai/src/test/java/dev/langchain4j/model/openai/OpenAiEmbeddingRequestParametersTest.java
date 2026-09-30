package dev.langchain4j.model.openai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenAiEmbeddingRequestParametersTest {

    @Test
    void carries_openai_specific_and_common_parameters() {
        OpenAiEmbeddingRequestParameters params = OpenAiEmbeddingRequestParameters.builder()
                .modelName("text-embedding-3-small")
                .dimensions(256)
                .user("user-1")
                .encodingFormat("float")
                .customParameters(Map.of("input_type", "passage"))
                .build();

        assertThat(params.modelName()).isEqualTo("text-embedding-3-small");
        assertThat(params.dimensions()).isEqualTo(256);
        assertThat(params.user()).isEqualTo("user-1");
        assertThat(params.encodingFormat()).isEqualTo("float");
        assertThat(params.customParameters()).containsEntry("input_type", "passage");

        assertThat(params.presentParameters())
                .containsExactlyInAnyOrder(
                        EmbeddingRequestParameters.MODEL_NAME,
                        EmbeddingRequestParameters.DIMENSIONS,
                        OpenAiEmbeddingRequestParameters.USER,
                        OpenAiEmbeddingRequestParameters.ENCODING_FORMAT,
                        OpenAiEmbeddingRequestParameters.CUSTOM_PARAMETERS);
    }

    @Test
    void customParameter_merges_into_the_map() {
        OpenAiEmbeddingRequestParameters params = OpenAiEmbeddingRequestParameters.builder()
                .customParameter("input_type", "query")
                .customParameter("dimensions_hint", 512)
                .build();

        assertThat(params.customParameters())
                .containsEntry("input_type", "query")
                .containsEntry("dimensions_hint", 512);
    }

    @Test
    void unset_parameters_are_absent() {
        OpenAiEmbeddingRequestParameters params =
                OpenAiEmbeddingRequestParameters.builder().user("u").build();

        assertThat(params.presentParameters()).containsExactly(OpenAiEmbeddingRequestParameters.USER);
        assertThat(params.encodingFormat()).isNull();
        assertThat(params.customParameters()).isNull();
        assertThat(params.inputType()).isNull(); // common accessor, not populated
    }

    @Test
    void override_with_merges_common_and_specific() {
        EmbeddingRequestParameters defaults = OpenAiEmbeddingRequestParameters.builder()
                .modelName("m")
                .user("u")
                .build();
        EmbeddingRequestParameters perCall = OpenAiEmbeddingRequestParameters.builder()
                .encodingFormat("base64")
                .inputType(EmbeddingInputType.QUERY)
                .build();

        EmbeddingRequestParameters merged = defaults.overrideWith(perCall);

        assertThat(merged).isInstanceOf(OpenAiEmbeddingRequestParameters.class);
        assertThat(merged.modelName()).isEqualTo("m");
        assertThat(merged.parameter(OpenAiEmbeddingRequestParameters.USER)).isEqualTo("u");
        assertThat(merged.parameter(OpenAiEmbeddingRequestParameters.ENCODING_FORMAT))
                .isEqualTo("base64");
        assertThat(merged.inputType()).isEqualTo(EmbeddingInputType.QUERY);
    }

    @Test
    void override_with_empty_or_null_returns_same_instance() {
        OpenAiEmbeddingRequestParameters params =
                OpenAiEmbeddingRequestParameters.builder().user("u").build();

        assertThat(params.overrideWith(null)).isSameAs(params);
        assertThat(params.overrideWith(EmbeddingRequestParameters.EMPTY)).isSameAs(params);
    }
}
