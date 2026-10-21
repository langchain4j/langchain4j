package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.genai.AsyncModels;
import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.types.AudioTranscriptionConfig;
import com.google.genai.types.Candidate;
import com.google.genai.types.CodeExecutionResult;
import com.google.genai.types.Content;
import com.google.genai.types.ExecutableCode;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import com.google.genai.types.SpeechConfig;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleGenAiChatModelTest {

    @Test
    void should_build_with_api_key_and_model_name() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .build();

        assertThat(model).isNotNull();
        assertThat(model.provider()).isEqualTo(ModelProvider.GOOGLE_GENAI);
    }

    @Test
    void should_build_with_custom_client() {
        Client client = Client.builder().apiKey("test").build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-3.1-flash-lite")
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_set_default_request_parameters() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .temperature(0.7)
                .topP(0.9)
                .topK(40)
                .frequencyPenalty(0.5)
                .presencePenalty(0.3)
                .maxOutputTokens(512)
                .stopSequences(List.of("END"))
                .build();

        assertThat(model.defaultRequestParameters().temperature()).isEqualTo(0.7);
        assertThat(model.defaultRequestParameters().topP()).isEqualTo(0.9);
        assertThat(model.defaultRequestParameters().topK()).isEqualTo(40);
        assertThat(model.defaultRequestParameters().frequencyPenalty()).isEqualTo(0.5);
        assertThat(model.defaultRequestParameters().presencePenalty()).isEqualTo(0.3);
        assertThat(model.defaultRequestParameters().maxOutputTokens()).isEqualTo(512);
        assertThat(model.defaultRequestParameters().stopSequences()).containsExactly("END");
    }

    @Test
    void should_return_empty_listeners_by_default() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .build();

        assertThat(model.listeners()).isEmpty();
    }

    @Test
    void should_return_listeners_when_configured() {
        ChatModelListener listener = mock(ChatModelListener.class);

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .listeners(List.of(listener))
                .build();

        assertThat(model.listeners()).hasSize(1);
    }

    @Test
    void should_always_advertise_json_schema_capability() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .build();

        assertThat(model.supportedCapabilities()).containsExactly(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @Test
    void should_build_with_all_builder_options() {
        Client client = Client.builder().apiKey("test").build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-3.1-flash-lite")
                .temperature(0.5)
                .topP(0.8)
                .topK(30)
                .frequencyPenalty(0.5)
                .presencePenalty(0.3)
                .maxOutputTokens(1024)
                .thinkingBudget(500)
                .includeThoughts(true)
                .returnThinking(true)
                .sendThinking(true)
                .seed(42)
                .stopSequences(List.of("STOP"))
                .maxRetries(5)
                .safetySettings(List.of(SafetySetting.builder().build()))
                .responseFormat(ResponseFormat.JSON)
                .enableGoogleSearch(true)
                .enableGoogleMaps(true)
                .enableUrlContext(true)
                .allowedFunctionNames(List.of("fn1"))
                .listeners(List.of(mock(ChatModelListener.class)))
                .timeout(Duration.ofSeconds(30))
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_build_with_null_optional_fields() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .maxRetries(null)
                .listeners(null)
                .safetySettings(null)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.listeners()).isEmpty();
    }

    @Test
    void should_support_all_builder_setters() {
        GoogleGenAiChatModel.Builder builder = GoogleGenAiChatModel.builder();

        assertThat(builder.apiKey("key")).isSameAs(builder);
        assertThat(builder.modelName("model")).isSameAs(builder);
        assertThat(builder.temperature(0.5)).isSameAs(builder);
        assertThat(builder.topP(0.8)).isSameAs(builder);
        assertThat(builder.topK(40)).isSameAs(builder);
        assertThat(builder.frequencyPenalty(0.5)).isSameAs(builder);
        assertThat(builder.presencePenalty(0.3)).isSameAs(builder);
        assertThat(builder.maxOutputTokens(100)).isSameAs(builder);
        assertThat(builder.thinkingBudget(500)).isSameAs(builder);
        assertThat(builder.includeThoughts(true)).isSameAs(builder);
        assertThat(builder.returnThinking(true)).isSameAs(builder);
        assertThat(builder.sendThinking(true)).isSameAs(builder);
        assertThat(builder.seed(42)).isSameAs(builder);
        assertThat(builder.stopSequences(List.of("STOP"))).isSameAs(builder);
        assertThat(builder.maxRetries(3)).isSameAs(builder);
        assertThat(builder.timeout(Duration.ofSeconds(10))).isSameAs(builder);
        assertThat(builder.enableGoogleSearch(true)).isSameAs(builder);
        assertThat(builder.enableGoogleMaps(true)).isSameAs(builder);
        assertThat(builder.enableUrlContext(true)).isSameAs(builder);
        assertThat(builder.safetySettings(List.of())).isSameAs(builder);
        assertThat(builder.responseFormat(ResponseFormat.JSON)).isSameAs(builder);
        assertThat(builder.allowedFunctionNames(List.of("fn1"))).isSameAs(builder);
        assertThat(builder.listeners(List.of())).isSameAs(builder);
        assertThat(builder.audioTranscriptionConfig(
                        AudioTranscriptionConfig.builder().build()))
                .isSameAs(builder);
        assertThat(builder.speechConfig(SpeechConfig.builder().build())).isSameAs(builder);
        assertThat(builder.responseModalities(List.of("AUDIO"))).isSameAs(builder);
        assertThat(builder.automaticContinuation(true)).isSameAs(builder);
    }

    @Test
    void should_build_with_google_credentials_builder_setters() {
        GoogleGenAiChatModel.Builder builder = GoogleGenAiChatModel.builder();

        assertThat(builder.googleCredentials(null)).isSameAs(builder);
        assertThat(builder.projectId("project")).isSameAs(builder);
        assertThat(builder.location("us-central1")).isSameAs(builder);
    }

    @Test
    void should_invoke_generate_content_config_customizer_when_building_the_request() {
        RuntimeException fromCustomizer = new RuntimeException("customizer invoked");
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.8-flash")
                .generateContentConfigCustomizer(config -> {
                    throw fromCustomizer;
                })
                .build();

        // customizer runs before the SDK call, so its exception surfaces without any network access
        assertThatThrownBy(() -> model.chat("hello")).isSameAs(fromCustomizer);
    }

    @Test
    void should_use_request_level_cached_content() throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        GenerateContentResponse mockResponse = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.builder().text("response").build()))
                                .build())
                        .build()))
                .build();

        ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);

        when(models.generateContent(any(String.class), anyList(), configCaptor.capture()))
                .thenReturn(mockResponse);

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-3.1-flash-lite")
                .cachedContent("projects/123/locations/us-central1/cachedContents/global")
                .build();

        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("Hello"))
                .parameters(GoogleGenAiChatRequestParameters.builder()
                        .cachedContent("projects/123/locations/us-central1/cachedContents/per-request")
                        .build())
                .build();

        model.chat(request);

        assertThat(configCaptor.getValue().cachedContent().isPresent()).isTrue();
        assertThat(configCaptor.getValue().cachedContent().get())
                .isEqualTo("projects/123/locations/us-central1/cachedContents/per-request");
    }

    @Test
    void should_send_audio_transcription_config() throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        GenerateContentResponse mockResponse = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.builder().text("transcript").build()))
                                .build())
                        .build()))
                .build();

        ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);

        when(models.generateContent(any(String.class), anyList(), configCaptor.capture()))
                .thenReturn(mockResponse);

        AudioTranscriptionConfig audioTranscriptionConfig = AudioTranscriptionConfig.builder()
                .mode("SMART")
                .languageCodes(List.of("en-US"))
                .build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-3.5-transcribe")
                .audioTranscriptionConfig(audioTranscriptionConfig)
                .build();

        model.chat(ChatRequest.builder().messages(UserMessage.from("Hello")).build());

        assertThat(configCaptor.getValue().audioTranscriptionConfig()).contains(audioTranscriptionConfig);
    }

    @Test
    void should_send_speech_config_response_modalities_automatic_continuation_and_override_with_request_parameters()
            throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        GenerateContentResponse mockResponse = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.builder().text("spoken").build()))
                                .build())
                        .build()))
                .build();

        ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);

        when(models.generateContent(any(String.class), anyList(), configCaptor.capture()))
                .thenReturn(mockResponse);

        SpeechConfig speechConfig = SpeechConfig.builder().languageCode("en-US").build();

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-3.8-flash-tts")
                .speechConfig(speechConfig)
                .responseModalities(List.of("AUDIO"))
                .automaticContinuation(false)
                .labels(Map.of("env", "default"))
                .build();

        model.chat(ChatRequest.builder()
                .messages(UserMessage.from("Say hello"))
                .parameters(GoogleGenAiChatRequestParameters.builder()
                        .automaticContinuation(true)
                        .labels(Map.of("env", "override"))
                        .build())
                .build());

        assertThat(configCaptor.getValue().speechConfig()).contains(speechConfig);
        assertThat(configCaptor.getValue().responseModalities()).contains(List.of("AUDIO"));
        assertThat(configCaptor.getValue().automaticContinuation()).contains(true);
        assertThat(configCaptor.getValue().labels()).contains(Map.of("env", "override"));
    }

    @Test
    void global_cached_content_should_be_reflected_in_default_request_parameters() {
        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(mock(Client.class))
                .modelName("gemini-3.1-flash-lite")
                .cachedContent("projects/123/locations/us-central1/cachedContents/global")
                .build();

        assertThat(model.defaultRequestParameters()).isInstanceOf(GoogleGenAiChatRequestParameters.class);
        assertThat(((GoogleGenAiChatRequestParameters) model.defaultRequestParameters()).cachedContent())
                .isEqualTo("projects/123/locations/us-central1/cachedContents/global");
    }

    @Test
    void defaulted_by_should_preserve_cached_content() {
        GoogleGenAiChatRequestParameters requestParameters = GoogleGenAiChatRequestParameters.builder()
                .cachedContent("projects/123/locations/us-central1/cachedContents/per-request")
                .build();

        // Mirrors how AiServiceParamsUtil merges request parameters with defaults.
        ChatRequestParameters merged = requestParameters.defaultedBy(
                GoogleGenAiChatRequestParameters.builder().temperature(0.5).build());

        assertThat(merged).isInstanceOf(GoogleGenAiChatRequestParameters.class);
        assertThat(((GoogleGenAiChatRequestParameters) merged).cachedContent())
                .isEqualTo("projects/123/locations/us-central1/cachedContents/per-request");
        assertThat(merged.temperature()).isEqualTo(0.5);
    }

    @Test
    void should_chat_async_using_async_models() throws Exception {
        Client client = mock(Client.class);
        Client.Async async = mock(Client.Async.class);
        AsyncModels asyncModels = mock(AsyncModels.class);

        Field asyncField = Client.class.getDeclaredField("async");
        asyncField.setAccessible(true);
        asyncField.set(client, async);

        Field asyncModelsField = Client.Async.class.getDeclaredField("models");
        asyncModelsField.setAccessible(true);
        asyncModelsField.set(async, asyncModels);

        GenerateContentResponse mockResponse = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(
                                        Part.builder().text("Async hello!").build()))
                                .build())
                        .build()))
                .build();

        when(asyncModels.generateContent(any(String.class), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-3.1-flash-lite")
                .build();

        CompletableFuture<ChatResponse> future = model.chatAsync(
                ChatRequest.builder().messages(UserMessage.from("Hi")).build());

        ChatResponse response = future.get();
        assertThat(response.aiMessage().text()).isEqualTo("Async hello!");
    }

    @Test
    void should_add_executed_code_to_text_in_async_chat() throws Exception {
        Client client = mock(Client.class);
        Client.Async async = mock(Client.Async.class);
        AsyncModels asyncModels = mock(AsyncModels.class);

        Field asyncField = Client.class.getDeclaredField("async");
        asyncField.setAccessible(true);
        asyncField.set(client, async);

        Field asyncModelsField = Client.Async.class.getDeclaredField("models");
        asyncModelsField.setAccessible(true);
        asyncModelsField.set(async, asyncModels);

        when(asyncModels.generateContent(any(String.class), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(responseWithParts(
                        Part.builder()
                                .executableCode(ExecutableCode.builder()
                                        .language("PYTHON")
                                        .code("print(6 * 7)"))
                                .build(),
                        Part.builder()
                                .codeExecutionResult(CodeExecutionResult.builder()
                                        .outcome("OUTCOME_OK")
                                        .output("42\n"))
                                .build(),
                        Part.fromText("6 times 7 is 42."))));

        GoogleGenAiChatModel model = GoogleGenAiChatModel.builder()
                .client(client)
                .modelName("gemini-3.8-flash")
                .enableCodeExecution(true)
                .includeCodeExecutionOutput(true)
                .build();

        ChatResponse response = model.chatAsync(ChatRequest.builder()
                        .messages(UserMessage.from("What is 6 times 7?"))
                        .build())
                .get();

        assertThat(response.aiMessage().text())
                .isEqualTo("Code executed:\n```python\nprint(6 * 7)\n```\n\nOutput:\n```\n42\n```\n\n"
                        + "6 times 7 is 42.");
    }

    @Test
    void should_send_code_execution_tool_when_enabled() throws Exception {
        Models models = mockModels();
        ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
        when(models.generateContent(any(String.class), anyList(), configCaptor.capture()))
                .thenReturn(responseWithParts(Part.fromText("42")));

        GoogleGenAiChatModel.builder()
                .client(clientWith(models))
                .modelName("gemini-3.8-flash")
                .enableCodeExecution(true)
                .build()
                .chat("What is 6 times 7?");

        assertThat(configCaptor.getValue().tools().get())
                .anySatisfy(tool -> assertThat(tool.codeExecution()).isPresent());
    }

    @Test
    void should_add_executed_code_to_text_when_requested() throws Exception {
        Models models = mockModels();
        when(models.generateContent(any(String.class), anyList(), any(GenerateContentConfig.class)))
                .thenReturn(responseWithParts(
                        Part.builder()
                                .executableCode(ExecutableCode.builder()
                                        .language("PYTHON")
                                        .code("print(6 * 7)"))
                                .build(),
                        Part.builder()
                                .codeExecutionResult(CodeExecutionResult.builder()
                                        .outcome("OUTCOME_OK")
                                        .output("42\n"))
                                .build(),
                        Part.fromText("6 times 7 is 42.")));

        ChatResponse response = GoogleGenAiChatModel.builder()
                .client(clientWith(models))
                .modelName("gemini-3.8-flash")
                .enableCodeExecution(true)
                .includeCodeExecutionOutput(true)
                .build()
                .chat(ChatRequest.builder()
                        .messages(UserMessage.from("What is 6 times 7?"))
                        .build());

        assertThat(response.aiMessage().text())
                .isEqualTo("Code executed:\n```python\nprint(6 * 7)\n```\n\nOutput:\n```\n42\n```\n\n"
                        + "6 times 7 is 42.");
    }

    private static Models mockModels() {
        return mock(Models.class);
    }

    private static Client clientWith(Models models) throws Exception {
        Client client = mock(Client.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);
        return client;
    }

    private static GenerateContentResponse responseWithParts(Part... parts) {
        return GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(parts))
                                .build())
                        .build()))
                .build();
    }
}
