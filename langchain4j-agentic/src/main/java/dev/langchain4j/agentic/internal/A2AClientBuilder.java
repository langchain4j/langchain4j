package dev.langchain4j.agentic.internal;

import dev.langchain4j.agentic.observability.AgentListener;

public interface A2AClientBuilder<T> {

    A2AClientBuilder<T> inputKeys(String... inputKeys);

    A2AClientBuilder<T> outputKey(String outputKey);

    A2AClientBuilder<T> async(boolean async);

    A2AClientBuilder<T> listener(AgentListener agentListener);

    /**
     * Configures the transport used by the underlying A2A client, allowing for instance to select a specific
     * transport implementation (JSON-RPC, gRPC, REST, ...) or to customize it with call interceptors enabling
     * authentication, OpenTelemetry context propagation and so on.
     * <p>
     * The {@code transportConfigurer} argument is expected to be an instance of
     * {@code dev.langchain4j.agentic.a2a.A2AClientTransportConfigurer}, provided by the
     * {@code langchain4j-agentic-a2a} module. It is typed as {@link Object} here because this module does not
     * depend on the A2A SDK, keeping the transport specific types confined to the A2A integration module.
     *
     * @param transportConfigurer the transport configurer to apply, or {@code null} to use the default transport.
     * @return this builder.
     */
    default A2AClientBuilder<T> clientTransport(Object transportConfigurer) {
        // Default no-op so that pre-existing third-party implementations keep working unchanged; implementations
        // supporting transport configuration (such as the one in langchain4j-agentic-a2a) override this method.
        return this;
    }

    T build();
}
