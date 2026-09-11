package dev.langchain4j.http.client.log;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpRequestLoggerMaskingTest {

    private static final String SECRET = "sk-1234567890abcdef";

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Authorization",
                "Proxy-Authorization",
                "Authentication",
                "WWW-Authenticate",
                "X-API-Key",
                "x-goog-api-key",
                "X-Api_Key",
                "ApiKey",
                "Ocp-Apim-Subscription-Key",
                "X-Auth-Token",
                "X-Amz-Security-Token",
                "X-Amz-Credential",
                "X-Client-Secret",
                "X-Password",
                "Cookie",
                "Set-Cookie"
            })
    void should_mask_credential_carrying_header(String headerKey) {
        String formatted = HttpRequestLogger.format(headerKey, List.of(SECRET));

        assertThat(formatted).isEqualTo("[" + headerKey + ": sk-12...ef]").doesNotContain(SECRET);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Content-Type",
                "Accept",
                "User-Agent",
                "Idempotency-Key",
                "Last-Event-ID",
                "Mcp-Session-Id",
                "OpenAI-Organization",
                "X-Request-Id"
            })
    void should_not_mask_header_that_carries_no_credential(String headerKey) {
        String formatted = HttpRequestLogger.format(headerKey, List.of("some-value"));

        assertThat(formatted).isEqualTo("[" + headerKey + ": some-value]");
    }

    @Test
    void should_mask_every_value_of_a_multi_valued_secret_header() {
        String formatted = HttpRequestLogger.format("Set-Cookie", List.of(SECRET, "session=0987654321fedcba"));

        assertThat(formatted).isEqualTo("[Set-Cookie: [sk-12...ef, sessi...ba]]").doesNotContain(SECRET);
    }
}
