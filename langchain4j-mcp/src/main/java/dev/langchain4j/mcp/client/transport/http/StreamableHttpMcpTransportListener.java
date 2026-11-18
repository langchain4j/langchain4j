package dev.langchain4j.mcp.client.transport.http;

import dev.langchain4j.Experimental;
import dev.langchain4j.spi.PrioritizedFactory;
import java.util.ServiceLoader;

/**
 * Notified each time a {@link StreamableHttpMcpTransport} is about to be created, whoever creates it.
 * This is meant for integrations that need to know about, or adjust, every Streamable HTTP transport
 * of an application, for example to route its traffic through a proxy.
 * <p>
 * Implementations are discovered with the {@link ServiceLoader}: list them in
 * {@code META-INF/services/dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransportListener}.
 * They are looked up each time a transport is created, with {@link StreamableHttpMcpTransport.Builder#build()}
 * or the {@link StreamableHttpMcpTransport#StreamableHttpMcpTransport(StreamableHttpMcpTransport.Builder) constructor},
 * in the thread context class loader in use at that time, or in the class loader of LangChain4j if that finds none.
 * Each lookup creates new instances, so an implementation needs a public no-argument constructor.
 * <p>
 * Listeners are called one after the other, highest {@link PrioritizedFactory#priority() priority} first.
 * A listener that does not implement {@link PrioritizedFactory} has {@link PrioritizedFactory#DEFAULT_PRIORITY},
 * and listeners with the same priority are called in no fixed order. A listener sees the changes made by the
 * listeners called before it, and the last one to change a setting decides its value.
 * <p>
 * A listener can see and change where every Streamable HTTP transport of the application connects to,
 * and the transport then sends its requests, including their headers (for example an API key), there.
 * Only put listeners you trust on the classpath.
 *
 * @since 1.25.0
 */
@Experimental
public interface StreamableHttpMcpTransportListener {

    /**
     * Called before the transport is created. The listener can read the URL configured so far with
     * {@link StreamableHttpMcpTransport.Builder#getUrl()}, which is {@code null} if none was set, and the
     * name of the transport with {@link StreamableHttpMcpTransport.Builder#getName()}, and change any setting
     * through the builder.
     * An exception thrown here fails the creation of the transport.
     *
     * @param builder the builder of the transport being created
     */
    void onNewTransport(StreamableHttpMcpTransport.Builder builder);
}
