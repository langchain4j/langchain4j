package dev.langchain4j.model.openai.realtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.realtime.session.FakeRealtimeTransport;
import dev.langchain4j.model.openai.realtime.session.OpenAiRealtimeSession;
import dev.langchain4j.model.openai.realtime.tools.RealtimeToolRegistry;
import dev.langchain4j.service.tool.ToolExecutor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RealtimeGatewaySessionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private FakeRealtimeTransport transport;
    private OpenAiRealtimeSession outbound;
    private RealtimeGatewaySession gateway;
    private List<String> clientMessages;
    private RealtimeToolRegistry registry;

    @BeforeEach
    void setUp() {
        transport = new FakeRealtimeTransport();
        clientMessages = new CopyOnWriteArrayList<>();

        ToolSpecification weather = ToolSpecification.builder()
                .name("get_weather")
                .description("Get weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();
        ToolSpecification ping = ToolSpecification.builder()
                .name("ping")
                .description("Ping")
                .build();
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        tools.put(weather, (req, mem) -> "sunny");
        tools.put(ping, (req, mem) -> "pong");
        registry = RealtimeToolRegistry.from(tools);

        AtomicReference<RealtimeGatewaySession> gatewayRef = new AtomicReference<>();
        outbound = OpenAiRealtimeSession.builder()
                .transport(transport)
                .apiKey("sk-test")
                .listener(json -> gatewayRef.get().onOutboundEvent(json))
                .build();
        gateway = new RealtimeGatewaySession(outbound, registry, Runnable::run, clientMessages::add);
        gatewayRef.set(gateway);
        outbound.connect();
    }

    @AfterEach
    void tearDown() {
        if (gateway != null) {
            gateway.close();
        }
    }

    @Test
    void sessionUpdate_rewritesToolsBeforeOutbound() throws Exception {
        String inbound =
                """
                {"type":"session.update","session":{"type":"realtime","tools":[{"type":"function","name":"ping"}]}}
                """;

        gateway.onClientMessage(inbound);

        awaitSent(transport, 1);
        JsonNode root = OBJECT_MAPPER.readTree(transport.sent.get(0));
        assertThat(root.path("type").asText()).isEqualTo("session.update");
        JsonNode tools = root.path("session").path("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("ping");
        assertThat(tools.get(0).path("description").asText()).isEqualTo("Ping");
        assertThat(clientMessages).isEmpty();
    }

    @Test
    void sessionUpdate_unknownTool_sendsErrorAndNoOutbound() throws Exception {
        String inbound =
                """
                {"type":"session.update","session":{"tools":[{"type":"function","name":"unknown_tool"}]}}
                """;

        gateway.onClientMessage(inbound);

        Thread.sleep(50L);
        assertThat(transport.sent).isEmpty();
        assertThat(clientMessages).hasSize(1);
        JsonNode error = OBJECT_MAPPER.readTree(clientMessages.get(0));
        assertThat(error.path("type").asText()).isEqualTo("error");
        assertThat(error.path("error").path("message").asText()).contains("unknown_tool");
    }

    @Test
    void clientFunctionCallOutput_isIgnored() throws Exception {
        String inbound =
                """
                {"type":"conversation.item.create","item":{"type":"function_call_output","call_id":"call_1","output":"hack"}}
                """;

        gateway.onClientMessage(inbound);

        Thread.sleep(50L);
        assertThat(transport.sent).isEmpty();
        assertThat(clientMessages).hasSize(1);
        JsonNode error = OBJECT_MAPPER.readTree(clientMessages.get(0));
        assertThat(error.path("type").asText()).isEqualTo("error");
        assertThat(error.path("error").path("message").asText()).containsIgnoringCase("function_call_output");
    }

    @Test
    void responseDoneWithFunctionCall_runsLoopAndForwardsEvents() throws Exception {
        String responseDone = loadFixture("response-done-two-function-calls.json");

        transport.simulateText(responseDone);

        awaitSent(transport, 3);
        List<String> outboundCopy = new ArrayList<>(transport.sent);
        assertThat(outboundCopy.stream().filter(s -> s.contains("function_call_output"))).hasSize(2);
        assertThat(outboundCopy.stream().filter(s -> s.contains("\"type\":\"response.create\""))).hasSize(1);

        assertThat(clientMessages).hasSize(1);
        assertThat(OBJECT_MAPPER.readTree(clientMessages.get(0)).path("type").asText())
                .isEqualTo("response.done");
    }

    private static String loadFixture(String name) throws Exception {
        String path = "realtime/fixtures/" + name;
        try (InputStream in =
                RealtimeGatewaySessionTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("fixture %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void awaitSent(FakeRealtimeTransport transport, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (transport.sent.size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
        assertThat(transport.sent).hasSize(expected);
    }
}
