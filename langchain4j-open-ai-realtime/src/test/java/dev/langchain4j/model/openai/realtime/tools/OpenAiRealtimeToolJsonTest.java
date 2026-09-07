package dev.langchain4j.model.openai.realtime.tools;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.junit.jupiter.api.Test;

class OpenAiRealtimeToolJsonTest {

    @Test
    void toFunctionTool_mapsNameDescriptionParameters() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("get_weather")
                .description("Get weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .required("city")
                        .build())
                .build();

        ObjectNode node = OpenAiRealtimeToolJson.toFunctionTool(spec);

        assertThat(node.get("type").asText()).isEqualTo("function");
        assertThat(node.get("name").asText()).isEqualTo("get_weather");
        assertThat(node.get("description").asText()).isEqualTo("Get weather");
        assertThat(node.get("parameters").get("type").asText()).isEqualTo("object");
        assertThat(node.get("parameters").get("properties").get("city").get("type").asText())
                .isEqualTo("string");
        assertThat(node.get("parameters").get("required").get(0).asText()).isEqualTo("city");
        // Realtime shape is flat — not Chat Completions nested function{}
        assertThat(node.has("function")).isFalse();
    }

    @Test
    void toFunctionTool_nullParameters_usesEmptyObjectSchema() {
        ToolSpecification spec = ToolSpecification.builder()
                .name("ping")
                .description("Ping")
                .build();

        ObjectNode node = OpenAiRealtimeToolJson.toFunctionTool(spec);

        assertThat(node.get("type").asText()).isEqualTo("function");
        assertThat(node.get("name").asText()).isEqualTo("ping");
        assertThat(node.get("parameters").get("type").asText()).isEqualTo("object");
        assertThat(node.get("parameters").get("properties").isEmpty()).isTrue();
    }
}
