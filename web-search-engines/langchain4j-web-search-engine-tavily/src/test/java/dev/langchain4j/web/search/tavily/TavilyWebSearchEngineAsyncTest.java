package dev.langchain4j.web.search.tavily;

import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic (no API key) coverage of {@link TavilyWebSearchEngine#searchAsync}'s cancellation contract:
 * cancelling the returned future must abort the in-flight HTTP call. A raw server socket accepts the connection but
 * never responds; when the OkHttp call is cancelled it closes the socket, which the server observes as EOF. Without
 * cancellation reaching the HTTP call the socket would stay open and the assertion would time out.
 */
class TavilyWebSearchEngineAsyncTest {

    @Test
    void searchAsync_cancellation_aborts_the_in_flight_http_call() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CountDownLatch accepted = new CountDownLatch(1);
            CompletableFuture<Boolean> clientDisconnected = new CompletableFuture<>();

            Thread serverThread = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    accepted.countDown();
                    InputStream in = socket.getInputStream();
                    byte[] buffer = new byte[1024];
                    while (in.read(buffer) != -1) {
                        // drain until the cancelled OkHttp call closes the socket
                    }
                    clientDisconnected.complete(true);
                } catch (IOException e) {
                    clientDisconnected.complete(true); // a socket reset also means the client aborted
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            TavilyWebSearchEngine engine = TavilyWebSearchEngine.builder()
                    .baseUrl("http://localhost:" + server.getLocalPort())
                    .apiKey("test-key")
                    .build();

            CompletableFuture<WebSearchResults> future = engine.searchAsync(WebSearchRequest.from("What is LangChain4j?"));

            assertThat(accepted.await(5, SECONDS)).isTrue();
            assertThat(future).isNotDone();

            future.cancel(true);

            // cancelling the returned future aborts the in-flight HTTP call (R3)
            assertThat(clientDisconnected.get(5, SECONDS)).isTrue();
        }
    }
}
