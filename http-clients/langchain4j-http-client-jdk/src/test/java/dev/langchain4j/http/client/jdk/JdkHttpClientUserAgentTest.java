package dev.langchain4j.http.client.jdk;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdkHttpClientUserAgentTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedUserAgent = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/test", exchange -> {
            capturedUserAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            byte[] response = new byte[0];
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void should_send_langchain4j_user_agent_by_default() throws Exception {
        JdkHttpClient.builder().build().execute(request(null));

        assertThat(capturedUserAgent.get()).isEqualTo("LangChain4j");
    }

    @Test
    void should_allow_caller_to_override_user_agent() throws Exception {
        JdkHttpClient.builder().build().execute(request("my-app/1.0"));

        assertThat(capturedUserAgent.get()).isEqualTo("my-app/1.0");
    }

    private HttpRequest request(String userAgent) {
        HttpRequest.Builder builder =
                HttpRequest.builder().method(HttpMethod.GET).url(baseUrl + "/test");
        if (userAgent != null) {
            builder.addHeader("User-Agent", userAgent);
        }
        return builder.build();
    }
}
