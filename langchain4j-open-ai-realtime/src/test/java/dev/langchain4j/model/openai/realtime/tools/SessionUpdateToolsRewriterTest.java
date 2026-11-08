package dev.langchain4j.model.openai.realtime.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SessionUpdateToolsRewriterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SessionUpdateToolsRewriter rewriter;

    @BeforeEach
    void setUp() {
        ToolSpecification weather = ToolSpecification.builder()
                .name("get_weather")
                .description("Get weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();
        ToolSpecification ping =
                ToolSpecification.builder().name("ping").description("Ping").build();
        Map<ToolSpecification, ToolExecutor> tools = new LinkedHashMap<>();
        tools.put(weather, (req, mem) -> "sunny");
        tools.put(ping, (req, mem) -> "pong");
        rewriter = new SessionUpdateToolsRewriter(RealtimeToolRegistry.from(tools));
    }

    @Test
    void omitTools_usesFullRegistry() throws Exception {
        String inbound = """
                {"type":"session.update","session":{"type":"realtime","instructions":"hi"}}
                """;

        String outbound = rewriter.rewrite(inbound);

        JsonNode root = OBJECT_MAPPER.readTree(outbound);
        assertThat(root.get("type").asText()).isEqualTo("session.update");
        assertThat(root.path("session").path("instructions").asText()).isEqualTo("hi");
        assertThat(root.path("session").path("type").asText()).isEqualTo("realtime");
        JsonNode tools = root.path("session").path("tools");
        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).get("name").asText()).isEqualTo("get_weather");
        assertThat(tools.get(1).get("name").asText()).isEqualTo("ping");
        assertThat(tools.get(0).get("parameters").get("properties").has("city")).isTrue();
    }

    @Test
    void emptyToolsArray_usesFullRegistry() throws Exception {
        String inbound = """
                {"type":"session.update","session":{"tools":[]}}
                """;

        String outbound = rewriter.rewrite(inbound);

        assertThat(OBJECT_MAPPER.readTree(outbound).path("session").path("tools"))
                .hasSize(2);
    }

    @Test
    void whitelist_filtersByName() throws Exception {
        String inbound = """
                {"type":"session.update","session":{"type":"realtime","tools":[{"type":"function","name":"ping"}]}}
                """;

        String outbound = rewriter.rewrite(inbound);

        JsonNode tools = OBJECT_MAPPER.readTree(outbound).path("session").path("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("name").asText()).isEqualTo("ping");
        assertThat(tools.get(0).get("description").asText()).isEqualTo("Ping");
    }

    @Test
    void whitelist_supportsNestedFunctionName() throws Exception {
        String inbound = """
                {"type":"session.update","session":{"tools":[{"type":"function","function":{"name":"get_weather"}}]}}
                """;

        String outbound = rewriter.rewrite(inbound);

        JsonNode tools = OBJECT_MAPPER.readTree(outbound).path("session").path("tools");
        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).get("name").asText()).isEqualTo("get_weather");
        assertThat(tools.get(0).has("function")).isFalse();
    }

    @Test
    void unknownName_throws() {
        String inbound = """
                {"type":"session.update","session":{"tools":[{"type":"function","name":"unknown_tool"}]}}
                """;

        assertThatThrownBy(() -> rewriter.rewrite(inbound))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown_tool");
    }

    @Test
    void nonSessionUpdate_returnsUnchanged() {
        String inbound = """
                {"type":"response.create","response":{}}
                """;

        assertThat(rewriter.rewrite(inbound)).isEqualTo(inbound);
    }
}
