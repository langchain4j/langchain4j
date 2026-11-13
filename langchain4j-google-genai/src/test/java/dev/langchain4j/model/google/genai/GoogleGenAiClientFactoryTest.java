package dev.langchain4j.model.google.genai;

import static dev.langchain4j.model.google.genai.GoogleGenAiClientFactory.isVertexAiOrApiVersionSetIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GoogleGenAiClientFactoryTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> paths = new ArrayList<>();
    private final List<String> userAgents = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            synchronized (this) {
                paths.add(exchange.getRequestURI().getPath());
                userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            }
            byte[] body = "{\"error\":{\"code\":404,\"message\":\"not found\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void generateContent(Client client) {
        assertThatThrownBy(() -> client.models.generateContent("gemini-model", "Hi", null));
    }

    @Test
    void should_create_client_with_api_key() {
        Client client = GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, null, null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void should_create_client_with_timeout() {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, null, null, Duration.ofSeconds(30), null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void should_create_client_without_timeout() {
        Client client = GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, null, null, null);

        assertThat(client).isNotNull();
    }

    @Test
    void should_create_client_with_vertex_project_and_location() {
        GoogleCredentials credentials =
                GoogleCredentials.create(new AccessToken("test-token", new Date(System.currentTimeMillis() + 3600000)));
        Client client = GoogleGenAiClientFactory.createClient(
                null, credentials, "test-project-id", "us-central1", null, null, null);

        assertThat(client).isNotNull();
        assertThat(client.vertexAI()).isTrue();
        assertThat(client.project()).isEqualTo("test-project-id");
        assertThat(client.location()).isEqualTo("us-central1");
    }

    @Test
    void should_create_client_with_vertex_and_api_key() {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, "test-project-id", "us-central1", null, null, null);

        assertThat(client).isNotNull();
        assertThat(client.vertexAI()).isTrue();
        assertThat(client.project()).isEqualTo("test-project-id");
        assertThat(client.location()).isEqualTo("us-central1");
        assertThat(client.apiKey()).isEqualTo("test-api-key");
    }

    @Test
    void should_keep_v1beta_on_gemini_developer_api() {
        assumeFalse(isVertexAiOrApiVersionSetIn(System::getenv));
        Client client = GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, null, null, baseUrl);

        generateContent(client);

        assertThat(paths).containsExactly("/v1beta/models/gemini-model:generateContent");
    }

    @Test
    void should_leave_api_version_to_sdk_when_vertex_ai_or_api_version_is_set_in_environment() {
        assertThat(isVertexAiOrApiVersionSetIn(Map.<String, String>of()::get)).isFalse();
        assertThat(isVertexAiOrApiVersionSetIn(Map.of("GOOGLE_GENAI_USE_VERTEXAI", "true")::get))
                .isTrue();
        assertThat(isVertexAiOrApiVersionSetIn(Map.of("GOOGLE_GENAI_USE_VERTEXAI", "TRUE")::get))
                .isTrue();
        assertThat(isVertexAiOrApiVersionSetIn(Map.of("GOOGLE_GENAI_USE_VERTEXAI", "false")::get))
                .isFalse();
        assertThat(isVertexAiOrApiVersionSetIn(Map.of("GOOGLE_GENAI_USE_ENTERPRISE", "true")::get))
                .isTrue();
        assertThat(isVertexAiOrApiVersionSetIn(
                        Map.of("GOOGLE_GENAI_USE_ENTERPRISE", "false", "GOOGLE_GENAI_USE_VERTEXAI", "true")::get))
                .isFalse();
        assertThat(isVertexAiOrApiVersionSetIn(Map.of("GOOGLE_GENAI_API_VERSION", "v1")::get))
                .isTrue();
    }

    @Test
    void should_add_langchain4j_to_user_agent() {
        Client client = GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, null, null, baseUrl);

        generateContent(client);

        assertThat(userAgents).hasSize(1);
        assertThat(userAgents.get(0)).startsWith("google-genai-sdk/").endsWith(" LangChain4j");
    }

    @ParameterizedTest
    @ValueSource(strings = {"User-Agent", "user-agent", "USER-AGENT"})
    void should_send_custom_user_agent_instead_of_langchain4j(String headerName) {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, null, null, null, Map.of(headerName, "MyApp/1.0"), baseUrl);

        generateContent(client);

        assertThat(userAgents).hasSize(1);
        assertThat(userAgents.get(0)).endsWith(" MyApp/1.0").doesNotContain("LangChain4j");
    }

    @Test
    void should_use_the_configured_api_version() {
        Client client = GoogleGenAiClientFactory.createClient(
                "test-api-key", null, null, null, null, null, baseUrl, "v1");

        generateContent(client);

        assertThat(paths).containsExactly("/v1/models/gemini-model:generateContent");
    }

    @Test
    void should_pass_the_api_version_of_the_builder_to_the_client() {
        GoogleGenAiChatModel chatModel = GoogleGenAiChatModel.builder()
                .apiKey("test-api-key")
                .apiEndpoint(baseUrl)
                .apiVersion("v1")
                .modelName("gemini-model")
                .maxRetries(0)
                .build();
        GoogleGenAiEmbeddingModel embeddingModel = GoogleGenAiEmbeddingModel.builder()
                .apiKey("test-api-key")
                .apiEndpoint(baseUrl)
                .apiVersion("v1")
                .modelName("embedding-model")
                .maxRetries(0)
                .build();

        assertThatThrownBy(() -> chatModel.chat("Hi"));
        assertThatThrownBy(() -> embeddingModel.embed("Hi"));

        assertThat(paths)
                .containsExactly(
                        "/v1/models/gemini-model:generateContent", "/v1/models/embedding-model:batchEmbedContents");
    }
}
