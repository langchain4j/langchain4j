package dev.langchain4j.code.azure.acads;

/**
 * Test Suite for SessionsREPLTool
 *
 * Testing Strategy:
 * -----------------
 * This test suite focuses on unit testing the SessionsREPLTool class which implements
 * the CodeExecutionEngine interface. The tests utilize a mock-based approach to isolate
 * the class under test from external dependencies like Azure services.
 *
 * Key test areas covered:
 * 1. Interface contract verification - Ensuring SessionsREPLTool correctly implements CodeExecutionEngine
 * 2. Input sanitization - Verifying code snippets are properly sanitized before execution
 * 3. HTTP request formation - Confirming requests to the Azure Container Apps service are properly formed
 * 4. Response handling - Testing that responses are correctly parsed and transformed
 *
 * Implementation approach:
 * - Uses a custom TestSessionsREPLTool subclass that allows dependency injection via reflection
 * - Mocks HTTP client responses to simulate Azure Container Apps service responses
 * - Uses ArgumentCaptor to inspect request details for verification
 *
 * This approach allows comprehensive testing of the SessionsREPLTool's functionality
 * without requiring actual Azure credentials or incurring costs from real service calls.
 */
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.code.CodeExecutionEngine;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

class SessionsREPLToolTest {

    private SessionsREPLTool sessionsREPLTool;
    private DefaultAzureCredential mockCredential;
    private HttpClient mockHttpClient;
    private static final String TEST_ENDPOINT = "https://test-endpoint.com";
    private static final String TEST_CODE = "print('Hello World')";
    private static final String TEST_TOKEN = "test-token";

    @BeforeEach
    void setUp() throws Exception {
        // Create mocks
        mockCredential = mock(DefaultAzureCredential.class);
        mockHttpClient = mock(HttpClient.class);

        // Set up the mock credential
        AccessToken accessToken =
                new AccessToken(TEST_TOKEN, OffsetDateTime.now().plusHours(1));
        when(mockCredential.getToken(any(TokenRequestContext.class))).thenReturn(Mono.just(accessToken));

        // Create a custom SessionsREPLTool with mocked dependencies
        sessionsREPLTool = new TestSessionsREPLTool(TEST_ENDPOINT, mockCredential, mockHttpClient);
    }

    @Test
    void testExecuteMethodImplementsCodeExecutionEngineInterface() {
        // Verify that SessionsREPLTool implements CodeExecutionEngine
        assertThat(sessionsREPLTool).isInstanceOf(CodeExecutionEngine.class);

        // Set up mock response for execute call
        setupMockHttpResponse();

        // Execute code using the CodeExecutionEngine interface method
        String result = sessionsREPLTool.execute(TEST_CODE);

        // Verify result contains expected JSON structure
        assertThat(result).contains("\"result\"");
        assertThat(result).contains("\"stdout\"");
        assertThat(result).contains("\"stderr\"");
    }

    @Test
    void testUseMethodCallsExecuteCode() {
        // Set up mock response
        setupMockHttpResponse();

        // Call the use method
        String result = sessionsREPLTool.use(TEST_CODE);

        // Verify HTTP request was made
        verify(mockHttpClient).execute(any(HttpRequest.class));

        // Verify result contains expected JSON structure
        assertThat(result).contains("\"result\"");
        assertThat(result).contains("\"stdout\"");
        assertThat(result).contains("\"stderr\"");
    }

    @Test
    void testExecuteCodeSanitizesInput() {
        // Set up mock response
        setupMockHttpResponse();

        // Input with whitespace and python prefix
        String inputWithPrefix = "```python\nprint('Hello World')\n```";

        // Execute code
        Map<String, Object> result = sessionsREPLTool.executeCode(inputWithPrefix);

        // Capture HTTP request to verify sanitized input
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttpClient).execute(requestCaptor.capture());

        // Verify that the request body contains the sanitized code
        String requestBody = requestCaptor.getValue().body();
        assertThat(requestBody).contains("print('Hello World')");
        assertThat(requestBody).doesNotContain("```python");
        assertThat(requestBody).doesNotContain("```");

        // Verify result structure
        assertThat(result).containsKey("result");
        assertThat(result).containsKey("stdout");
        assertThat(result).containsKey("stderr");
    }

    /**
     * Helper method to set up mock HTTP response
     */
    private void setupMockHttpResponse() {
        // Create mock response data
        Map<String, Object> responseProperties = new HashMap<>();
        responseProperties.put("result", "Hello World");
        responseProperties.put("stdout", "Hello World");
        responseProperties.put("stderr", "");

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("properties", responseProperties);

        // Set up mock HTTP response
        SuccessfulHttpResponse mockResponse = mock(SuccessfulHttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body())
                .thenReturn(new ObjectMapper().valueToTree(responseMap).toString());
        when(mockResponse.headers()).thenReturn(Collections.emptyMap());

        // Set up mock HTTP client
        when(mockHttpClient.execute(any(HttpRequest.class))).thenReturn(mockResponse);
    }

    /**
     * Custom SessionsREPLTool for testing that uses mocked dependencies
     */
    private static class TestSessionsREPLTool extends SessionsREPLTool {
        public TestSessionsREPLTool(
                String poolManagementEndpoint, DefaultAzureCredential credential, HttpClient mockHttpClient) {
            // Use the protected constructor for testing that accepts pre-configured dependencies
            super(poolManagementEndpoint, "test-session-id", true, mockHttpClient, credential);
        }
    }
}
