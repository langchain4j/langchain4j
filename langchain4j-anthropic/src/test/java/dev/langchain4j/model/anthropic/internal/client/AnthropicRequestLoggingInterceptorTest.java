package dev.langchain4j.model.anthropic.internal.client;

import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.anthropic.internal.api.AnthropicApi.X_API_KEY;
import static dev.langchain4j.model.anthropic.internal.client.AnthropicRequestLoggingInterceptor.format;
import static dev.langchain4j.model.anthropic.internal.client.AnthropicRequestLoggingInterceptor.maskSecretKey;
import static org.assertj.core.api.Assertions.assertThat;

class AnthropicRequestLoggingInterceptorTest {

    @Test
    void should_mask_secret_headers() {

        assertThat(format("Authorization", null))
                .isEqualTo("[Authorization: null]");
        assertThat(format("Authorization", "1234567890"))
                .isEqualTo("[Authorization: 12345...90]");
        assertThat(format("authorization", "1234567890"))
                .isEqualTo("[authorization: 12345...90]");

        assertThat(format("x-api-key", null))
                .isEqualTo("[x-api-key: null]");
        assertThat(format("x-api-key", "1234567890"))
                .isEqualTo("[x-api-key: 12345...90]");
        assertThat(format("X-API-KEY", "1234567890"))
                .isEqualTo("[X-API-KEY: 12345...90]");

        assertThat(format("X-Auth-Token", null))
                .isEqualTo("[X-Auth-Token: null]");
        assertThat(format("X-Auth-Token", "1234567890"))
                .isEqualTo("[X-Auth-Token: 12345...90]");
        assertThat(format("x-auth-token", "1234567890"))
                .isEqualTo("[x-auth-token: 12345...90]");

        assertThat(format(X_API_KEY, null))
                .isEqualTo("[x-api-key: null]");
        assertThat(format(X_API_KEY, "1234567890"))
                .isEqualTo("[x-api-key: 12345...90]");
    }

    @Test
    void should_mask_secret() {

        assertThat(maskSecretKey(null)).isNull();
        assertThat(maskSecretKey("")).isEqualTo("");
        assertThat(maskSecretKey(" ")).isEqualTo(" ");
        assertThat(maskSecretKey("key")).isEqualTo("...");
        assertThat(maskSecretKey("sk-1234567890")).isEqualTo("sk-12...90");
    }
}