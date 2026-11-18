package dev.langchain4j.mcp.client.transport.http;

import dev.langchain4j.Experimental;
import dev.langchain4j.spi.PrioritizedFactory;
import java.util.ServiceLoader;

/**
 * Notified each time a {@link StreamableHttpMcpTransport} is about to be built, whoever builds it.
 * This is meant for integrations that need to know about, or adjust, every Streamable HTTP transport
 * of an application, for example to route its traffic through a proxy.
 * <p>
 * Implementations are discovered with the {@link ServiceLoader}: list them in
 * {@code META-INF/services/dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransportListener}.
 * They are looked up on every {@link StreamableHttpMcpTransport.Builder#build()}, so listeners follow
 * the thread context class loader in use at that time. A listener that implements
 * {@link PrioritizedFactory} is called before listeners with a lower priority.
 *
 * @since 1.25.0
 */
@Experimental
public interface StreamableHttpMcpTransportListener {

    /**
     * Called from {@link StreamableHttpMcpTransport.Builder#build()}, before the transport is created.
     * The listener can read what was configured so far, that is
     * {@link StreamableHttpMcpTransport.Builder#getUrl()} and {@link StreamableHttpMcpTransport.Builder#getName()},
     * and change it through the builder.
     * An exception thrown here fails the build.
     *
     * @param builder the builder of the transport being created
     */
    void onNewTransport(StreamableHttpMcpTransport.Builder builder);
}
