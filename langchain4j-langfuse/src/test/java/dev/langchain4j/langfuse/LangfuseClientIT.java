package dev.langchain4j.langfuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.langfuse.model.Observation;
import dev.langchain4j.langfuse.model.ObservationType;
import dev.langchain4j.langfuse.model.Score;
import dev.langchain4j.langfuse.model.Session;
import dev.langchain4j.langfuse.model.Trace;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LangfuseClientTest {

    private static final String BASE_URL = "https://test.langfuse.com";
    private static final String PUBLIC_KEY = "public-key";
    private static final String SECRET_KEY = "secret-key";

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpClientBuilder httpClientBuilder;

    @Mock
    private SuccessfulHttpResponse successResponse;

    private LangfuseClient client;

    @BeforeEach
    void setUp() {
        httpClientBuilder = mock(HttpClientBuilder.class);
        when(httpClientBuilder.connectTimeout(any(Duration.class))).thenReturn(httpClientBuilder);
        when(httpClientBuilder.readTimeout(any(Duration.class))).thenReturn(httpClientBuilder);
        when(httpClientBuilder.build()).thenReturn(httpClient);
        client = LangfuseClient.builder()
                .baseUrl(BASE_URL)
                .publicKey(PUBLIC_KEY)
                .secretKey(SECRET_KEY)
                .httpClientBuilder(httpClientBuilder)
                .build();
    }

    @Test
    void shouldCreateTrace() throws IOException {
        // Given
        when(httpClient.execute(any(HttpRequest.class))).thenReturn(successResponse);
        when(successResponse.statusCode()).thenReturn(200);

        Trace trace = Trace.builder()
                .name("test-trace")
                .input("test-key", "test-value")
                .build();

        // When
        client.trace().create(trace);

        // Then
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).execute(requestCaptor.capture());

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.url()).isEqualTo(BASE_URL + "/api/public/traces");
        assertThat(capturedRequest.method().name()).isEqualTo("POST");

        Map<String, List<String>> expectedHeaders = new HashMap<>();
        expectedHeaders.put("Authorization", Collections.singletonList("Bearer " + SECRET_KEY));
        expectedHeaders.put("Content-Type", Collections.singletonList("application/json"));

        assertThat(capturedRequest.headers()).containsAllEntriesOf(expectedHeaders);
    }

    @Test
    void shouldCreateObservationBatch() throws IOException {
        // Given
        when(httpClient.execute(any(HttpRequest.class))).thenReturn(successResponse);
        when(successResponse.statusCode()).thenReturn(200);

        Observation observation = Observation.builder()
                .name("test-observation")
                .traceId("test-trace-id")
                .type(ObservationType.EVENT)
                .build();

        // When
        client.observation().createBatch(Arrays.asList(observation));

        // Then
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).execute(requestCaptor.capture());

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.url()).isEqualTo(BASE_URL + "/api/public/observations/batch");
        assertThat(capturedRequest.method().name()).isEqualTo("POST");
    }

    @Test
    void shouldCreateSession() throws IOException {
        // Given
        when(httpClient.execute(any(HttpRequest.class))).thenReturn(successResponse);
        when(successResponse.statusCode()).thenReturn(200);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("test-key", "test-value");

        Session session =
                Session.builder().setName("test-session").addMetadata(metadata).build();

        // When
        client.session().create(session);

        // Then
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).execute(requestCaptor.capture());

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.url()).isEqualTo(BASE_URL + "/api/public/sessions");
        assertThat(capturedRequest.method().name()).isEqualTo("POST");
    }

    @Test
    void shouldCreateScore() throws IOException {
        // Given
        when(httpClient.execute(any(HttpRequest.class))).thenReturn(successResponse);
        when(successResponse.statusCode()).thenReturn(200);

        Score score = Score.builder()
                .name("test-score")
                .value(0.95)
                .traceId("test-trace-id")
                .build();

        // When
        client.score().create(score);

        // Then
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).execute(requestCaptor.capture());

        HttpRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.url()).isEqualTo(BASE_URL + "/api/public/scores");
        assertThat(capturedRequest.method().name()).isEqualTo("POST");
    }

    @Test
    void shouldHandleErrorResponse() throws IOException {
        // Given
        when(httpClient.execute(any(HttpRequest.class))).thenReturn(successResponse);
        when(successResponse.statusCode()).thenReturn(400);
        when(successResponse.body()).thenReturn("Bad Request");

        Trace trace = Trace.builder().name("test-trace").build();

        // Then
        assertThatThrownBy(() -> client.trace().create(trace))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to create trace");
    }

    @Test
    void shouldCloseHttpClientWhenShutdown() throws Exception {
        // Given
        HttpClient mockCloseableHttpClient = mock(HttpClient.class);
        when(httpClientBuilder.build()).thenReturn(mockCloseableHttpClient);

        LangfuseClient clientWithCloseableHttp = LangfuseClient.builder()
                .baseUrl(BASE_URL)
                .publicKey(PUBLIC_KEY)
                .secretKey(SECRET_KEY)
                .httpClientBuilder(httpClientBuilder)
                .build();

        // When
        clientWithCloseableHttp.shutdown();
    }
}
