package dev.langchain4j.web.search.tavily;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretMaskingTest {

    @Test
    void engineBuilder_toString_should_mask_api_key() {
        String toString =
                TavilyWebSearchEngine.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void engineBuilder_toString_should_render_null_api_key_as_null() {
        String toString = TavilyWebSearchEngine.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }

    @Test
    void requestBuilder_toString_should_mask_api_key() {
        String toString =
                TavilySearchRequest.builder().apiKey("secret-api-key").toString();

        assertThat(toString).doesNotContain("secret-api-key").contains("apiKey=********");
    }

    @Test
    void requestBuilder_toString_should_render_null_api_key_as_null() {
        String toString = TavilySearchRequest.builder().toString();

        assertThat(toString).contains("apiKey=null");
    }
}
