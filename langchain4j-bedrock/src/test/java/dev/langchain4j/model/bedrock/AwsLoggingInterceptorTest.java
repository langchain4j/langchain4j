package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import software.amazon.awssdk.core.SdkRequest;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.SdkHttpRequest;

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

    @ParameterizedTest
    @ValueSource(
            strings = {
                "x-api-key",
                "X-Functions-Key",
                "x-portkey-virtual-key",
                "X-Auth-Token",
                "X-Client-Secret",
                "X-Password",
                "X-Amz-Credential",
                "X-Signature",
                "X-Session-Id",
                "Cookie"
            })
    void should_redact_custom_header_whose_name_suggests_a_credential(String headerName) {
        Map<String, List<String>> headers = Map.of(headerName, List.of("super-secret-value"));

        String result = AwsLoggingInterceptor.maskHeaders(headers);

        assertThat(result).isEqualTo("{" + headerName + "=[REDACTED]}");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Content-Type",
                "User-Agent",
                "X-Amz-Date",
                "X-Amz-Content-Sha256",
                "amz-sdk-invocation-id",
                "amz-sdk-request"
            })
    void should_preserve_header_whose_name_does_not_suggest_a_credential(String headerName) {
        Map<String, List<String>> headers = Map.of(headerName, List.of("some-value"));

        String result = AwsLoggingInterceptor.maskHeaders(headers);

        assertThat(result).isEqualTo("{" + headerName + "=[some-value]}");
    }

    @Test
    void should_redact_custom_credential_header_when_logging_request() {
        SdkHttpFullRequest request = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.POST)
                .uri(URI.create("https://bedrock-runtime.us-east-1.amazonaws.com/model/test/converse"))
                .putHeader("Content-Type", "application/json")
                .build();
        SdkHttpRequest requestWithCustomHeaders = new BedrockCustomHeadersInterceptor(
                        () -> Map.of("x-api-key", "custom-gateway-secret"))
                .modifyHttpRequest(contextFor(request), new ExecutionAttributes());
        Logger logger = mock(Logger.class);

        new AwsLoggingInterceptor(true, false, logger)
                .beforeTransmission(contextFor(requestWithCustomHeaders), new ExecutionAttributes());

        ArgumentCaptor<Object> arguments = ArgumentCaptor.forClass(Object.class);
        verify(logger)
                .debug(
                        anyString(),
                        arguments.capture(),
                        arguments.capture(),
                        arguments.capture(),
                        arguments.capture(),
                        arguments.capture());
        assertThat(arguments.getAllValues().get(2))
                .asString()
                .contains("x-api-key=[REDACTED]")
                .contains("Content-Type=[application/json]");
        assertThat(arguments.getAllValues().toString()).doesNotContain("custom-gateway-secret");
    }

    private static Context.BeforeTransmission contextFor(SdkHttpRequest httpRequest) {
        return new Context.BeforeTransmission() {
            @Override
            public SdkRequest request() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SdkHttpRequest httpRequest() {
                return httpRequest;
            }

            @Override
            public Optional<RequestBody> requestBody() {
                return Optional.empty();
            }

            @Override
            public Optional<AsyncRequestBody> asyncRequestBody() {
                return Optional.empty();
            }
        };
    }
}
