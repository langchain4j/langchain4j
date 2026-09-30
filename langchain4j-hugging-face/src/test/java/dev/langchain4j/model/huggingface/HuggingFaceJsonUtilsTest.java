package dev.langchain4j.model.huggingface;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
import dev.langchain4j.model.huggingface.client.Options;
import dev.langchain4j.model.huggingface.client.Parameters;
import dev.langchain4j.model.huggingface.client.TextGenerationRequest;
import java.util.List;
import org.junit.jupiter.api.Test;

class HuggingFaceJsonUtilsTest {

    @Test
    void should_serialize_embedding_request_with_wait_for_model_enabled() {
        String json = HuggingFaceJsonUtils.toJson(new EmbeddingRequest(List.of("hello"), true));

        assertThat(json).isEqualTo("{\"inputs\":[\"hello\"],\"options\":{\"wait_for_model\":true}}");
    }

    @Test
    void should_serialize_embedding_request_with_wait_for_model_disabled() {
        String json = HuggingFaceJsonUtils.toJson(new EmbeddingRequest(List.of("hello"), false));

        assertThat(json).isEqualTo("{\"inputs\":[\"hello\"],\"options\":{\"wait_for_model\":false}}");
    }

    @Test
    void should_serialize_use_cache() {
        String json = HuggingFaceJsonUtils.toJson(
                Options.builder().waitForModel(true).useCache(false).build());

        assertThat(json).isEqualTo("{\"wait_for_model\":true,\"use_cache\":false}");
    }

    @Test
    void should_omit_null_options() {
        String json = HuggingFaceJsonUtils.toJson(Options.builder().build());

        assertThat(json).isEqualTo("{\"wait_for_model\":true}");
    }

    @Test
    @SuppressWarnings("removal")
    void should_serialize_text_generation_request_parameters() {
        TextGenerationRequest request = TextGenerationRequest.builder()
                .inputs("hello")
                .parameters(Parameters.builder()
                        .temperature(0.7)
                        .maxNewTokens(20)
                        .returnFullText(false)
                        .build())
                .options(Options.builder().waitForModel(true).build())
                .build();

        String json = HuggingFaceJsonUtils.toJson(request);

        assertThat(json)
                .isEqualTo("{\"inputs\":\"hello\","
                        + "\"parameters\":{\"temperature\":0.7,\"max_new_tokens\":20,\"return_full_text\":false},"
                        + "\"options\":{\"wait_for_model\":true}}");
    }
}
