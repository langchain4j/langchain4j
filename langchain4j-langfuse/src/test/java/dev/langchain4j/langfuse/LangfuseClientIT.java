package dev.langchain4j.langfuse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.langchain4j.langfuse.model.*;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LangfuseClientTest {

    private static final String TEST_ENDPOINT = "http://test.langfuse.com";
    private static final String TEST_PUBLIC_KEY = "public-key";
    private static final String TEST_SECRET_KEY = "secret-key";

    private LangfuseClient client;

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private HttpResponse<String> mockResponse;

    @BeforeEach
    void setUp() {
        client = LangfuseClient.builder()
                .endpoint(TEST_ENDPOINT)
                .publicKey(TEST_PUBLIC_KEY)
                .secretKey(TEST_SECRET_KEY)
                .build();
    }

    @Test
    void createTrace_Success() throws IOException {
        // Arrange
        Trace trace = createSampleTrace();
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        assertDoesNotThrow(() -> client.trace().create(trace));
    }

    @Test
    void createTrace_Failed() throws IOException {
        // Arrange
        Trace trace = createSampleTrace();
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("Bad Request");
        when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        assertThrows(IOException.class, () -> client.trace().create(trace));
    }

    @Test
    void createObservationBatch_Success() throws IOException {
        // Arrange
        List<Observation> observations =
                Arrays.asList(createSampleObservation("obs1"), createSampleObservation("obs2"));
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        assertDoesNotThrow(() -> client.observation().createBatch(observations));
    }

    @Test
    void createSession_Success() throws IOException {
        // Arrange
        Session session = createSampleSession();
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        assertDoesNotThrow(() -> client.session().create(session));
    }

    @Test
    void createScore_Success() throws IOException {
        // Arrange
        Score score = createSampleScore();
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        assertDoesNotThrow(() -> client.score().create(score));
    }

    @Test
    void updateTrace_Success() throws IOException {
        // Arrange
        String traceId = "trace-123";
        Map<String, String> output = new HashMap<>();
        output.put("key", "value");
        String status = "completed";

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        assertDoesNotThrow(() -> client.trace().update(traceId, output, status));
    }

    @Test
    void addTraceToSession_Success() throws IOException {
        // Arrange
        String sessionId = "session-123";
        String traceId = "trace-123";

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockHttpClient.send(any(), any())).thenReturn(mockResponse);

        // Act & Assert
        assertDoesNotThrow(() -> client.session().addTrace(sessionId, traceId));
    }

    // Helper methods to create test objects
    private Trace createSampleTrace() {
        return Trace.builder()
                .id("trace-123")
                .name("Test Trace")
                .userId("user-123")
                .build();
    }

    private Observation createSampleObservation(String id) {
        return Observation.builder()
                .id(id)
                .name("Test Observation")
                .traceId("trace-123")
                .build();
    }

    private Session createSampleSession() {
        return Session.builder()
                .id("session-123")
                .name("Test Session")
                .userId("user-123")
                .build();
    }

    private Score createSampleScore() {
        return Score.builder()
                .id("score-123")
                .name("Test Score")
                .value(0.95)
                .traceId("trace-123")
                .build();
    }

    @Test
    void builder_DefaultEndpoint() {
        // Act
        LangfuseClient client = LangfuseClient.builder()
                .publicKey(TEST_PUBLIC_KEY)
                .secretKey(TEST_SECRET_KEY)
                .build();

        // Assert
        assertEquals("https://cloud.langfuse.com", client.getEndpoint());
    }

    @Test
    void builder_CustomEndpoint() {
        // Act
        LangfuseClient client = LangfuseClient.builder()
                .endpoint(TEST_ENDPOINT)
                .publicKey(TEST_PUBLIC_KEY)
                .secretKey(TEST_SECRET_KEY)
                .build();

        // Assert
        assertEquals(TEST_ENDPOINT, client.getEndpoint());
    }

    @Test
    void close_Success() {
        // Act & Assert
        assertDoesNotThrow(() -> client.close());
    }
}
