package dev.langchain4j.model.huggingface;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.huggingface.client.EmbeddingRequest;
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
}
