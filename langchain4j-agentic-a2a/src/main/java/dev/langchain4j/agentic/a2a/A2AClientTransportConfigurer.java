package dev.langchain4j.agentic.a2a;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.spi.ClientTransport;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfig;
import org.a2aproject.sdk.client.transport.spi.ClientTransportConfigBuilder;

/**
 * Customizes the transport of the A2A {@link org.a2aproject.sdk.client.Client} created for an A2A client agent.
 * <p>
 * By default an A2A client agent communicates over the JSON-RPC transport with its default configuration. Providing
 * an {@code A2AClientTransportConfigurer} gives full control over the {@link ClientBuilder} used under the hood, so it
 * is possible to:
 * <ul>
 *     <li>select a different transport implementation (JSON-RPC, gRPC, REST, ...);</li>
 *     <li>register {@link org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor call interceptors}
 *     to add authentication, OpenTelemetry context propagation, custom headers and so on;</li>
 *     <li>plug in a custom HTTP client.</li>
 * </ul>
 * <p>
 * A configurer can be passed programmatically through
 * {@link dev.langchain4j.agentic.internal.A2AClientBuilder#clientTransport(Object)}:
 * <pre>{@code
 * A2AClientTransportConfigurer transport = A2AClientTransportConfigurer.transport(
 *         JSONRPCTransport.class,
 *         new JSONRPCTransportConfigBuilder().addInterceptor(myOpenTelemetryInterceptor));
 *
 * MyAgent agent = AgenticServices.a2aBuilder(a2aServerUrl, MyAgent.class)
 *         .clientTransport(transport)
 *         .build();
 * }</pre>
 * or declaratively by annotating a static supplier method with
 * {@link dev.langchain4j.agentic.declarative.A2AClientTransportSupplier}.
 *
 * @see dev.langchain4j.agentic.declarative.A2AClientTransportSupplier
 */
@FunctionalInterface
public interface A2AClientTransportConfigurer {

    /**
     * Configures the given {@link ClientBuilder}. Implementations are expected to at least set a transport via
     * {@link ClientBuilder#withTransport(Class, ClientTransportConfigBuilder)} (or the config-based overload).
     *
     * @param clientBuilder the client builder to configure.
     */
    void configure(ClientBuilder clientBuilder);

    /**
     * Convenience factory building a configurer that simply sets the given transport and its configuration builder,
     * mirroring {@link ClientBuilder#withTransport(Class, ClientTransportConfigBuilder)}. This covers the most common
     * case of choosing a transport and configuring it, for example to register call interceptors.
     *
     * @param transportClass         the transport implementation class to use.
     * @param transportConfigBuilder the configuration builder for the chosen transport.
     * @param <T>                    the transport type.
     * @return a configurer applying the given transport and configuration.
     */
    static <T extends ClientTransport> A2AClientTransportConfigurer transport(
            Class<T> transportClass,
            ClientTransportConfigBuilder<? extends ClientTransportConfig<T>, ?> transportConfigBuilder) {
        ensureNotNull(transportClass, "transportClass");
        ensureNotNull(transportConfigBuilder, "transportConfigBuilder");
        return clientBuilder -> clientBuilder.withTransport(transportClass, transportConfigBuilder);
    }
}
