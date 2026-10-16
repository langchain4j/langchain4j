package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.Part;
import com.google.genai.types.PrebuiltVoiceConfig;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.SpeechConfig;
import com.google.genai.types.VoiceConfig;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
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
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

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
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

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
                parameters, null, safetySettings, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.safetySettings().get()).hasSize(1);
    }

    @Test
    void should_skip_empty_safety_settings() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, List.of(), null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config).isNotNull();
    }

    @Test
    void should_set_json_mime_type_from_response_format() {
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .responseFormat(
                        ResponseFormat.builder().type(ResponseFormatType.JSON).build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

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
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.responseMimeType().get()).isEqualTo("application/json");
        assertThat(config.responseSchema().isPresent()).isTrue();
        assertThat(config.responseSchema().get().type().get().toString()).contains("OBJECT");
    }

    @Test
    void should_set_thinking_config() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, 1024, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig().get().thinkingBudget().get()).isEqualTo(1024);
    }

    @Test
    void should_set_thinking_level() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, "MEDIUM", null, null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig().get().thinkingLevel().get().toString())
                .contains("MEDIUM");
    }

    @Test
    void should_throw_if_both_thinking_config_are_set() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        assertThatThrownBy(() -> GoogleGenAiConfigBuilder.buildConfig(
                        parameters,
                        null,
                        null,
                        1024,
                        "MEDIUM",
                        null,
                        null,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null))
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
                parameters, null, null, null, null, null, 42, false, false, false, null, null, null, null);

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
                parameters,
                systemInstruction,
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
                null);

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
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

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
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

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
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

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
                parameters,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                List.of("getWeather"),
                null,
                null,
                null);

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
                parameters, null, null, null, null, null, null, true, false, false, null, null, null, null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).googleSearch().isPresent()).isTrue();
    }

    @Test
    void should_enable_google_maps_when_no_tools() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, true, false, null, null, null, null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).googleMaps().isPresent()).isTrue();
    }

    @Test
    void should_enable_url_context_when_no_tools() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, false, true, null, null, null, null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).urlContext().isPresent()).isTrue();
    }

    @Test
    void should_enable_all_google_tools_together() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, true, true, true, null, null, null, null);

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
                parameters, null, null, null, null, null, null, true, true, true, null, null, null, null);

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
                parameters, null, null, null, null, null, null, true, false, false, null, null, null, null);

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
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

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
                parameters, null, null, null, null, null, null, false, false, false, null, null, labels, null);

        assertThat(config.labels().isPresent()).isTrue();
        assertThat(config.labels().get()).containsEntry("env", "prod").containsEntry("team", "billing");
    }

    @Test
    void should_apply_generate_content_config_customizer() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
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
                null,
                builder -> builder.seed(123));

        assertThat(config.seed().get()).isEqualTo(123);
    }

    @Test
    void should_let_customizer_override_existing_values() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().temperature(0.7).build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
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
                null,
                builder -> builder.temperature(0.2f));

        assertThat(config.temperature().get()).isEqualTo(0.2f);
    }

    @Test
    void should_preserve_tools_when_customizer_is_applied() {
        ToolSpecification toolSpec = ToolSpecification.builder()
                .name("getWeather")
                .description("Get weather")
                .build();
        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .toolSpecifications(List.of(toolSpec))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
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
                null,
                builder -> builder.seed(7));

        assertThat(config.tools().get()).isNotEmpty();
        assertThat(config.seed().get()).isEqualTo(7);
    }

    @Test
    void should_build_config_unchanged_when_customizer_is_null() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().temperature(0.7).build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null, null);

        assertThat(config.temperature().get()).isEqualTo(0.7f);
    }

    @Test
    void should_set_include_thoughts() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, true, null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig().get().includeThoughts()).hasValue(true);
    }

    @Test
    void should_set_include_thoughts_to_false() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, false, null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig().get().includeThoughts()).hasValue(false);
    }

    @Test
    void should_not_set_thinking_config_when_no_thinking_option_is_set() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig()).isEmpty();
    }

    @Test
    void should_set_include_thoughts_together_with_thinking_budget() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, 1024, null, true, null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig().get().thinkingBudget()).hasValue(1024);
        assertThat(config.thinkingConfig().get().includeThoughts()).hasValue(true);
    }

    @Test
    void should_set_include_thoughts_together_with_thinking_level() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, "MEDIUM", true, null, false, false, false, null, null, null, null);

        assertThat(config.thinkingConfig().get().thinkingLevel().get().toString())
                .contains("MEDIUM");
        assertThat(config.thinkingConfig().get().includeThoughts()).hasValue(true);
    }

    @Test
    void should_set_audio_transcription_config() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        AudioTranscriptionConfig transcriptionConfig = AudioTranscriptionConfig.builder()
                .mode("VERBATIM")
                .languageCodes(List.of("en", "fr"))
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
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
                null,
                transcriptionConfig,
                null);

        assertThat(config.audioTranscriptionConfig()).isPresent();
        assertThat(config.audioTranscriptionConfig().get().mode().get().toString())
                .contains("VERBATIM");
        assertThat(config.audioTranscriptionConfig().get().languageCodes().get())
                .containsExactly("en", "fr");
    }

    @Test
    void should_not_set_audio_transcription_config_when_not_provided() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
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
                null,
                null,
                null);

        assertThat(config.audioTranscriptionConfig()).isEmpty();
    }

    @Test
    void should_set_speech_config_and_response_modalities_and_automatic_continuation() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        SpeechConfig speechConfig = SpeechConfig.builder()
                .languageCode("en-US")
                .voiceConfig(VoiceConfig.builder()
                        .prebuiltVoiceConfig(
                                PrebuiltVoiceConfig.builder().voiceName("Kore").build())
                        .build())
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
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
                null,
                null,
                speechConfig,
                List.of("AUDIO"),
                true,
                null);

        assertThat(config.speechConfig()).isPresent();
        assertThat(config.speechConfig().get().languageCode()).hasValue("en-US");
        assertThat(config.speechConfig()
                        .get()
                        .voiceConfig()
                        .get()
                        .prebuiltVoiceConfig()
                        .get()
                        .voiceName())
                .hasValue("Kore");
        assertThat(config.responseModalities()).isPresent();
        assertThat(config.responseModalities().get()).containsExactly("AUDIO");
        assertThat(config.automaticContinuation()).hasValue(true);
    }

    @Test
    void should_override_labels_cached_content_and_automatic_continuation_from_google_request_parameters() {
        GoogleGenAiChatRequestParameters parameters = GoogleGenAiChatRequestParameters.builder()
                .labels(Map.of("env", "staging", "requestId", "req-42"))
                .cachedContent("projects/123/locations/us-central1/cachedContents/override")
                .automaticContinuation(false)
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
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
                Map.of("env", "prod"),
                "projects/123/locations/us-central1/cachedContents/default",
                null,
                null,
                null,
                true,
                null);

        assertThat(config.labels()).isPresent();
        assertThat(config.labels().get())
                .containsEntry("env", "staging")
                .containsEntry("requestId", "req-42")
                .hasSize(2);
        assertThat(config.cachedContent()).hasValue("projects/123/locations/us-central1/cachedContents/override");
        assertThat(config.automaticContinuation()).hasValue(false);
    }

    @Test
    void should_verify_google_gen_ai_chat_request_parameters_override_equals_hashcode_and_to_string() {
        GoogleGenAiChatRequestParameters first = GoogleGenAiChatRequestParameters.builder()
                .temperature(0.7)
                .cachedContent("cache-1")
                .labels(Map.of("k", "v"))
                .automaticContinuation(true)
                .build();

        GoogleGenAiChatRequestParameters second = GoogleGenAiChatRequestParameters.builder()
                .labels(Map.of("k2", "v2"))
                .automaticContinuation(false)
                .build();

        GoogleGenAiChatRequestParameters merged = first.overrideWith(second);
        assertThat(merged.labels()).containsExactlyEntriesOf(Map.of("k2", "v2"));
        assertThat(merged.automaticContinuation()).isFalse();
        assertThat(merged.temperature()).isEqualTo(0.7);
        assertThat(merged.cachedContent()).isEqualTo("cache-1");

        GoogleGenAiChatRequestParameters copyOfFirst =
                GoogleGenAiChatRequestParameters.builder().overrideWith(first).build();
        assertThat(copyOfFirst).isEqualTo(first).hasSameHashCodeAs(first);
        assertThat(first).isNotEqualTo(merged);
        assertThat(first.toString())
                .contains("labels={k=v}")
                .contains("automaticContinuation=true")
                .contains("cachedContent=\"cache-1\"");
    }

    @Test
    void should_use_response_json_schema_when_root_element_is_raw_schema() {
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("Answer")
                        .rootElement(JsonRawSchema.from(
                                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}"))
                        .build())
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .responseFormat(responseFormat)
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.responseMimeType()).hasValue("application/json");
        assertThat(config.responseSchema()).isEmpty();
        assertThat(config.responseJsonSchema()).isPresent();
        assertThat(config.responseJsonSchema().get()).isInstanceOf(Map.class);
    }

    @Test
    void should_use_response_json_schema_when_root_element_has_definitions() {
        JsonObjectSchema personSchema = JsonObjectSchema.builder()
                .addStringProperty("name")
                .addProperty(
                        "parent",
                        JsonReferenceSchema.builder().reference("Person").build())
                .definitions(Map.of(
                        "Person",
                        JsonObjectSchema.builder().addStringProperty("name").build()))
                .build();

        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("Person")
                        .rootElement(personSchema)
                        .build())
                .build();

        ChatRequestParameters parameters = DefaultChatRequestParameters.builder()
                .responseFormat(responseFormat)
                .build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, false, false, false, null, null, null, null);

        assertThat(config.responseMimeType()).hasValue("application/json");
        assertThat(config.responseSchema()).isEmpty();
        assertThat(config.responseJsonSchema()).isPresent();
    }

    @Test
    void should_enable_code_execution_when_no_tools() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThat(config.tools().get()).hasSize(1);
        assertThat(config.tools().get().get(0).codeExecution()).isPresent();
    }

    @Test
    void should_add_code_execution_after_the_other_google_tools() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters,
                null,
                null,
                null,
                null,
                null,
                null,
                true,
                true,
                true,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThat(config.tools().get()).hasSize(4);
        assertThat(config.tools().get().get(0).googleSearch()).isPresent();
        assertThat(config.tools().get().get(1).googleMaps()).isPresent();
        assertThat(config.tools().get().get(2).urlContext()).isPresent();
        assertThat(config.tools().get().get(3).codeExecution()).isPresent();
    }

    @Test
    void should_not_enable_code_execution_through_the_overloads_without_it() {
        ChatRequestParameters parameters =
                DefaultChatRequestParameters.builder().build();

        GenerateContentConfig config = GoogleGenAiConfigBuilder.buildConfig(
                parameters, null, null, null, null, null, null, true, true, true, null, null, null, null);

        assertThat(config.tools().get())
                .hasSize(3)
                .allSatisfy(tool -> assertThat(tool.codeExecution()).isEmpty());
    }
}
