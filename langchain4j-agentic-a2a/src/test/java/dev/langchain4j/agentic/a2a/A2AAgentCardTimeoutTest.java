package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agentic.AgenticServices;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class A2AAgentCardTimeoutTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stalledCardFetchTimesOutAndCanBeRetried(boolean headersSent) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        var release = new CountDownLatch(1);
        var requests = new AtomicInteger();
        server.createContext("/", exchange -> {
            try {
                if (requests.incrementAndGet() == 1) {
                    if (headersSent) {
                        exchange.sendResponseHeaders(200, 128);
                    }
                    release.await(5, TimeUnit.SECONDS);
                } else {
                    byte[] body = ("{\"name\":\"remote\",\"description\":\"remote agent\",\"version\":\"1.0\","
                                    + "\"url\":\"http://localhost\",\"capabilities\":{},\"defaultInputModes\":[\"text\"],"
                                    + "\"defaultOutputModes\":[\"text\"],\"skills\":[],\"supportedInterfaces\":[]}")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            var agent = AgenticServices.a2aBuilder(
                            "http://127.0.0.1:" + server.getAddress().getPort())
                    .inputKeys("request")
                    .agentCardTimeout(Duration.ofMillis(500))
                    .build();
            assertThat(requests).hasValue(0); // construction stays lazy
            var client = (A2AClientInstance) agent;
            assertThatThrownBy(client::agentCard)
                    .isInstanceOf(A2AAgentCardException.class)
                    .hasRootCauseInstanceOf(HttpTimeoutException.class);
            assertThat(client.agentCard().name()).isEqualTo("remote");
            assertThat(client.agentCard().name()).isEqualTo("remote");
            assertThat(requests).hasValue(2); // a failure is retryable, a success is cached
        } finally {
            release.countDown();
            server.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidTimeouts() {
        var builder = AgenticServices.a2aBuilder("http://localhost:1234");
        assertThatThrownBy(() -> builder.agentCardTimeout(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.agentCardTimeout(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder.agentCardTimeout(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
