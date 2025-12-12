package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import java.lang.reflect.Field;
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

    private static SSLContext extractSslContext(StreamableHttpMcpTransport transport) throws Exception {
        Field field = StreamableHttpMcpTransport.class.getDeclaredField("sslContext");
        field.setAccessible(true);
        return (SSLContext) field.get(transport);
    }
}
