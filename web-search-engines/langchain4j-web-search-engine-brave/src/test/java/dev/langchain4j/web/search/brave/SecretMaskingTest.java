package dev.langchain4j.web.search.brave;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretMaskingTest {

    @Test
    void engineBuilder_toString_should_mask_api_key() {
        String toString =
                BraveWebSearchEngine.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void engineBuilder_toString_should_render_null_api_key_as_null() {
        String toString = BraveWebSearchEngine.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }

    @Test
    void clientBuilder_toString_should_mask_api_key() {
        String toString = BraveClient.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void clientBuilder_toString_should_render_null_api_key_as_null() {
        String toString = BraveClient.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
