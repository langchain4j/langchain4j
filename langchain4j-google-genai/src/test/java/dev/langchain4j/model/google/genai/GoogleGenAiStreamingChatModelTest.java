package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.genai.Client;
import com.google.genai.Models;
import com.google.genai.ResponseStream;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GoogleGenAiStreamingChatModelTest {

    @Test
    void should_build_with_api_key_and_model_name() {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .build();

        assertThat(model).isNotNull();
        assertThat(model.provider()).isEqualTo(ModelProvider.GOOGLE_GENAI);
    }

    @Test
    void should_build_with_custom_client() {
        Client client = Client.builder().apiKey("test").build();

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(client)
                .modelName("gemini-3.1-flash-lite")
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_set_default_request_parameters() {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
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
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .build();

        assertThat(model.listeners()).isEmpty();
    }

    @Test
    void should_return_listeners_when_configured() {
        ChatModelListener listener = mock(ChatModelListener.class);

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .listeners(List.of(listener))
                .build();

        assertThat(model.listeners()).hasSize(1);
    }

    @Test
    void should_always_advertise_json_schema_capability() {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .build();

        assertThat(model.supportedCapabilities()).containsExactly(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
    }

    @Test
    void should_build_with_all_builder_options() {
        Client client = Client.builder().apiKey("test").build();

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
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
                .safetySettings(List.of(SafetySetting.builder().build()))
                .responseFormat(ResponseFormat.JSON)
                .enableGoogleSearch(true)
                .enableGoogleMaps(true)
                .enableUrlContext(true)
                .allowedFunctionNames(List.of("fn1"))
                .listeners(List.of(mock(ChatModelListener.class)))
                .timeout(Duration.ofSeconds(30))
                .executor(mock(ExecutorService.class))
                .build();

        assertThat(model).isNotNull();
    }

    @Test
    void should_build_with_null_optional_fields() {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .apiKey("test-key")
                .modelName("gemini-3.1-flash-lite")
                .listeners(null)
                .safetySettings(null)
                .executor(null)
                .build();

        assertThat(model).isNotNull();
        assertThat(model.listeners()).isEmpty();
    }

    @Test
    void should_support_all_builder_setters() {
        GoogleGenAiStreamingChatModel.Builder builder = GoogleGenAiStreamingChatModel.builder();

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
        assertThat(builder.timeout(Duration.ofSeconds(10))).isSameAs(builder);
        assertThat(builder.enableGoogleSearch(true)).isSameAs(builder);
        assertThat(builder.enableGoogleMaps(true)).isSameAs(builder);
        assertThat(builder.enableUrlContext(true)).isSameAs(builder);
        assertThat(builder.safetySettings(List.of())).isSameAs(builder);
        assertThat(builder.responseFormat(ResponseFormat.JSON)).isSameAs(builder);
        assertThat(builder.allowedFunctionNames(List.of("fn1"))).isSameAs(builder);
        assertThat(builder.listeners(List.of())).isSameAs(builder);
        assertThat(builder.executor(mock(ExecutorService.class))).isSameAs(builder);
    }

    @Test
    void should_build_with_google_credentials_builder_setters() {
        GoogleGenAiStreamingChatModel.Builder builder = GoogleGenAiStreamingChatModel.builder();

        assertThat(builder.googleCredentials(null)).isSameAs(builder);
        assertThat(builder.projectId("project")).isSameAs(builder);
        assertThat(builder.location("us-central1")).isSameAs(builder);
    }

    @Test
    void should_accumulate_attributes_from_streaming_chunks() throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        @SuppressWarnings("unchecked")
        ResponseStream<GenerateContentResponse> stream = mock(ResponseStream.class);

        when(models.generateContentStream(any(String.class), any(List.class), any()))
                .thenReturn(stream);

        // Create a mock chunk with a function call and thought signature
        Map<String, Object> args = new HashMap<>();
        args.put("location", "Paris");

        FunctionCall functionCall = FunctionCall.builder()
                .name("get_weather")
                .id("call_123")
                .args(args)
                .build();

        Part part = Part.builder()
                .functionCall(functionCall)
                .thoughtSignature("signature-data".getBytes())
                .build();

        Content content = Content.builder().role("model").parts(List.of(part)).build();

        Candidate candidate = Candidate.builder().content(content).build();

        GenerateContentResponse chunk =
                GenerateContentResponse.builder().candidates(List.of(candidate)).build();

        when(stream.iterator()).thenReturn(List.of(chunk).iterator());

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(client)
                .modelName("gemini-3.5-flash")
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        model.chat(List.of(UserMessage.from("What's the weather in Paris?")), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(5, TimeUnit.SECONDS);
        AiMessage aiMessage = response.aiMessage();

        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
        assertThat(aiMessage.attributes()).isNotEmpty();
        String encodedSig = Base64.getEncoder().encodeToString("signature-data".getBytes());
        assertThat(aiMessage.attribute("thought_signature_call_123", String.class))
                .isEqualTo(encodedSig);
    }

    @Test
    void should_not_overwrite_truncation_finish_reason_with_stop() throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        @SuppressWarnings("unchecked")
        ResponseStream<GenerateContentResponse> stream = mock(ResponseStream.class);

        when(models.generateContentStream(any(String.class), any(List.class), any()))
                .thenReturn(stream);

        // Chunk 1: FinishReason.MAX_TOKENS -> maps to LENGTH
        Candidate candidate1 = Candidate.builder()
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder().text("First part").build()))
                        .build())
                .finishReason(
                        new com.google.genai.types.FinishReason(com.google.genai.types.FinishReason.Known.MAX_TOKENS))
                .build();
        GenerateContentResponse chunk1 = GenerateContentResponse.builder()
                .candidates(List.of(candidate1))
                .build();

        // Chunk 2: FinishReason.STOP -> maps to STOP (trailing chunk)
        Candidate candidate2 = Candidate.builder()
                .content(Content.builder()
                        .role("model")
                        .parts(List.of(Part.builder().text("").build()))
                        .build())
                .finishReason(new com.google.genai.types.FinishReason(com.google.genai.types.FinishReason.Known.STOP))
                .build();
        GenerateContentResponse chunk2 = GenerateContentResponse.builder()
                .candidates(List.of(candidate2))
                .build();

        when(stream.iterator()).thenReturn(List.of(chunk1, chunk2).iterator());

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(client)
                .modelName("gemini-3.5-flash")
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        model.chat(List.of(UserMessage.from("Hello")), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(5, TimeUnit.SECONDS);

        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.LENGTH);
    }

    @Test
    void should_use_request_level_cached_content() throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        @SuppressWarnings("unchecked")
        ResponseStream<GenerateContentResponse> stream = mock(ResponseStream.class);

        when(models.generateContentStream(any(String.class), any(List.class), any(GenerateContentConfig.class)))
                .thenReturn(stream);

        when(stream.iterator()).thenReturn(List.<GenerateContentResponse>of().iterator());

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
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

        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {}

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        future.get(30, TimeUnit.SECONDS);

        ArgumentCaptor<GenerateContentConfig> configCaptor = ArgumentCaptor.forClass(GenerateContentConfig.class);
        verify(models).generateContentStream(any(String.class), any(List.class), configCaptor.capture());

        assertThat(configCaptor.getValue().cachedContent())
                .contains("projects/123/locations/us-central1/cachedContents/per-request");
    }

    private static Client clientStreamingThoughtAndAnswer() throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        @SuppressWarnings("unchecked")
        ResponseStream<GenerateContentResponse> stream = mock(ResponseStream.class);
        when(models.generateContentStream(any(String.class), any(List.class), any()))
                .thenReturn(stream);

        Content content = Content.builder()
                .role("model")
                .parts(List.of(
                        Part.builder().text("Thinking it over.").thought(true).build(),
                        Part.builder().text("42").build()))
                .build();
        GenerateContentResponse chunk = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder().content(content).build()))
                .build();
        when(stream.iterator()).thenReturn(List.of(chunk).iterator());
        return client;
    }

    private static ChatResponse stream(
            GoogleGenAiStreamingChatModel model, List<String> partialResponses, List<String> partialThinking)
            throws Exception {
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        model.chat(List.of(UserMessage.from("What is 6 times 7?")), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                partialResponses.add(partialResponse);
            }

            @Override
            public void onPartialThinking(PartialThinking thinking) {
                partialThinking.add(thinking.text());
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });
        return future.get(5, TimeUnit.SECONDS);
    }

    @Test
    void should_report_thought_summary_through_on_partial_thinking_when_return_thinking_is_true() throws Exception {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(clientStreamingThoughtAndAnswer())
                .modelName("gemini-3.5-flash")
                .returnThinking(true)
                .build();

        List<String> partialResponses = new ArrayList<>();
        List<String> partialThinking = new ArrayList<>();
        ChatResponse response = stream(model, partialResponses, partialThinking);

        assertThat(partialThinking).containsExactly("Thinking it over.");
        assertThat(partialResponses).containsExactly("42");
        assertThat(response.aiMessage().thinking()).isEqualTo("Thinking it over.");
        assertThat(response.aiMessage().text()).isEqualTo("42");
    }

    @Test
    void should_drop_thought_summary_when_return_thinking_is_not_set() throws Exception {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(clientStreamingThoughtAndAnswer())
                .modelName("gemini-3.5-flash")
                .build();

        List<String> partialResponses = new ArrayList<>();
        List<String> partialThinking = new ArrayList<>();
        ChatResponse response = stream(model, partialResponses, partialThinking);

        assertThat(partialThinking).isEmpty();
        assertThat(partialResponses).containsExactly("42");
        assertThat(response.aiMessage().thinking()).isNull();
        assertThat(response.aiMessage().text()).isEqualTo("42");
    }

    @Test
    void should_drop_thought_summary_when_return_thinking_is_false() throws Exception {
        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(clientStreamingThoughtAndAnswer())
                .modelName("gemini-3.5-flash")
                .returnThinking(false)
                .build();

        List<String> partialResponses = new ArrayList<>();
        List<String> partialThinking = new ArrayList<>();
        ChatResponse response = stream(model, partialResponses, partialThinking);

        assertThat(partialThinking).isEmpty();
        assertThat(partialResponses).containsExactly("42");
        assertThat(response.aiMessage().thinking()).isNull();
        assertThat(response.aiMessage().text()).isEqualTo("42");
    }

    @Test
    void should_accumulate_thought_summaries_across_streaming_chunks() throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        @SuppressWarnings("unchecked")
        ResponseStream<GenerateContentResponse> stream = mock(ResponseStream.class);
        when(models.generateContentStream(any(String.class), any(List.class), any()))
                .thenReturn(stream);

        GenerateContentResponse firstChunk = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(Part.builder()
                                        .text("Six ")
                                        .thought(true)
                                        .build()))
                                .build())
                        .build()))
                .build();
        GenerateContentResponse secondChunk = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(
                                        Part.builder()
                                                .text("times seven.")
                                                .thought(true)
                                                .build(),
                                        Part.builder().text("42").build()))
                                .build())
                        .build()))
                .build();
        when(stream.iterator()).thenReturn(List.of(firstChunk, secondChunk).iterator());

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(client)
                .modelName("gemini-3.5-flash")
                .returnThinking(true)
                .build();

        List<String> partialResponses = new ArrayList<>();
        List<String> partialThinking = new ArrayList<>();
        ChatResponse response = stream(model, partialResponses, partialThinking);

        assertThat(partialThinking).containsExactly("Six ", "times seven.");
        assertThat(partialResponses).containsExactly("42");
        assertThat(response.aiMessage().thinking()).isEqualTo("Six times seven.");
        assertThat(response.aiMessage().text()).isEqualTo("42");
    }

    @Test
    void should_keep_both_thought_summary_and_tool_call_signature_when_streaming() throws Exception {
        Client client = mock(Client.class);
        Models models = mock(Models.class);
        Field modelsField = Client.class.getDeclaredField("models");
        modelsField.setAccessible(true);
        modelsField.set(client, models);

        @SuppressWarnings("unchecked")
        ResponseStream<GenerateContentResponse> stream = mock(ResponseStream.class);
        when(models.generateContentStream(any(String.class), any(List.class), any()))
                .thenReturn(stream);

        FunctionCall functionCall = FunctionCall.builder()
                .name("get_weather")
                .id("call_1")
                .args(Map.of("city", "Paris"))
                .build();
        GenerateContentResponse chunk = GenerateContentResponse.builder()
                .candidates(List.of(Candidate.builder()
                        .content(Content.builder()
                                .role("model")
                                .parts(List.of(
                                        Part.builder()
                                                .text("I should call the tool.")
                                                .thought(true)
                                                .build(),
                                        Part.builder()
                                                .functionCall(functionCall)
                                                .thoughtSignature("sig".getBytes())
                                                .build()))
                                .build())
                        .build()))
                .build();
        when(stream.iterator()).thenReturn(List.of(chunk).iterator());

        GoogleGenAiStreamingChatModel model = GoogleGenAiStreamingChatModel.builder()
                .client(client)
                .modelName("gemini-3.5-flash")
                .returnThinking(true)
                .build();

        ChatResponse response = stream(model, new ArrayList<>(), new ArrayList<>());
        AiMessage aiMessage = response.aiMessage();

        assertThat(aiMessage.thinking()).isEqualTo("I should call the tool.");
        assertThat(aiMessage.hasToolExecutionRequests()).isTrue();
        assertThat(aiMessage.attribute("thought_signature_call_1", String.class))
                .isEqualTo(Base64.getEncoder().encodeToString("sig".getBytes()));
    }
}
