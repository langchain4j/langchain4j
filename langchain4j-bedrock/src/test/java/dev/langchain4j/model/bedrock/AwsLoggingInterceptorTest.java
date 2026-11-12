package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AwsLoggingInterceptorTest {

    @Test
    void should_redact_authorization_header() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put(
                "Authorization",
                List.of("AWS4-HMAC-SHA256 Credential=AKIAEXAMPLE/20240101/us-east-1/bedrock/aws4_request, "
                        + "SignedHeaders=host;x-amz-date, Signature=deadbeefdeadbeef"));
        headers.put("Content-Type", List.of("application/json"));

        String result = AwsLoggingInterceptor.maskHeaders(headers);

        assertThat(result).contains("Authorization=[REDACTED]");
        assertThat(result).doesNotContain("AKIAEXAMPLE");
        assertThat(result).doesNotContain("deadbeefdeadbeef");
        assertThat(result).contains("Content-Type=[application/json]");
    }

    @Test
    void should_redact_session_token_case_insensitively() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("x-amz-security-token", List.of("FwoGZXIvYXdzEXAMPLESESSIONTOKEN"));

        String result = AwsLoggingInterceptor.maskHeaders(headers);

        assertThat(result).contains("x-amz-security-token=[REDACTED]");
        assertThat(result).doesNotContain("EXAMPLESESSIONTOKEN");
    }

    @Test
    void should_redact_authorization_regardless_of_case() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("AUTHORIZATION", List.of("super-secret-value"));

        String result = AwsLoggingInterceptor.maskHeaders(headers);

        assertThat(result).contains("AUTHORIZATION=[REDACTED]");
        assertThat(result).doesNotContain("super-secret-value");
    }

    @Test
    void should_preserve_non_sensitive_headers() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("X-Amz-Date", List.of("20240101T000000Z"));
        headers.put("Host", List.of("bedrock-runtime.us-east-1.amazonaws.com"));

        String result = AwsLoggingInterceptor.maskHeaders(headers);

        assertThat(result).contains("X-Amz-Date=[20240101T000000Z]");
        assertThat(result).contains("Host=[bedrock-runtime.us-east-1.amazonaws.com]");
    }

    @Test
    void should_preserve_multiple_values_for_non_sensitive_headers() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("Accept", List.of("application/json", "text/event-stream"));

        String result = AwsLoggingInterceptor.maskHeaders(headers);

        assertThat(result).contains("Accept=[application/json, text/event-stream]");
    }

    @Test
    void should_handle_empty_map() {
        assertThat(AwsLoggingInterceptor.maskHeaders(Map.of())).isEqualTo("{}");
    }

    @Test
    void should_handle_null_map() {
        assertThat(AwsLoggingInterceptor.maskHeaders(null)).isEqualTo("{}");
    }

    @Test
    void should_mask_custom_headers_that_look_like_credentials() {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        headers.put("X-Api-Key", List.of("gateway-key"));
        headers.put("X-Tenant-Token", List.of("tenant-token"));
        headers.put("Proxy-Authorization", List.of("Basic dXNlcjpwYXNz"));
        headers.put("X-Correlation-Id", List.of("123"));

        String result = AwsLoggingInterceptor.maskHeaders(headers);

        assertThat(result)
                .contains("X-Api-Key=[REDACTED]")
                .contains("X-Tenant-Token=[REDACTED]")
                .contains("Proxy-Authorization=[REDACTED]")
                .contains("X-Correlation-Id=[123]")
                .doesNotContain("gateway-key")
                .doesNotContain("tenant-token")
                .doesNotContain("dXNlcjpwYXNz");
    }
}

