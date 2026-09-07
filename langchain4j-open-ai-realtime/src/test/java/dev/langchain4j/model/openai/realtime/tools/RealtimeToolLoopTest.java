package dev.langchain4j.model.openai.realtime.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.ToolExecutor;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RealtimeToolLoopTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ToolSpecification weatherSpec = ToolSpecification.builder()
            .name("get_weather")
            .description("weather")
            .build();
    private final ToolSpecification pingSpec = ToolSpecification.builder()
            .name("ping")
            .description("ping")
            .build();

    @Test
    void parallelFunctionCalls_emitOutputsThenSingleResponseCreate() throws Exception {
        List<String> sent = Collections.synchronizedList(new ArrayList<>());
        RealtimeOutboundWriter writer = sent::add;
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        tools.put(weatherSpec, (r, m) -> "sunny");
        tools.put(pingSpec, (r, m) -> "pong");
        RealtimeToolLoop loop = new RealtimeToolLoop(RealtimeToolRegistry.from(tools), writer, Runnable::run);

        boolean handled = loop.onServerEvent(loadFixture("response-done-two-function-calls.json"));

        assertThat(handled).isTrue();
        assertThat(sent.stream().filter(s -> s.contains("function_call_output"))).hasSize(2);
        assertThat(sent.stream().filter(s -> s.contains("\"type\":\"response.create\""))).hasSize(1);

        JsonNode weatherOut = findOutputByCallId(sent, "call_weather");
        assertThat(weatherOut.path("item").path("output").asText()).isEqualTo("sunny");
        JsonNode pingOut = findOutputByCallId(sent, "call_ping");
        assertThat(pingOut.path("item").path("output").asText()).isEqualTo("pong");

        int lastOutputIdx = IntStream.range(0, sent.size())
                .filter(i -> sent.get(i).contains("function_call_output"))
                .max()
                .orElseThrow();
        int responseCreateIdx = IntStream.range(0, sent.size())
                .filter(i -> sent.get(i).contains("\"type\":\"response.create\""))
                .findFirst()
                .orElseThrow();
        assertThat(responseCreateIdx).isGreaterThan(lastOutputIdx);
    }

    @Test
    void executorException_stillEmitsOutputAndResponseCreate() throws Exception {
        List<String> sent = Collections.synchronizedList(new ArrayList<>());
        ToolExecutor boom = (r, m) -> {
            throw new RuntimeException("fail");
        };
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        tools.put(weatherSpec, boom);
        RealtimeToolLoop loop =
                new RealtimeToolLoop(RealtimeToolRegistry.from(tools), sent::add, Runnable::run);

        String event =
                """
                {"type":"response.done","response":{"id":"resp_1","output":[{"type":"function_call","name":"get_weather","arguments":"{}","call_id":"call_boom"}]}}
                """;
        boolean handled = loop.onServerEvent(event);

        assertThat(handled).isTrue();
        assertThat(sent).hasSize(2);
        JsonNode outputEvent = OBJECT_MAPPER.readTree(sent.get(0));
        assertThat(outputEvent.path("type").asText()).isEqualTo("conversation.item.create");
        assertThat(outputEvent.path("item").path("type").asText()).isEqualTo("function_call_output");
        assertThat(outputEvent.path("item").path("call_id").asText()).isEqualTo("call_boom");
        assertThat(outputEvent.path("item").path("output").asText()).contains("fail");
        assertThat(OBJECT_MAPPER.readTree(sent.get(1)).path("type").asText()).isEqualTo("response.create");
    }

    @Test
    void hallucinatedToolName_emitsErrorOutput() throws Exception {
        List<String> sent = Collections.synchronizedList(new ArrayList<>());
        RealtimeToolLoop loop =
                new RealtimeToolLoop(RealtimeToolRegistry.from(Map.of()), sent::add, Runnable::run);

        String event =
                """
                {"type":"response.done","response":{"id":"resp_1","output":[{"type":"function_call","name":"no_such_tool","arguments":"{}","call_id":"call_missing"}]}}
                """;
        boolean handled = loop.onServerEvent(event);

        assertThat(handled).isTrue();
        assertThat(sent).hasSize(2);
        JsonNode outputEvent = OBJECT_MAPPER.readTree(sent.get(0));
        assertThat(outputEvent.path("item").path("call_id").asText()).isEqualTo("call_missing");
        assertThat(outputEvent.path("item").path("output").asText()).contains("no_such_tool");
        assertThat(OBJECT_MAPPER.readTree(sent.get(1)).path("type").asText()).isEqualTo("response.create");
    }

    @Test
    void responseDoneWithoutFunctionCall_returnsFalse() {
        List<String> sent = Collections.synchronizedList(new ArrayList<>());
        RealtimeToolLoop loop =
                new RealtimeToolLoop(RealtimeToolRegistry.from(Map.of()), sent::add, Runnable::run);

        String event =
                """
                {"type":"response.done","response":{"id":"resp_1","output":[{"type":"message","role":"assistant"}]}}
                """;
        boolean handled = loop.onServerEvent(event);

        assertThat(handled).isFalse();
        assertThat(sent).isEmpty();
    }

    private static String loadFixture(String name) throws Exception {
        String path = "realtime/fixtures/" + name;
        try (InputStream in = RealtimeToolLoopTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("fixture %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static JsonNode findOutputByCallId(List<String> sent, String callId) throws Exception {
        for (String json : sent) {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if ("function_call_output".equals(node.path("item").path("type").asText())
                    && callId.equals(node.path("item").path("call_id").asText())) {
                return node;
            }
        }
        throw new AssertionError("No function_call_output for call_id=" + callId);
    }
}
