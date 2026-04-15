package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Regression test for issue #4773: Gemini tools must be serialized as an array,
 * not as a bare object.
 *
 * When serializing a request with a single tool, the output JSON must contain
 * "tools": [{ ... }] (array with one element) NOT "tools": { ... } (bare object).
 */
class GeminiToolsSerializationTest {

    @Test
    void should_serialize_tools_as_array_when_single_tool() {
        // given
        ToolSpecification spec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get the weather")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("city", JsonStringSchema.builder().description("The city name").build())
                        .required("city")
                        .build())
                .build();

        List<ToolSpecification> specs = List.of(spec);
        List<GeminiTool> tools = FunctionMapper.fromToolSpecsToGTools(specs, false, false, false, false, false);

        // Verify we have a list with exactly one tool
        assertThat(tools).hasSize(1);

        // Build a generate content request with this tool
        var request = GeminiGenerateContentRequest.builder()
                .contents(List.of())
                .tools(tools)
                .build();

        // when
        String json = Json.toJsonWithoutIndent(request);

        System.out.println("Serialized JSON:\n" + json);

        // then
        // The "tools" field must be an array "[...]" not a bare object "{...}"
        assertThat(json).contains("\"tools\":[");
        assertThat(json).doesNotContain("\"tools\":{");
    }

    @Test
    void should_serialize_tools_as_array_when_multiple_tools() {
        // given
        ToolSpecification spec1 = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("city", JsonStringSchema.builder().build())
                        .required("city")
                        .build())
                .build();

        ToolSpecification spec2 = ToolSpecification.builder()
                .name("getTime")
                .description("Get time")
                .parameters(JsonObjectSchema.builder()
                        .addProperty("tz", JsonStringSchema.builder().build())
                        .required("tz")
                        .build())
                .build();

        List<ToolSpecification> specs = List.of(spec1, spec2);
        List<GeminiTool> tools = FunctionMapper.fromToolSpecsToGTools(specs, false, false, false, false, false);

        assertThat(tools).hasSize(1); // All function declarations go into one GeminiTool

        var request = GeminiGenerateContentRequest.builder()
                .contents(List.of())
                .tools(tools)
                .build();

        // when
        String json = Json.toJsonWithoutIndent(request);

        System.out.println("Serialized JSON with multiple tools:\n" + json);

        // then
        assertThat(json).contains("\"tools\":[");
    }
}
