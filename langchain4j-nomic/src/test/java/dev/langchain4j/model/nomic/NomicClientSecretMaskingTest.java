package dev.langchain4j.model.nomic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NomicClientSecretMaskingTest {

    @Test
    void builder_toString_should_mask_api_key() {
        String toString = NomicClient.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void builder_toString_should_render_null_api_key_as_null() {
        String toString = NomicClient.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
