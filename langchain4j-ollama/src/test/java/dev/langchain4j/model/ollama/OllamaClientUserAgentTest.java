package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OllamaClientUserAgentTest {

    private HttpServer server;
    private AtomicReference<Map<String, String>> capturedHeaders;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        capturedHeaders = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/tags", exchange -> {
            Map<String, String> headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name.toLowerCase(), values.get(0)));
            capturedHeaders.set(headers);

            byte[] body = "{\"models\": []}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void should_set_user_agent_header() {
        OllamaClient ollamaClient = OllamaClient.builder()
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(5))
                .build();

        ollamaClient.listModels();

        Map<String, String> headers = capturedHeaders.get();
        assertThat(headers).containsEntry("user-agent", "LangChain4j");
    }
}
