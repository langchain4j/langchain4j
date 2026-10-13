package dev.langchain4j.model.openai.realtime;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.openai.realtime.gateway.RealtimeGatewayConfig;
import dev.langchain4j.model.openai.realtime.gateway.RealtimeGatewayServer;
import dev.langchain4j.model.openai.realtime.session.OkHttpRealtimeTransport;
import dev.langchain4j.model.openai.realtime.session.RealtimeTransport;
import dev.langchain4j.model.openai.realtime.tools.RealtimeToolRegistry;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolService;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * Public entry point for the OpenAI Realtime inbound WebSocket gateway.
 */
public final class OpenAiRealtimeGateway implements AutoCloseable {

    private final RealtimeGatewayServer server;

    private OpenAiRealtimeGateway(RealtimeGatewayServer server) {
        this.server = server;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    public URI wsUri() {
        return server.wsUri();
    }

    public int port() {
        return server.port();
    }

    @Override
    public void close() {
        stop();
    }

    public static final class Builder {

        private String host = "127.0.0.1";
        private int port = 0;
        private Map<ToolSpecification, ToolExecutor> tools = Collections.emptyMap();
        private Executor toolExecutor;
        private Function<String, RealtimeTransport> outboundTransportFactory;

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host");
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder tools(Map<ToolSpecification, ToolExecutor> tools) {
            this.tools = Objects.requireNonNull(tools, "tools");
            return this;
        }

        /**
         * Registers tools scanned from an object with {@code @Tool}-annotated methods.
         */
        public Builder tools(Object objectWithTools) {
            Objects.requireNonNull(objectWithTools, "objectWithTools");
            List<AiServiceTool> discovered = ToolService.findTools(objectWithTools);
            Map<ToolSpecification, ToolExecutor> map = new LinkedHashMap<>();
            for (AiServiceTool tool : discovered) {
                map.put(tool.toolSpecification(), tool.toolExecutor());
            }
            return tools(map);
        }

        public Builder toolExecutor(Executor toolExecutor) {
            this.toolExecutor = Objects.requireNonNull(toolExecutor, "toolExecutor");
            return this;
        }

        /**
         * Factory for outbound Realtime transports. Defaults to {@link OkHttpRealtimeTransport}.
         * Tests may inject {@code FakeRealtimeTransport}.
         */
        public Builder outboundTransportFactory(Function<String, RealtimeTransport> outboundTransportFactory) {
            this.outboundTransportFactory =
                    Objects.requireNonNull(outboundTransportFactory, "outboundTransportFactory");
            return this;
        }

        public OpenAiRealtimeGateway build() {
            Executor executor = toolExecutor != null ? toolExecutor : Executors.newCachedThreadPool();
            Function<String, RealtimeTransport> transportFactory = outboundTransportFactory != null
                    ? outboundTransportFactory
                    : apiKey -> new OkHttpRealtimeTransport();
            RealtimeGatewayConfig config =
                    new RealtimeGatewayConfig(host, port, RealtimeToolRegistry.from(tools), executor, transportFactory);
            return new OpenAiRealtimeGateway(new RealtimeGatewayServer(config));
        }
    }
}
