package dev.langchain4j.mcp.client.transport;

import java.util.concurrent.ExecutorService;

/**
 * SPI for providing a custom {@link ExecutorService} for MCP transports.
 * <p>
 * Frameworks like Quarkus or Spring can implement this interface to provide
 * their own executor service (e.g., managed thread pool) instead of the default one.
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 * If no implementation is found, the transport will fall back to using
 * virtual threads (Java 21+) or a fixed thread pool with daemon threads.
 *
 * <p>Example implementation for Quarkus:</p>
 * <pre>{@code
 * public class QuarkusMcpExecutorServiceSupplier implements McpExecutorServiceSupplier {
 *     @Override
 *     public ExecutorService get() {
 *         return Arc.container().instance(ManagedExecutor.class).get();
 *     }
 * }
 * }</pre>
 *
 * <p>Register the implementation in {@code META-INF/services/dev.langchain4j.mcp.client.transport.McpExecutorServiceSupplier}</p>
 */
public interface McpExecutorServiceSupplier {

    /**
     * Provides an {@link ExecutorService} to be used by MCP transports.
     *
     * @return the executor service to use for transport operations
     */
    ExecutorService get();
}
