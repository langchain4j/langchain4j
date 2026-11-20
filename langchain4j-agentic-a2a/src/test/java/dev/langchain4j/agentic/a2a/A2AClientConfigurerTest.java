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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallInterceptor;
import org.a2aproject.sdk.client.transport.spi.interceptors.PayloadAndHeaders;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(30)
class A2AClientConfigurerTest {

    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"?([^\",}]*)\"?");
    private static String serverUrl;

    private HttpServer server;
    private final List<String> cardAuthorization = new CopyOnWriteArrayList<>();
    private final List<String> messageAuthorization = new CopyOnWriteArrayList<>();
    private final List<String> calls = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        serverUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        TestA2AClientConfigurer.DELEGATE.set(null);
        server.stop(0);
    }

    @Test
    void should_add_the_configured_credentials_to_the_card_request_and_the_messages() {
        TestA2AClientConfigurer.DELEGATE.set(new BearerConfigurer("configured"));

        UntypedAgent agent =
                AgenticServices.a2aBuilder(serverUrl).inputKeys("input").build();

        assertThat(cardAuthorization).isEmpty();
        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(cardAuthorization).containsExactly("Bearer configured-card@" + serverUrl);
        assertThat(messageAuthorization).containsExactly("Bearer configured-message@" + serverUrl + "/remote-agent");
    }

    @Test
    void should_let_the_agent_card_headers_of_the_agent_take_precedence() {
        TestA2AClientConfigurer.DELEGATE.set(new BearerConfigurer("configured"));

        UntypedAgent agent = AgenticServices.a2aBuilder(serverUrl)
                .agentCardHeaders(() -> Map.of("Authorization", "Bearer own"))
                .inputKeys("input")
                .build();

        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(cardAuthorization).containsExactly("Bearer own");
    }

    @Test
    void should_configure_the_client_before_the_client_customizer_of_the_agent() {
        TestA2AClientConfigurer.DELEGATE.set(new BearerConfigurer("configured"));

        UntypedAgent agent = AgenticServices.a2aBuilder(serverUrl)
                .clientCustomizer((ClientBuilder cb) -> calls.add("own customizer"))
                .inputKeys("input")
                .build();

        assertThat(agent.invoke(Map.of("input", "ping"))).isEqualTo("pong");
        assertThat(calls).containsExactly("configurer", "own customizer");
        assertThat(messageAuthorization).containsExactly("Bearer configured-message@" + serverUrl + "/remote-agent");
    }

    @Test
    void should_configure_declarative_agents() {
        TestA2AClientConfigurer.DELEGATE.set(new BearerConfigurer("configured"));

        RemoteAgent agent = AgenticServices.createAgenticSystem(RemoteAgent.class, (ChatModel) null);

        assertThat(agent.ask("ping")).isEqualTo("pong");
        assertThat(calls).containsExactly("configurer", "declarative customizer");
        assertThat(cardAuthorization).containsExactly("Bearer configured-card@" + serverUrl);
        assertThat(messageAuthorization).containsExactly("Bearer configured-message@" + serverUrl + "/remote-agent");
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

        @A2AClientCustomizer
        static void customize(ClientBuilder clientBuilder) {
            CURRENT.get().calls.add("declarative customizer");
        }
    }

    private static final ThreadLocal<A2AClientConfigurerTest> CURRENT = new ThreadLocal<>();

    @BeforeEach
    void setCurrent() {
        CURRENT.set(this);
    }

    // Adds a token naming the request and the agent, to the card request and to the messages
    private class BearerConfigurer implements A2AClientConfigurer {

        private final String token;

        BearerConfigurer(String token) {
            this.token = token;
        }

        @Override
        public Map<String, String> agentCardHeaders(String a2aServerUrl) {
            return Map.of("Authorization", "Bearer " + token + "-card@" + a2aServerUrl);
        }

        @Override
        public void customize(String a2aServerUrl, AgentCard agentCard, ClientBuilder clientBuilder) {
            calls.add("configurer");
            clientBuilder.withTransport(
                    JSONRPCTransport.class,
                    new JSONRPCTransportConfigBuilder()
                            .addInterceptor(new AuthorizationInterceptor(
                                    "Bearer " + token + "-message@" + a2aServerUrl + "/" + agentCard.name())));
        }
    }

    private static class AuthorizationInterceptor extends ClientCallInterceptor {

        private final String authorization;

        AuthorizationInterceptor(String authorization) {
            this.authorization = authorization;
        }

        @Override
        public PayloadAndHeaders intercept(
                String methodName,
                Object payload,
                Map<String, String> headers,
                AgentCard agentCard,
                ClientCallContext clientCallContext) {
            Map<String, String> withAuthorization = headers == null ? new HashMap<>() : new HashMap<>(headers);
            withAuthorization.put("Authorization", authorization);
            return new PayloadAndHeaders(payload, withAuthorization);
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
