package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.lang.reflect.Field;
import java.net.http.HttpClient;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

class StreamableHttpMcpTransportTest {

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
    void shouldForceHttp11ForStreamableTransport() throws Exception {
        StreamableHttpMcpTransport transport = StreamableHttpMcpTransport.builder()
                .url("http://localhost/mcp")
                .setHttpVersion1_1()
                .build();

        assertThat(extractHttpClient(transport).version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    private static SSLContext extractSslContext(StreamableHttpMcpTransport transport) throws Exception {
        Field field = StreamableHttpMcpTransport.class.getDeclaredField("sslContext");
        field.setAccessible(true);
        return (SSLContext) field.get(transport);
    }

    private static HttpClient extractHttpClient(StreamableHttpMcpTransport transport) throws Exception {
        Field field = StreamableHttpMcpTransport.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        return (HttpClient) field.get(transport);
    }
}
