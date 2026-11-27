package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AClientCustomizer;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class A2AClientConfiguratorTest {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"?([^\",}]*)\"?");
    private static String serverUrl;

    private HttpServer server;
    private final List<String> cardAuthorization = new CopyOnWriteArrayList<>();
    private final List<String> messageAuthorization = new CopyOnWriteArrayList<>();
    private final List<String> messageTrace = new CopyOnWriteArrayList<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final List<A2AClientContext> contexts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        CURRENT.set(this);
    }

    @AfterEach
    void stop() {
        TestA2AClientConfigurator.DELEGATE.set(null);
        SecondTestA2AClientConfigurator.DELEGATE.set(null);
        server.stop(0);
    }

    @Test
    void should_add_the_configured_headers_to_the_card_request_and_the_messages() {
        TestA2AClientConfigurator.DELEGATE.set(new BearerConfigurator("configured"));

        UntypedAgent agent =
                AgenticServices.a2aBuilder(serverUrl).inputKeys("input").build();

        assertThat(cardAuthorization).isEmpty();
        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(cardAuthorization).containsExactly("Bearer configured-card@" + serverUrl);
        assertThat(messageAuthorization).containsExactly("Bearer configured-message@remote-agent-1");
    }

    @Test
    void should_keep_the_message_headers_when_the_customizer_of_the_agent_configures_the_transport() {
        TestA2AClientConfigurator.DELEGATE.set(new BearerConfigurator("configured"));

        // A typical customizer: it configures the JSON-RPC transport, which replaces any earlier configuration of it
        UntypedAgent agent = AgenticServices.a2aBuilder(serverUrl)
                .clientCustomizer((ClientBuilder cb) -> {
                    calls.add("own customizer");
                    cb.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
                })
                .inputKeys("input")
                .build();

        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(calls).containsExactly("configurator", "own customizer");
        assertThat(messageAuthorization).containsExactly("Bearer configured-message@remote-agent-1");
    }

    @Test
    void should_merge_the_headers_of_several_configurators() {
        TestA2AClientConfigurator.DELEGATE.set(new BearerConfigurator("first"));
        SecondTestA2AClientConfigurator.DELEGATE.set(new A2AClientConfigurator() {
            @Override
            public Map<String, String> agentCardHeaders(A2AClientContext context) {
                return Map.of("Authorization", "Bearer second-card");
            }

            @Override
            public Map<String, String> messageHeaders(A2AClientContext context) {
                return Map.of("X-Trace", "trace-1");
            }
        });

        UntypedAgent agent =
                AgenticServices.a2aBuilder(serverUrl).inputKeys("input").build();

        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        // Different headers are all sent; for the same header, the configurator found later wins
        assertThat(cardAuthorization).containsExactly("Bearer second-card");
        assertThat(messageAuthorization).containsExactly("Bearer first-message@remote-agent-1");
        assertThat(messageTrace).containsExactly("trace-1");
    }

    @Test
    void should_ask_for_the_message_headers_on_every_message() {
        TestA2AClientConfigurator.DELEGATE.set(new BearerConfigurator("configured"));

        UntypedAgent agent =
                AgenticServices.a2aBuilder(serverUrl).inputKeys("input").build();

        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(messageAuthorization)
                .containsExactly(
                        "Bearer configured-message@remote-agent-1", "Bearer configured-message@remote-agent-2");
        assertThat(cardAuthorization).hasSize(1);
    }

    @Test
    void should_describe_the_agent_to_the_configurator() {
        TestA2AClientConfigurator.DELEGATE.set(new BearerConfigurator("configured"));

        UntypedAgent agent = AgenticServices.a2aBuilder(serverUrl)
                .name("local-name")
                .tenant("tenant-a")
                .inputKeys("input")
                .build();

        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        A2AClientContext card = contexts.get(0);
        assertThat(card.a2aServerUrl()).isEqualTo(serverUrl);
        assertThat(card.tenant()).isEqualTo("tenant-a");
        assertThat(card.agentName()).isEqualTo("local-name");
        assertThat(card.agentInterface()).isEqualTo(UntypedAgent.class);
        assertThat(card.agentCard()).isNull();
        A2AClientContext message = contexts.get(contexts.size() - 1);
        assertThat(message.tenant()).isEqualTo("tenant-a");
        assertThat(message.agentName()).isEqualTo("local-name");
        assertThat(message.agentCard().name()).isEqualTo("remote-agent");
    }

    @Test
    void should_let_the_agent_card_headers_of_the_agent_take_precedence() {
        TestA2AClientConfigurator.DELEGATE.set(new BearerConfigurator("configured"));

        UntypedAgent agent = AgenticServices.a2aBuilder(serverUrl)
                .agentCardHeaders(() -> Map.of("Authorization", "Bearer own"))
                .inputKeys("input")
                .build();

        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(cardAuthorization).containsExactly("Bearer own");
    }

    @Test
    void should_configure_declarative_agents_with_a_customizer() {
        TestA2AClientConfigurator.DELEGATE.set(new BearerConfigurator("configured"));

        RemoteAgent agent = AgenticServices.createAgenticSystem(RemoteAgent.class, (ChatModel) null);

        assertThat(agent.ask("ping")).isEqualTo("pong");
        assertThat(calls).containsExactly("configurator", "declarative customizer");
        assertThat(cardAuthorization).containsExactly("Bearer configured-card@" + serverUrl);
        assertThat(messageAuthorization).containsExactly("Bearer configured-message@remote-agent-1");
    }

    @Test
    void should_send_no_credentials_when_nothing_is_configured() {
        UntypedAgent agent =
                AgenticServices.a2aBuilder(serverUrl).inputKeys("input").build();

        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(cardAuthorization).containsExactly("none");
        assertThat(messageAuthorization).containsExactly("none");
    }

    public interface RemoteAgent {

        @A2AClientAgent(description = "A remote agent", outputKey = "reply")
        String ask(@V("input") String input);

        @A2AServerUrlSupplier
        static String serverUrl() {
            return serverUrl;
        }

        // Configures the transport, as a real customizer does
        @A2AClientCustomizer
        static void customize(ClientBuilder clientBuilder) {
            CURRENT.get().calls.add("declarative customizer");
            clientBuilder.withTransport(JSONRPCTransport.class, new JSONRPCTransportConfigBuilder());
        }
    }

    private static final ThreadLocal<A2AClientConfiguratorTest> CURRENT = new ThreadLocal<>();

    // Card token names the server; message tokens name the remote agent and count the messages
    private class BearerConfigurator implements A2AClientConfigurator {

        private final String token;
        private int messages;

        BearerConfigurator(String token) {
            this.token = token;
        }

        @Override
        public Map<String, String> agentCardHeaders(A2AClientContext context) {
            contexts.add(context);
            return Map.of("Authorization", "Bearer " + token + "-card@" + context.a2aServerUrl());
        }

        @Override
        public Map<String, String> messageHeaders(A2AClientContext context) {
            contexts.add(context);
            return Map.of(
                    "Authorization",
                    "Bearer " + token + "-message@" + context.agentCard().name() + "-" + ++messages);
        }

        @Override
        public void customize(A2AClientContext context, ClientBuilder clientBuilder) {
            calls.add("configurator");
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        String recorded = authorization == null ? "none" : authorization;
        if (exchange.getRequestURI().getPath().equals("/.well-known/agent-card.json")) {
            cardAuthorization.add(recorded);
            respond(exchange, """
                    {
                      "name": "remote-agent",
                      "description": "A remote agent",
                      "version": "1.0.0",
                      "supportedInterfaces": [
                        { "url": "%s", "protocolBinding": "JSONRPC", "protocolVersion": "1.0" }
                      ],
                      "capabilities": {},
                      "skills": [],
                      "defaultInputModes": ["text"],
                      "defaultOutputModes": ["text"]
                    }
                    """.formatted(serverUrl));
            return;
        }
        messageAuthorization.add(recorded);
        String trace = exchange.getRequestHeaders().getFirst("X-Trace");
        if (trace != null) {
            messageTrace.add(trace);
        }
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Matcher id = ID.matcher(body);
        respond(exchange, """
                {"jsonrpc":"2.0","id":"%s","result":{"message":{"messageId":"reply","role":"ROLE_AGENT",\
                "parts":[{"text":"pong"}]}}}""".formatted(id.find() ? id.group(1) : ""));
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
