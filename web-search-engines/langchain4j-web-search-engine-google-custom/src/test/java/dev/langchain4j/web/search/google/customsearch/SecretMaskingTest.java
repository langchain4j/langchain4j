package dev.langchain4j.web.search.google.customsearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretMaskingTest {

    @Test
    void apiClientBuilder_toString_should_mask_api_key() {
        String toString =
                GoogleCustomSearchApiClient.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void apiClientBuilder_toString_should_render_null_api_key_as_null() {
        String toString = GoogleCustomSearchApiClient.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }

    @Test
    void engineBuilder_toString_should_mask_api_key() {
        String toString =
                GoogleCustomWebSearchEngine.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void engineBuilder_toString_should_render_null_api_key_as_null() {
        String toString = GoogleCustomWebSearchEngine.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
