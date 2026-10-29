package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.time.Duration;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

class StreamableHttpMcpTransportTest {

    @Test
    void shouldUseDefaultConnectionTimeoutForDefaultBackend() throws Exception {
        try (StreamableHttpMcpTransport transport =
                StreamableHttpMcpTransport.builder().url("http://localhost/mcp").build()) {
            assertThat(extractHttpClient(transport).connectTimeout()).contains(Duration.ofSeconds(60));
        }
    }

    @Test
    void shouldApplyExplicitConnectionTimeoutToDefaultBackend() throws Exception {
        try (StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost/mcp")
                .timeout(Duration.ofSeconds(13))
                .build()) {
            assertThat(extractHttpClient(transport).connectTimeout()).contains(Duration.ofSeconds(13));
        }
    }

    @Test
    void shouldPreserveConnectionTimeoutOnUnderlyingJdkBuilder() throws Exception {
        try (StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost/mcp")
                .httpClientBuilder(JdkHttpClient.builder()
                        .httpClientBuilder(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))))
                .build()) {
            assertThat(extractHttpClient(transport).connectTimeout()).contains(Duration.ofSeconds(5));
        }
    }

    @Test
    void shouldOverrideUnderlyingJdkBuilderTimeoutWhenTransportTimeoutIsExplicit() throws Exception {
        try (StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost/mcp")
                .httpClientBuilder(JdkHttpClient.builder()
                        .httpClientBuilder(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))))
                .timeout(Duration.ofSeconds(13))
                .build()) {
            assertThat(extractHttpClient(transport).connectTimeout()).contains(Duration.ofSeconds(13));
        }
    }

    @Test
    void shouldApplyCustomSslContext() throws Exception {
        SSLContext customContext = SSLContext.getInstance("TLS");
        customContext.init(null, null, null);

        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost/mcp")
                .sslContext(customContext)
                .build();

        assertThat(extractSslContext(transport)).isSameAs(customContext);
    }

    @Test
    void shouldUseHttp2ByDefault() throws Exception {
        StreamableHttpMcpTransport transport =
                StreamableHttpMcpTransport.builder().url("http://localhost/mcp").build();

        assertThat(extractHttpClient(transport).version()).isEqualTo(HttpClient.Version.HTTP_2);
    }

    @Test
    void shouldNotFollowRedirectsByDefault() throws Exception {
        StreamableHttpMcpTransport transport =
                StreamableHttpMcpTransport.builder().url("http://localhost/mcp").build();

        assertThat(extractHttpClient(transport).followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
    }

    @Test
    void shouldFollowRedirectsWhenEnabled() throws Exception {
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost/mcp")
                .followRedirects(true)
                .build();

        assertThat(extractHttpClient(transport).followRedirects()).isEqualTo(HttpClient.Redirect.NORMAL);
    }

    @Test
    void shouldForceHttp11ForStreamableTransport() throws Exception {
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost/mcp")
                .setHttpVersion1_1()
                .build();

        assertThat(extractHttpClient(transport).version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    void mcpSessionIdShouldBeNullByDefault() {
        StreamableHttpMcpTransport transport =
                StreamableHttpMcpTransport.builder().url("http://localhost/mcp").build();

        assertThat(transport.getMcpSessionId()).isNull();
    }

    @Test
    void shouldExposeAndAcceptMcpSessionId() {
        StreamableHttpMcpTransport transport =
                StreamableHttpMcpTransport.builder().url("http://localhost/mcp").build();

        transport.setMcpSessionId("session-123");
        assertThat(transport.getMcpSessionId()).isEqualTo("session-123");

        transport.setMcpSessionId(null);
        assertThat(transport.getMcpSessionId()).isNull();
    }

    private static SSLContext extractSslContext(StreamableHttpMcpTransport transport) throws Exception {
        Field field = StreamableHttpMcpTransport.class.getDeclaredField("sslContext");
        field.setAccessible(true);
        return (SSLContext) field.get(transport);
    }

    private static HttpClient extractHttpClient(StreamableHttpMcpTransport transport) throws Exception {
        Field field = StreamableHttpMcpTransport.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        Field delegate = JdkHttpClient.class.getDeclaredField("delegate");
        delegate.setAccessible(true);
        return (HttpClient) delegate.get(field.get(transport));
    }
}
