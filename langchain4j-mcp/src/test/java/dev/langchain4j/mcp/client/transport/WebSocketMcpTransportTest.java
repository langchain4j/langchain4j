package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import java.lang.reflect.Field;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;

class WebSocketMcpTransportTest {

    @Test
    void shouldApplyCustomSslContext() throws Exception {
        SSLContext customContext = SSLContext.getInstance("TLS");
        customContext.init(null, null, null);

        WebSocketMcpTransport transport = WebSocketMcpTransport.builder()
                .url("ws://localhost/mcp")
                .sslContext(customContext)
                .build();

        assertThat(extractSslContext(transport)).isSameAs(customContext);
    }

    @Test
    void shouldReloadSslContext() throws Exception {
        SSLContext initialContext = SSLContext.getInstance("TLS");
        initialContext.init(null, null, null);
        SSLContext reloadedContext = SSLContext.getInstance("TLS");
        reloadedContext.init(null, null, null);

        WebSocketMcpTransport transport = WebSocketMcpTransport.builder()
                .url("ws://localhost/mcp")
                .sslContext(initialContext)
                .build();

        transport.reloadSslContext(reloadedContext);

        assertThat(extractSslContext(transport)).isSameAs(reloadedContext);
    }

    private static SSLContext extractSslContext(WebSocketMcpTransport transport) throws Exception {
        Field field = WebSocketMcpTransport.class.getDeclaredField("sslContext");
        field.setAccessible(true);
        return (SSLContext) field.get(transport);
    }
}
