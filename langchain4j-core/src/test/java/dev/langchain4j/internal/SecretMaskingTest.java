package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SecretMaskingTest {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Authorization",
                "Proxy-Authorization",
                "X-API-Key",
                "x-goog-api-key",
                "api_key",
                "apiKey",
                "Ocp-Apim-Subscription-Key",
                "subscription-key",
                "X-Auth-Token",
                "access_token",
                "pageToken",
                "X-Amz-Security-Token",
                "X-Amz-Credential",
                "X-Amz-Signature",
                "client_secret",
                "password",
                "Cookie",
                "Mcp-Session-Id"
            })
    void should_treat_name_containing_a_keyword_as_secret(String name) {
        assertThat(SecretMasking.isSecretName(name)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Content-Type", "Accept", "Keep-Alive", "api-version", "alt", "q", "OpenAI-Organization"})
    void should_not_treat_other_names_as_secret(String name) {
        assertThat(SecretMasking.isSecretName(name)).isFalse();
    }

    @Test
    @Execution(ExecutionMode.SAME_THREAD)
    void should_match_names_independently_of_default_locale() {
        // Without Locale.ROOT, "X-SESSION-ID".toLowerCase() yields "x-sessıon-ıd" under tr, which does not contain
        // "session"
        Locale defaultLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(SecretMasking.isSecretName("X-SESSION-ID")).isTrue();
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }

    @ParameterizedTest
    @CsvSource({
        "a, ...",
        "sk-1234567890abcdef, ...", // 19 characters
        "sk-1234567890abcdefg, sk-12...fg", // 20 characters
        "Bearer sk-proj-0123456789abcdefXy, Beare...Xy"
    })
    void should_mask_value(String value, String expected) {
        assertThat(SecretMasking.mask(value)).isEqualTo(expected);
    }

    @Test
    void should_keep_null_and_blank_value() {
        assertThat(SecretMasking.mask(null)).isNull();
        assertThat(SecretMasking.mask("")).isEmpty();
        assertThat(SecretMasking.mask(" ")).isEqualTo(" ");
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "https://user:p4ssw0rd@example.com:8080/v1/chat?a=b | https://***@example.com:8080/v1/chat?a=b",
                "http://user:p@ss@localhost/path | http://***@localhost/path",
                "https://user:se#cret@host/agents | https://***@host/agents",
                "https://user:se?cret@host/agents | https://***@host/agents",
                "https://user:secret#part@remote-host/agents | https://***@remote-host/agents",
                "user:secret@host/path | ***@host/path",
                "https://example.com/users/@me?email=a@b.c | https://example.com/users/@me?email=a@b.c",
                "http://localhost:11434/api/chat | http://localhost:11434/api/chat",
                "file:///tmp/page.html | file:///tmp/page.html"
            })
    void should_mask_user_info(String url, String expected) {
        assertThat(SecretMasking.maskUserInfo(url)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "https://user:p4ssw0rd@example.com:8080/v1/agents?key=secret#top | https://example.com:8080/v1/agents",
                "https://user:secret#part@remote-host/agents | https://remote-host/agents",
                "https://user:se?cret@host/agents | https://host/agents",
                "https://user:p ss@host/a | https://host/a",
                "user:secret@host/path?token=abc | host/path",
                "https://example.com/users/@me?email=a@b.c | https://example.com/users/@me",
                "localhost:8080 | localhost:8080",
                "https://example.com/page | https://example.com/page"
            })
    void should_remove_credentials_from_url(String url, String expected) {
        assertThat(SecretMasking.withoutCredentials(url)).isEqualTo(expected);
    }

    @Test
    void should_err_towards_masking_when_query_without_path_contains_at_sign() {
        // Without a path, an '@' in the query cannot be told apart from a password containing an unencoded '?'
        assertThat(SecretMasking.withoutCredentials("https://host?email=a@b")).isEqualTo("https://b");
    }

    @Test
    void should_keep_null_url() {
        assertThat(SecretMasking.withoutCredentials(null)).isNull();
    }
}
