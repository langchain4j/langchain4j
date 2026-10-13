package dev.langchain4j.model.openai.realtime.gateway;

import dev.langchain4j.model.openai.realtime.session.RealtimeTransport;
import dev.langchain4j.model.openai.realtime.tools.RealtimeToolRegistry;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * Bind address and shared runtime settings for {@link RealtimeGatewayServer}.
 */
public final class RealtimeGatewayConfig {

    private final String host;
    private final int port;
    private final RealtimeToolRegistry toolRegistry;
    private final Executor toolExecutor;
    private final Function<String, RealtimeTransport> outboundTransportFactory;

    public RealtimeGatewayConfig(
            String host,
            int port,
            RealtimeToolRegistry toolRegistry,
            Executor toolExecutor,
            Function<String, RealtimeTransport> outboundTransportFactory) {
        this.host = Objects.requireNonNull(host, "host");
        this.port = port;
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry");
        this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor");
        this.outboundTransportFactory = Objects.requireNonNull(outboundTransportFactory, "outboundTransportFactory");
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public RealtimeToolRegistry toolRegistry() {
        return toolRegistry;
    }

    public Executor toolExecutor() {
        return toolExecutor;
    }

    public Function<String, RealtimeTransport> outboundTransportFactory() {
        return outboundTransportFactory;
    }
}
