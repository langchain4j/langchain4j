package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.genai.Client;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class GoogleGenAiClientFactoryTest {

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
    void should_send_gemini_api_requests_to_v1beta() throws IOException {
        List<String> paths = new CopyOnWriteArrayList<>();
        HttpServer server = recordingServer(paths);
        try (Client client =
                GoogleGenAiClientFactory.createClient("test-api-key", null, null, null, null, null, baseUrl(server))) {

            generateContent(client);

            assertThat(paths).containsExactly("/v1beta/models/gemini-model:generateContent");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void should_keep_the_default_api_version_of_vertex_ai() throws IOException {
        List<String> paths = new CopyOnWriteArrayList<>();
        HttpServer server = recordingServer(paths);
        GoogleCredentials credentials =
                GoogleCredentials.create(new AccessToken("test-token", new Date(System.currentTimeMillis() + 3600000)));
        try (Client client = GoogleGenAiClientFactory.createClient(
                null, credentials, "test-project-id", "us-central1", null, null, baseUrl(server))) {

            generateContent(client);

            assertThat(paths).singleElement().asString().startsWith("/v1beta1/");
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer recordingServer(List<String> paths) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"error\":{\"code\":400,\"message\":\"stubbed\"}}".getBytes();
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void generateContent(Client client) {
        try {
            client.models.generateContent("gemini-model", "Hello", null);
        } catch (RuntimeException ignored) {
        }
    }
}
