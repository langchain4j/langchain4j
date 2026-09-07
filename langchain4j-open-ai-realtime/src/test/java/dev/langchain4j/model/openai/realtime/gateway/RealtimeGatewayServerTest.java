package dev.langchain4j.model.openai.realtime.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.realtime.OpenAiRealtimeGateway;
import dev.langchain4j.model.openai.realtime.session.FakeRealtimeTransport;
import dev.langchain4j.service.tool.ToolExecutor;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RealtimeGatewayServerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OpenAiRealtimeGateway gateway;
    private WebSocketClient client;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            client.closeBlocking();
            client = null;
        }
        if (gateway != null) {
            gateway.stop();
            gateway = null;
        }
    }

    @Test
    void connectsWithBearerAuthorization() throws Exception {
        FakeRealtimeTransport fake = new FakeRealtimeTransport();
        gateway = OpenAiRealtimeGateway.builder()
                .host("127.0.0.1")
                .port(0)
                .tools(sampleTools())
                .toolExecutor(Runnable::run)
                .outboundTransportFactory(apiKey -> fake)
                .build();
        gateway.start();

        CountDownLatch open = new CountDownLatch(1);
        client = newClient(gateway.wsUri(), "sk-test", open, new CopyOnWriteArrayList<>());
        assertThat(client.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        assertThat(open.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(client.isOpen()).isTrue();

        awaitCondition(() -> fake.listener != null, 5);
        assertThat(fake.connectedHeaders.get("Authorization")).isEqualTo("Bearer sk-test");
    }

    @Test
    void sessionUpdateWithoutTools_rewritesToFullRegistry() throws Exception {
        FakeRealtimeTransport fake = new FakeRealtimeTransport();
        gateway = OpenAiRealtimeGateway.builder()
                .host("127.0.0.1")
                .port(0)
                .tools(sampleTools())
                .toolExecutor(Runnable::run)
                .outboundTransportFactory(apiKey -> fake)
                .build();
        gateway.start();

        CountDownLatch open = new CountDownLatch(1);
        client = newClient(gateway.wsUri(), "sk-test", open, new CopyOnWriteArrayList<>());
        assertThat(client.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        assertThat(open.await(5, TimeUnit.SECONDS)).isTrue();
        awaitCondition(() -> fake.listener != null, 5);

        client.send("{\"type\":\"session.update\",\"session\":{\"type\":\"realtime\"}}");

        awaitSent(fake, 1);
        JsonNode root = OBJECT_MAPPER.readTree(fake.sent.get(0));
        assertThat(root.path("type").asText()).isEqualTo("session.update");
        JsonNode tools = root.path("session").path("tools");
        assertThat(tools).hasSize(2);
        List<String> names = new ArrayList<>();
        tools.forEach(n -> names.add(n.path("name").asText()));
        assertThat(names).containsExactly("get_weather", "ping");
    }

    @Test
    void responseDoneWithFunctionCall_loopsAndForwardsToClient() throws Exception {
        FakeRealtimeTransport fake = new FakeRealtimeTransport();
        gateway = OpenAiRealtimeGateway.builder()
                .host("127.0.0.1")
                .port(0)
                .tools(sampleTools())
                .toolExecutor(Runnable::run)
                .outboundTransportFactory(apiKey -> fake)
                .build();
        gateway.start();

        List<String> inbound = new CopyOnWriteArrayList<>();
        CountDownLatch open = new CountDownLatch(1);
        client = newClient(gateway.wsUri(), "sk-test", open, inbound);
        assertThat(client.connectBlocking(5, TimeUnit.SECONDS)).isTrue();
        assertThat(open.await(5, TimeUnit.SECONDS)).isTrue();
        awaitCondition(() -> fake.listener != null, 5);

        String responseDone = loadFixture("response-done-two-function-calls.json");
        fake.simulateText(responseDone);

        awaitSent(fake, 3);
        List<String> outboundCopy = new ArrayList<>(fake.sent);
        assertThat(outboundCopy.stream().filter(s -> s.contains("function_call_output"))).hasSize(2);
        assertThat(outboundCopy.stream().filter(s -> s.contains("\"type\":\"response.create\"")))
                .hasSize(1);

        awaitCondition(() -> inbound.stream().anyMatch(s -> s.contains("response.done")), 5);
        assertThat(inbound.stream()
                        .anyMatch(s -> {
                            try {
                                return "response.done"
                                        .equals(OBJECT_MAPPER.readTree(s).path("type").asText());
                            } catch (Exception e) {
                                return false;
                            }
                        }))
                .isTrue();
    }

    private static Map<ToolSpecification, ToolExecutor> sampleTools() {
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
        return tools;
    }

    private static WebSocketClient newClient(
            URI uri, String apiKey, CountDownLatch open, List<String> messages) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        return new WebSocketClient(uri, headers) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                open.countDown();
            }

            @Override
            public void onMessage(String message) {
                messages.add(message);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {}
        };
    }

    private static String loadFixture(String name) throws Exception {
        String path = "realtime/fixtures/" + name;
        try (InputStream in =
                RealtimeGatewayServerTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("fixture %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void awaitSent(FakeRealtimeTransport transport, int expected) throws Exception {
        awaitCondition(() -> transport.sent.size() >= expected, 5);
        assertThat(transport.sent.size()).isGreaterThanOrEqualTo(expected);
    }

    private static void awaitCondition(java.util.function.BooleanSupplier condition, int seconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10L);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }
}
