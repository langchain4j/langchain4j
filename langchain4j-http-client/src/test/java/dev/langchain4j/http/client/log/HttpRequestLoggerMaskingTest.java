package dev.langchain4j.http.client.log;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.http.client.HttpRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class HttpRequestLoggerMaskingTest {

    private static final String SECRET = "sk-1234567890abcdefgh";
    private static final String MASKED_SECRET = "sk-12...gh";

    // ---------- headers ----------

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
                "x-functions-key",
                "x-portkey-virtual-key",
                "Idempotency-Key",
                "X-Auth-Token",
                "X-Amz-Security-Token",
                "X-Amz-Credential",
                "X-Amz-Signature",
                "X-Client-Secret",
                "X-Password",
                "Cookie",
                "Set-Cookie",
                "Mcp-Session-Id",
                "X-Session"
            })
    void should_mask_credential_carrying_header(String headerKey) {
        String formatted = HttpRequestLogger.format(headerKey, List.of(SECRET));

        assertThat(formatted).isEqualTo("[" + headerKey + ": " + MASKED_SECRET + "]").doesNotContain(SECRET);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Content-Type",
                "Accept",
                "User-Agent",
                "Keep-Alive",
                "Last-Event-ID",
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

        assertThat(formatted).isEqualTo("[Set-Cookie: [sk-12...gh, sessi...ba]]").doesNotContain(SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "secret", "sk-1234567890abcdef"})
    void should_fully_mask_secret_header_value_shorter_than_20_characters(String value) {
        String formatted = HttpRequestLogger.format("Authorization", List.of(value));

        assertThat(formatted).isEqualTo("[Authorization: ...]");
    }

    @Test
    void should_partially_show_secret_header_value_of_20_characters() {
        String formatted = HttpRequestLogger.format("Authorization", List.of("Bearer 0123456789abc"));

        assertThat(formatted).isEqualTo("[Authorization: Beare...bc]");
    }

    // ---------- URL ----------

    @Test
    void should_mask_user_info_in_url() {
        assertThat(HttpRequestLogger.maskUrl("https://user:p4ssw0rd@example.com:8080/v1/chat?a=b"))
                .isEqualTo("https://***@example.com:8080/v1/chat?a=b");
    }

    @Test
    void should_mask_user_info_containing_at_sign_in_url() {
        assertThat(HttpRequestLogger.maskUrl("http://user:p@ss@localhost/path"))
                .isEqualTo("http://***@localhost/path");
    }

    @Test
    void should_mask_user_info_containing_unencoded_hash_in_url() {
        assertThat(HttpRequestLogger.maskUrl("https://user:se#cret@example.com/v1/chat"))
                .isEqualTo("https://***@example.com/v1/chat");
    }

    @Test
    void should_not_treat_at_sign_after_host_as_user_info() {
        String url = "https://example.com/users/@me?email=a@b.c";

        assertThat(HttpRequestLogger.maskUrl(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "key",
                "api_key",
                "apikey",
                "api-key",
                "token",
                "access_token",
                "refresh_token",
                "id_token",
                "secret",
                "client_secret",
                "password",
                "sig",
                "signature",
                "code",
                "auth",
                "authorization",
                "KEY",
                "Api_Key",
                "apiKey",
                "subscription-key",
                "X-Amz-Signature",
                "X-Amz-Credential",
                "X-Amz-Security-Token",
                "pageToken",
                "max_tokens"
            })
    void should_mask_value_of_secret_query_parameter(String name) {
        String url = "https://example.com/v1/models?" + name + "=" + SECRET;

        assertThat(HttpRequestLogger.maskUrl(url))
                .isEqualTo("https://example.com/v1/models?" + name + "=" + MASKED_SECRET)
                .doesNotContain(SECRET);
    }

    @Test
    void should_mask_only_secret_query_parameters_and_keep_the_rest_of_the_url() {
        String url = "https://example.com/search?q=cats&key=abc123&num=10&sig=xyz#top";

        assertThat(HttpRequestLogger.maskUrl(url)).isEqualTo("https://example.com/search?q=cats&key=...&num=10&sig=...#top");
    }

    @ParameterizedTest
    @ValueSource(strings = {"q", "alt", "api-version", "num", "codec", "zipcode", "signed"})
    void should_not_mask_query_parameter_that_carries_no_credential(String name) {
        String url = "https://example.com/path?" + name + "=visible-value";

        assertThat(HttpRequestLogger.maskUrl(url)).isEqualTo(url);
    }

    @Test
    void should_leave_url_without_user_info_and_query_unchanged() {
        String url = "http://localhost:11434/api/chat";

        assertThat(HttpRequestLogger.maskUrl(url)).isEqualTo(url);
    }

    // ---------- body ----------

    @ParameterizedTest
    @ValueSource(
            strings = {
                "api_key",
                "apikey",
                "api-key",
                "apiKey",
                "password",
                "secret",
                "client_secret",
                "token",
                "access_token",
                "refresh_token",
                "id_token",
                "API_KEY",
                "Password"
            })
    void should_mask_value_of_secret_json_field(String name) {
        String body = "{\"query\":\"cats\",\"" + name + "\":\"" + SECRET + "\",\"max_results\":5}";

        assertThat(HttpRequestLogger.maskBody(body))
                .isEqualTo("{\"query\":\"cats\",\"" + name + "\":\"" + MASKED_SECRET + "\",\"max_results\":5}")
                .doesNotContain(SECRET);
    }

    @Test
    void should_mask_secret_json_fields_with_whitespace_nesting_and_escaped_quotes() {
        String body = """
                {
                  "auth" : { "password" : "pa\\"ss" },
                  "api_key": "%s"
                }""".formatted(SECRET);

        assertThat(HttpRequestLogger.maskBody(body)).isEqualTo("""
                {
                  "auth" : { "password" : "..." },
                  "api_key": "%s"
                }""".formatted(MASKED_SECRET));
    }

    @Test
    void should_not_mask_json_fields_whose_name_only_contains_a_secret_name() {
        String body =
                "{\"model\":\"gpt-4o\",\"max_tokens\":100,\"tokens\":\"visible\",\"token_count\":\"visible\","
                        + "\"max_completion_tokens\":50,\"secrets_manager\":\"visible\"}";

        assertThat(HttpRequestLogger.maskBody(body)).isEqualTo(body);
    }

    @Test
    void should_not_mask_secret_json_field_whose_value_is_not_a_string() {
        String body = "{\"token\":null,\"password\":{\"type\":\"string\"}}";

        assertThat(HttpRequestLogger.maskBody(body)).isEqualTo(body);
    }

    @Test
    void should_not_mask_secret_name_used_as_json_value() {
        String body = "{\"name\":\"token\",\"content\":\"what is an api_key?\"}";

        assertThat(HttpRequestLogger.maskBody(body)).isEqualTo(body);
    }

    @Test
    void should_mask_unterminated_secret_json_value_up_to_the_end() {
        String body = "{\"api_key\":\"" + SECRET;

        assertThat(HttpRequestLogger.maskBody(body)).isEqualTo("{\"api_key\":\"" + MASKED_SECRET);
    }

    @Test
    void should_mask_value_of_secret_form_field() {
        String body = "grant_type=client_credentials&client_id=my-app&client_secret=" + SECRET
                + "&password=hunter2&max_tokens=10";

        assertThat(HttpRequestLogger.maskBody(body))
                .isEqualTo("grant_type=client_credentials&client_id=my-app&client_secret=" + MASKED_SECRET
                        + "&password=...&max_tokens=10");
    }

    @Test
    void should_mask_secret_form_field_at_start_of_body() {
        assertThat(HttpRequestLogger.maskBody("token=abc&scope=read")).isEqualTo("token=...&scope=read");
    }

    @Test
    void should_not_apply_form_field_rule_to_json_body() {
        String body = "{\"content\":\"open https://example.com/?a=1&token=abc and summarize it\"}";

        assertThat(HttpRequestLogger.maskBody(body)).isEqualTo(body);
    }

    @Test
    void should_mask_large_body_with_long_values() {
        String largeValue = "A".repeat(5_000_000);
        String escapedQuotes = "\\\"".repeat(100_000);
        String body = "{\"image\":\"" + largeValue + "\",\"token\":\"" + escapedQuotes + "\",\"api_key\":\""
                + largeValue + "\"}";

        String masked = HttpRequestLogger.maskBody(body);

        String expectedEnd = "\",\"token\":\"\\\"\\\"\\...\\\"\",\"api_key\":\"AAAAA...AA\"}";
        String expectedStart = "{\"image\":\"" + largeValue;
        // compares parts of the 10 MB strings so that a failure does not print them whole
        assertThat(masked.length()).isEqualTo(expectedStart.length() + expectedEnd.length());
        assertThat(masked.startsWith(expectedStart)).isTrue();
        assertThat(masked.substring(expectedStart.length())).isEqualTo(expectedEnd);
    }

    @Test
    void should_keep_empty_and_null_body() {
        assertThat(HttpRequestLogger.maskBody(null)).isNull();
        assertThat(HttpRequestLogger.maskBody("")).isEmpty();
    }

    // ---------- whole request ----------

    @Test
    void should_mask_url_headers_and_body_when_logging_request() {
        HttpRequest request = HttpRequest.builder()
                .method(POST)
                .url("https://user:pass@example.com/v1/search")
                .addQueryParam("api_key", SECRET)
                .addHeader("x-functions-key", SECRET)
                .addHeader("Content-Type", "application/json")
                .body("{\"api_key\":\"" + SECRET + "\",\"max_tokens\":100}")
                .build();
        Logger log = mock(Logger.class);

        HttpRequestLogger.log(log, request);

        ArgumentCaptor<Object> arguments = ArgumentCaptor.forClass(Object.class);
        verify(log).info(anyString(), arguments.capture(), arguments.capture(), arguments.capture(), arguments.capture());
        verifyNoMoreInteractions(log);
        assertThat(arguments.getAllValues())
                .containsExactly(
                        POST,
                        "https://***@example.com/v1/search?api_key=" + MASKED_SECRET,
                        "[Content-Type: application/json], [x-functions-key: " + MASKED_SECRET + "]",
                        "{\"api_key\":\"" + MASKED_SECRET + "\",\"max_tokens\":100}");
        assertThat(arguments.getAllValues().toString()).doesNotContain(SECRET);
    }
}
