package dev.langchain4j.model.anthropic;

import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.anthropic.AnthropicRequestLoggingInterceptor.maskApiKey;
import static org.assertj.core.api.Assertions.assertThat;

class AnthropicRequestLoggingInterceptorTest {

    @Test
    void should_mask_api_key() {

        assertThat(maskApiKey(null)).isNull();
        assertThat(maskApiKey("")).isEqualTo("");
        assertThat(maskApiKey(" ")).isEqualTo(" ");
        assertThat(maskApiKey("key")).isEqualTo("...");
        assertThat(maskApiKey("sk-1234567890")).isEqualTo("sk-12...90");
    }
}