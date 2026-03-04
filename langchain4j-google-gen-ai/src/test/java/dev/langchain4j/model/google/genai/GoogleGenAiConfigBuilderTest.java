package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.Schema;
import com.google.genai.types.Type;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GoogleGenAiConfigBuilderTest {

    @Test
    void should_build_config_with_all_generation_parameters() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .temperature(0.7)
                .topP(0.9)
                .topK(40)
                .maxOutputTokens(1024)
                .stopSequences(List.of("STOP"))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, null);

        assertThat(config).isNotNull();
        assertThat(config.temperature().get()).isEqualTo(0.7f);
        assertThat(config.topP().get()).isEqualTo(0.9f);
        assertThat(config.topK().get()).isEqualTo(40f);
        assertThat(config.maxOutputTokens().get()).isEqualTo(1024);
        assertThat(config.stopSequences().get()).containsExactly("STOP");
    }

    @Test
    void should_build_config_with_null_parameters() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, null);

        assertThat(config).isNotNull();
    }

    @Test
    void should_set_safety_settings() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();
        List<SafetySetting> safetySettings = List.of(
                SafetySetting.builder()
                        .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                        .build());

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, safetySettings, null, null, null, null, false, null);

        assertThat(config.safetySettings().get()).hasSize(1);
    }

    @Test
    void should_skip_empty_safety_settings() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, List.of(), null, null, null, null, false, null);

        assertThat(config).isNotNull();
    }

    @Test
    void should_set_response_mime_type() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, "text/plain", null, null, false, null);

        assertThat(config.responseMimeType().get()).isEqualTo("text/plain");
    }

    @Test
    void should_set_response_schema_with_json_mime_type() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();
        Schema schema = Schema.builder().type(Type.Known.OBJECT).build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, schema, null, null, null, false, null);

        assertThat(config.responseSchema().get()).isNotNull();
        assertThat(config.responseMimeType().get()).isEqualTo("application/json");
    }

    @Test
    void should_set_json_mime_type_from_response_format() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.JSON).build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, null);

        assertThat(config.responseMimeType().get()).isEqualTo("application/json");
    }

    @Test
    void should_set_thinking_config() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, 1024, null, false, null);

        assertThat(config.thinkingConfig().get().thinkingBudget().get()).isEqualTo(1024);
    }

    @Test
    void should_set_seed() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, 42, false, null);

        assertThat(config.seed().get()).isEqualTo(42);
    }

    @Test
    void should_set_system_instruction() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();
        Content systemInstruction = Content.builder()
                .role("system")
                .parts(Part.builder().text("Be helpful").build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, systemInstruction, null, null, null, null, null, false, null);

        assertThat(config.systemInstruction().get().parts().get().get(0).text().get())
                .isEqualTo("Be helpful");
    }

    @Test
    void should_add_tool_specifications_with_auto_mode() {
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather for a city")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city", "city name")
                        .build())
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpec))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, null);

        assertThat(config.tools().get()).isNotEmpty();
        assertThat(config.toolConfig().get().functionCallingConfig().get().mode().get().toString())
                .isEqualTo("AUTO");
    }

    @Test
    void should_set_required_tool_choice() {
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpec))
                .toolChoice(ToolChoice.REQUIRED)
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, null);

        assertThat(config.toolConfig().get().functionCallingConfig().get().mode().get().toString())
                .isEqualTo("ANY");
    }

    @Test
    void should_set_none_tool_choice() {
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpec))
                .toolChoice(ToolChoice.NONE)
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, null);

        assertThat(config.toolConfig().get().functionCallingConfig().get().mode().get().toString())
                .isEqualTo("NONE");
    }

    @Test
    void should_set_allowed_function_names() {
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpec))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false,
                List.of("getWeather"));

        assertThat(config.toolConfig().get().functionCallingConfig().get()
                .allowedFunctionNames().get()).containsExactly("getWeather");
    }

    @Test
    void should_enable_google_search_when_no_tools() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, true, null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).googleSearch().isPresent()).isTrue();
    }

    @Test
    void should_prefer_function_tools_over_google_search() {
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpec))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, true, null);

        // Function tool should be added, not google search
        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).functionDeclarations().get()).isNotEmpty();
    }

    @Test
    void should_not_set_response_format_when_not_json() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .responseFormat(ResponseFormat.builder().type(ResponseFormatType.TEXT).build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, null);

        assertThat(config).isNotNull();
    }
}
