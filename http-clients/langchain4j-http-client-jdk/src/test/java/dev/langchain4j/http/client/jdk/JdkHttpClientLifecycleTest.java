package dev.langchain4j.http.client.jdk;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import dev.langchain4j.http.client.HttpRequest;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class JdkHttpClientLifecycleTest {

    @Test
    void should_close_underlying_client_when_supported() throws Exception {
        CloseableHttpClient delegate = mock(CloseableHttpClient.class);
        JdkHttpClient client = client(delegate);

        client.close();

        verify((AutoCloseable) delegate).close();
    }

    @Test
    void should_report_close_failure() throws Exception {
        CloseableHttpClient delegate = mock(CloseableHttpClient.class);
        Exception failure = new Exception("close failed");
        doAnswer(invocation -> {
                    throw failure;
                })
                .when((AutoCloseable) delegate)
                .close();

        assertThatThrownBy(client(delegate)::close)
                .isInstanceOf(IOException.class)
                .hasCause(failure);
    }

    @Test
    void should_preserve_interrupt_status_when_close_is_interrupted() throws Exception {
        CloseableHttpClient delegate = mock(CloseableHttpClient.class);
        doAnswer(invocation -> {
                    throw new InterruptedException("interrupted");
                })
                .when((AutoCloseable) delegate)
                .close();

        try {
            assertThatThrownBy(client(delegate)::close)
                    .isInstanceOf(IOException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void should_close_default_client_only_on_supported_java_versions() throws IOException {
        WireMockServer server =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        try (JdkHttpClient client = JdkHttpClient.builder().build()) {
            server.stubFor(WireMock.get("/endpoint").willReturn(WireMock.ok("hello")));
            HttpRequest request = HttpRequest.builder()
                    .method(GET)
                    .url(server.baseUrl() + "/endpoint")
                    .build();
            assertThat(client.execute(request).body()).isEqualTo("hello");

            client.close();

            if (Runtime.version().feature() >= 21) {
                assertThatThrownBy(() -> client.execute(request)).hasCauseInstanceOf(IOException.class);
            } else {
                assertThat(client.execute(request).body()).isEqualTo("hello");
            }
        } finally {
            server.stop();
        }
    }

    private static JdkHttpClient client(java.net.http.HttpClient delegate) {
        java.net.http.HttpClient.Builder builder = mock(java.net.http.HttpClient.Builder.class);
        when(builder.build()).thenReturn(delegate);
        return JdkHttpClient.builder().httpClientBuilder(builder).build();
    }

    abstract static class CloseableHttpClient extends java.net.http.HttpClient implements AutoCloseable {}
}
