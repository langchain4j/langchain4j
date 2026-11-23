package dev.langchain4j.mcp.client.transport;

import dev.langchain4j.Internal;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.websocket.WebSocketMcpTransport;
import dev.langchain4j.spi.PrioritizedFactory;
import java.util.ServiceLoader;

/**
 * Lets frameworks and libraries adjust every {@link StreamableHttpMcpTransport} and {@link WebSocketMcpTransport} of an
 * application, whoever creates it, for example to route its traffic through a proxy. Other transports, such as the
 * stdio one, do not call it. It is an internal SPI: it can change or be removed in any release.
 * <p>
 * Implementations are discovered with the {@link ServiceLoader}: list them in a file in {@code META-INF/services}
 * named after the fully qualified name of this interface. They are looked up each time such a transport is created,
 * with its builder's {@code build()} or with its constructor, in the thread context class loader in use at that
 * time, or in the class loader of LangChain4j if that finds none. Each lookup creates new instances, so an
 * implementation needs a public no-argument constructor. Each method does nothing by default, so an implementation
 * only overrides the ones for the transports it cares about.
 * <p>
 * A customizer works on a copy of the builder: its changes apply only to the transport being created, never to the
 * builder that the application holds. Building from the same builder again starts from the application's settings.
 * The copy is shallow: an object set on the builder, such as an HTTP client builder, is shared with the application,
 * and a timeout set by a customizer is applied to that HTTP client builder, as a timeout set by the application is.
 * Customizers are called one after the other, highest {@link PrioritizedFactory#priority() priority} first.
 * A customizer that does not implement {@link PrioritizedFactory} has {@link PrioritizedFactory#DEFAULT_PRIORITY},
 * and customizers with the same priority are called in no fixed order. A customizer sees the changes made by the
 * customizers called before it, and the last one to change a setting decides its value.
 * An exception thrown by a customizer fails the creation of the transport.
 * <p>
 * A customizer can see and change where each of these transports connects to, and the transport then
 * sends its requests, including their headers (for example an API key), there. Only put customizers you trust on
 * the classpath.
 *
 * @since 1.25.0
 */
@Internal
public interface McpTransportCustomizer {

    /**
     * Called before a {@link StreamableHttpMcpTransport} is created. The customizer can read the URL configured so
     * far with {@link StreamableHttpMcpTransport.Builder#getUrl()}, which is {@code null} if none was set, and the
     * name of the transport with {@link StreamableHttpMcpTransport.Builder#getName()}, and change any setting
     * through the builder.
     *
     * @param builder a copy of the builder of the transport being created
     */
    default void customize(StreamableHttpMcpTransport.Builder builder) {}

    /**
     * Called before a {@link WebSocketMcpTransport} is created. The customizer can read the URL configured so far
     * with {@link WebSocketMcpTransport.Builder#getUrl()}, which is {@code null} if none was set, and the name of the
     * transport with {@link WebSocketMcpTransport.Builder#getName()}, and change any setting through the builder.
     *
     * @param builder a copy of the builder of the transport being created
     */
    default void customize(WebSocketMcpTransport.Builder builder) {}
}
