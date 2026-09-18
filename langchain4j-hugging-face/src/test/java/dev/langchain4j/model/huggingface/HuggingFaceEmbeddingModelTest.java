package dev.langchain4j.model.huggingface;

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

class HuggingFaceEmbeddingModelTest {

    @Test
    void should_build_with_custom_base_url_missing_trailing_slash() {
        // A custom base URL with a path but no trailing slash (e.g. the inference router URL
        // copied without the final '/') is rejected by Retrofit with "baseUrl must end in /",
        // so the client must normalize it.
        assertThatNoException()
                .isThrownBy(() -> HuggingFaceEmbeddingModel.builder()
                        .accessToken("hf_test_token")
                        .modelId("sentence-transformers/all-MiniLM-L6-v2")
                        .baseUrl("https://router.huggingface.co/hf-inference")
                        .build());
    }

    @Test
    void should_build_with_default_base_url() {
        assertThatNoException()
                .isThrownBy(() -> HuggingFaceEmbeddingModel.builder()
                        .accessToken("hf_test_token")
                        .modelId("sentence-transformers/all-MiniLM-L6-v2")
                        .build());
    }
}
