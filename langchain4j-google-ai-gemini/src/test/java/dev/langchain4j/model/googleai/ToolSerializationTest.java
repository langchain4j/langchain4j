package dev.langchain4j.model.googleai;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.googleai.GeminiGenerateContentRequest.GeminiTool;
import java.util.List;

class ToolSerializationTest {
    public static void main(String[] args) {
        // Create a single tool spec
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

        System.out.println("GeminiTool list size: " + tools.size());
        System.out.println("GeminiTool: " + tools);

        // Now build a request with tools
        var request = GeminiGenerateContentRequest.builder()
                .contents(List.of())
                .tools(tools)
                .build();

        String json = Json.toJsonWithoutIndent(request);
        System.out.println("Serialized request:");
        System.out.println(json);

        // Check if tools is array or object
        if (json.contains("\"tools\":{")) {
            System.out.println("\nBUG: tools is serialized as object, not array!");
        } else if (json.contains("\"tools\":[")) {
            System.out.println("\nOK: tools is correctly serialized as array");
        }
    }
}
