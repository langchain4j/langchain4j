package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.UntypedAgent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpClientFactory;
import org.junit.jupiter.api.Test;

class AgentCardHttpClientTest {
    @Test
    void timeoutCancelsTheActualHttpExchange() {
        HttpClient delegate = mock(HttpClient.class);
        CompletableFuture<HttpResponse<String>> pending = new CompletableFuture<>();
        when(delegate.<String>sendAsync(any(), any())).thenReturn(pending);
        var client = new AgentCardHttpClient(delegate, () -> Duration.ofMillis(1));
        assertThatThrownBy(() -> client.send(
                        HttpRequest.newBuilder(URI.create("http://localhost")).build(),
                        HttpResponse.BodyHandlers.ofString()))
                .isInstanceOf(HttpTimeoutException.class);
        assertThat(pending).isCancelled();
    }

    @Test
    void customSdkHttpProviderIsPreserved() throws Exception {
        var provider = mock(A2AHttpClient.class);
        var get = mock(A2AHttpClient.GetBuilder.class, RETURNS_SELF);
        when(provider.createGet()).thenReturn(get);
        when(get.get()).thenThrow(new IOException("custom provider"));
        try (var factory = mockStatic(A2AHttpClientFactory.class)) {
            factory.when(A2AHttpClientFactory::create).thenReturn(provider);
            var agent = new DefaultA2AClientBuilder<>("http://localhost:1234", UntypedAgent.class)
                    .inputKeys("request")
                    .build();
            assertThatThrownBy(() -> ((A2AClientInstance) agent).agentCard())
                    .isInstanceOf(A2AAgentCardException.class)
                    .hasRootCauseMessage("custom provider");
        }
    }

    @Test
    void interruptionCancelsTheActualHttpExchange() {
        HttpClient delegate = mock(HttpClient.class);
        CompletableFuture<HttpResponse<String>> pending = new CompletableFuture<>();
        when(delegate.<String>sendAsync(any(), any())).thenReturn(pending);
        var client = new AgentCardHttpClient(delegate, () -> Duration.ofSeconds(10));
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> client.send(
                            HttpRequest.newBuilder(URI.create("http://localhost"))
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()))
                    .isInstanceOf(InterruptedException.class);
            assertThat(pending).isCancelled();
        } finally {
            Thread.interrupted();
        }
    }
}
