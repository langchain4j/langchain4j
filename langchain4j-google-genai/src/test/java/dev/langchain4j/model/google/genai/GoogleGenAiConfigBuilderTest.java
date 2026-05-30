package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import java.util.HashMap;
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
                .frequencyPenalty(0.5)
                .presencePenalty(0.3)
                .maxOutputTokens(1024)
                .stopSequences(List.of("STOP"))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config).isNotNull();
        assertThat(config.temperature().get()).isEqualTo(0.7f);
        assertThat(config.topP().get()).isEqualTo(0.9f);
        assertThat(config.topK().get()).isEqualTo(40f);
        assertThat(config.frequencyPenalty().get()).isEqualTo(0.5f);
        assertThat(config.presencePenalty().get()).isEqualTo(0.3f);
        assertThat(config.maxOutputTokens().get()).isEqualTo(1024);
        assertThat(config.stopSequences().get()).containsExactly("STOP");
    }

    @Test
    void should_build_config_with_null_parameters() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config).isNotNull();
    }

    @Test
    void should_set_safety_settings() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();
        List<SafetySetting> safetySettings = List.of(SafetySetting.builder()
                .category(HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT)
                .build());

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, safetySettings, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.safetySettings().get()).hasSize(1);
    }

    @Test
    void should_skip_empty_safety_settings() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, List.of(), null, null, null, false, false, false, null, null, null, null);

        assertThat(config).isNotNull();
    }

    @Test
    void should_set_json_mime_type_from_response_format() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .responseFormat(
                        ResponseFormat.builder().type(ResponseFormatType.JSON).build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.responseMimeType().get()).isEqualTo("application/json");
        assertThat(config.responseSchema().isPresent()).isFalse();
    }

    @Test
    void should_set_json_schema_from_response_format() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .rootElement(JsonObjectSchema.builder()
                                        .addStringProperty("name")
                                        .build())
                                .build())
                        .build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.responseMimeType().get()).isEqualTo("application/json");
        assertThat(config.responseSchema().isPresent()).isTrue();
        assertThat(config.responseSchema().get().type().get().toString()).contains("OBJECT");
    }

    @Test
    void should_set_thinking_config() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, 1024, null, null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig().get().thinkingBudget().get()).isEqualTo(1024);
    }

    @Test
    void should_set_thinking_level() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, "MEDIUM", null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig().get().thinkingLevel().get().toString())
                .contains("MEDIUM");
    }

    @Test
    void should_throw_if_both_thinking_config_are_set() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        assertThatThrownBy(() -> GoogleGenAiConfigBuilder.buildConfig(
                        parameters, null, null, 1024, "MEDIUM", null, false, false, false, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot use both thinkingBudget and thinkingLevel at the same time");
    }

    @Test
    void should_set_cached_content() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
                null,
                null,
                "projects/123/locations/us-central1/cachedContents/456");

        assertThat(config.cachedContent().isPresent()).isTrue();
        assertThat(config.cachedContent().get()).isEqualTo("projects/123/locations/us-central1/cachedContents/456");
    }

    @Test
    void should_set_seed() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, 42, false, false, false, null, null, null, null);

        assertThat(config.seed().get()).isEqualTo(42);
    }

    @Test
    void should_set_system_instruction() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();
        Content systemInstruction = Content.builder()
                .role("system")
                .parts(Part.builder().text("Be helpful").build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, systemInstruction, null, null, null, null, false, false, false, null, null, null, null);

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
                parameters, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.tools().get()).isNotEmpty();
        assertThat(config.toolConfig()
                        .get()
                        .functionCallingConfig()
                        .get()
                        .mode()
                        .get()
                        .toString())
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
                parameters, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.toolConfig()
                        .get()
                        .functionCallingConfig()
                        .get()
                        .mode()
                        .get()
                        .toString())
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
                parameters, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.toolConfig()
                        .get()
                        .functionCallingConfig()
                        .get()
                        .mode()
                        .get()
                        .toString())
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
                parameters, null, null, null, null, null, false, false, false, List.of("getWeather"), null, null, null);

        assertThat(config.toolConfig()
                        .get()
                        .functionCallingConfig()
                        .get()
                        .allowedFunctionNames()
                        .get())
                .containsExactly("getWeather");
    }

    @Test
    void should_enable_google_search_when_no_tools() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, true, false, false, null, null, null, null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).googleSearch().isPresent()).isTrue();
    }

    @Test
    void should_enable_google_maps_when_no_tools() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, false, true, false, null, null, null, null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).googleMaps().isPresent()).isTrue();
    }

    @Test
    void should_enable_url_context_when_no_tools() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, false, false, true, null, null, null, null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).urlContext().isPresent()).isTrue();
    }

    @Test
    void should_enable_all_google_tools_together() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, true, true, true, null, null, null, null);

        assertThat(config.tools().get()).hasSize(3);
        assertThat(config.tools().get().get(0).googleSearch().isPresent()).isTrue();
        assertThat(config.tools().get().get(1).googleMaps().isPresent()).isTrue();
        assertThat(config.tools().get().get(2).urlContext().isPresent()).isTrue();
    }

    @Test
    void should_enable_google_tools_alongside_function_declarations() {
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpec))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, true, true, true, null, null, null, null);

        assertThat(config.tools().get()).hasSize(4);
        assertThat(config.tools().get().get(0).functionDeclarations().get()).isNotEmpty();
        assertThat(config.tools().get().get(1).googleSearch().isPresent()).isTrue();
        assertThat(config.tools().get().get(2).googleMaps().isPresent()).isTrue();
        assertThat(config.tools().get().get(3).urlContext().isPresent()).isTrue();
    }

    @Test
    void should_add_google_search_alongside_function_tools() {
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpec))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, true, false, false, null, null, null, null);

        assertThat(config.tools().get()).hasSize(2);
        assertThat(config.tools().get().get(0).functionDeclarations().get()).isNotEmpty();
        assertThat(config.tools().get().get(1).googleSearch().isPresent()).isTrue();
    }

    @Test
    void should_not_set_response_format_when_not_json() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .responseFormat(
                        ResponseFormat.builder().type(ResponseFormatType.TEXT).build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config).isNotNull();
    }

    @Test
    void should_add_vertex_search_datastore_tool() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                null,
                "projects/123/locations/global/collections/default_collection/dataStores/my-datastore",
                null,
                null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).retrieval().isPresent()).isTrue();
        assertThat(config.tools()
                        .get()
                        .get(0)
                        .retrieval()
                        .get()
                        .vertexAiSearch()
                        .isPresent())
                .isTrue();
        assertThat(config.tools()
                        .get()
                        .get(0)
                        .retrieval()
                        .get()
                        .vertexAiSearch()
                        .get()
                        .datastore()
                        .get())
                .isEqualTo("projects/123/locations/global/collections/default_collection/dataStores/my-datastore");
    }

    @Test
    void should_set_labels() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        Map<String, String> labels = new HashMap<>();
        labels.put("env", "prod");
        labels.put("team", "billing");

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, false, false, false, null, null, labels, null);

        assertThat(config.labels().isPresent()).isTrue();
        assertThat(config.labels().get()).containsEntry("env", "prod").containsEntry("team", "billing");
    }
}
