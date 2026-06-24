package dev.langchain4j.model.jina.internal.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JinaClientBuilderTest {

    @Test
    void toString_should_mask_api_key() {
        String toString = JinaClient.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void toString_should_render_null_api_key_as_null() {
        String toString = JinaClient.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
