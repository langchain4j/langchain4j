package dev.langchain4j.store.embedding.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.embedding.Embedding;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenSearchUserAgentTest {

    private static final String BULK_RESPONSE =
            "{\"took\":1,\"errors\":false,\"items\":[{\"index\":{\"_index\":\"test-index\",\"_id\":\"1\","
                    + "\"_version\":1,\"result\":\"created\",\"status\":201}}]}";

    private HttpServer server;
    private final AtomicReference<String> userAgent = new AtomicReference<>();

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            if ("HEAD".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                byte[] body = BULK_RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.getResponseBody().close();
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void should_set_default_user_agent() {
        OpenSearchEmbeddingStore store = OpenSearchEmbeddingStore.builder()
                .serverUrl("http://" + InetAddress.getLoopbackAddress().getHostAddress() + ":"
                        + server.getAddress().getPort())
                .indexName("test-index")
                .build();

        store.add(Embedding.from(new float[] {1f, 2f, 3f}));

        assertThat(userAgent.get()).isEqualTo("LangChain4j");
    }
}
