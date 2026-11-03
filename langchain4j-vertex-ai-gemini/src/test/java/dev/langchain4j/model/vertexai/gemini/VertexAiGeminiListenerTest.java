package dev.langchain4j.model.vertexai.gemini;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.cloud.vertexai.api.Candidate;
import com.google.cloud.vertexai.api.Content;
import com.google.cloud.vertexai.api.GenerateContentResponse;
import com.google.cloud.vertexai.api.GenerationConfig;
import com.google.cloud.vertexai.api.Part;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.generativeai.ResponseStream;
import com.google.cloud.vertexai.generativeai.ResponseStreamIteratorWithHistory;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class VertexAiGeminiListenerTest {

    static final ChatRequest CHAT_REQUEST =
            ChatRequest.builder().messages(UserMessage.from("hi")).build();

    static final GenerationConfig GENERATION_CONFIG = GenerationConfig.newBuilder()
            .setTemperature(0.5f)
            .setTopP(0.25f)
            .setMaxOutputTokens(100)
            .build();

    final List<String> events = new CopyOnWriteArrayList<>();
    final List<ChatRequestParameters> requestParameters = new CopyOnWriteArrayList<>();

    @Test
    void chat_model_should_notify_listeners_once_with_the_configured_parameters() throws IOException {
        GenerativeModel generativeModel = generativeModel();
        when(generativeModel.generateContent(anyList())).thenReturn(response("hello"));
        VertexAiGeminiChatModel model =
                new VertexAiGeminiChatModel(generativeModel, GENERATION_CONFIG, List.of(recordingListener()));

        ChatResponse response = model.chat(CHAT_REQUEST);

        assertThat(response.aiMessage().text()).isEqualTo("hello");
        assertThat(events).containsExactly("request", "response");
        assertConfiguredParameters();
    }

    @Test
    void chat_model_should_notify_listeners_once_on_error() throws IOException {
        GenerativeModel generativeModel = generativeModel();
        when(generativeModel.generateContent(anyList())).thenThrow(new IllegalStateException("failed"));
        VertexAiGeminiChatModel model =
                new VertexAiGeminiChatModel(generativeModel, GENERATION_CONFIG, List.of(recordingListener()));

        assertThatThrownBy(() -> model.chat(CHAT_REQUEST)).hasMessageContaining("failed");

        assertThat(events).containsExactly("request", "error");
        assertConfiguredParameters();
    }

    @Test
    void streaming_chat_model_should_notify_listeners_once_with_the_configured_parameters() throws IOException {
        GenerativeModel generativeModel = generativeModel();
        when(generativeModel.generateContentStream(anyList())).thenReturn(responseStream(response("hello")));
        VertexAiGeminiStreamingChatModel model =
                new VertexAiGeminiStreamingChatModel(generativeModel, GENERATION_CONFIG, List.of(recordingListener()));
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();

        model.chat(CHAT_REQUEST, handler(response));

        assertThat(response).succeedsWithin(5, SECONDS);
        assertThat(response.join().aiMessage().text()).isEqualTo("hello");
        assertThat(events).containsExactly("request", "response");
        assertConfiguredParameters();
    }

    @Test
    void streaming_chat_model_should_notify_listeners_once_on_error() throws IOException {
        GenerativeModel generativeModel = generativeModel();
        when(generativeModel.generateContentStream(anyList())).thenThrow(new IllegalStateException("failed"));
        VertexAiGeminiStreamingChatModel model =
                new VertexAiGeminiStreamingChatModel(generativeModel, GENERATION_CONFIG, List.of(recordingListener()));
        CompletableFuture<ChatResponse> response = new CompletableFuture<>();

        model.chat(CHAT_REQUEST, handler(response));

        assertThat(response).failsWithin(5, SECONDS);
        assertThat(events).containsExactly("request", "error");
        assertConfiguredParameters();
    }

    private static GenerativeModel generativeModel() {
        GenerativeModel generativeModel = mock(GenerativeModel.class);
        when(generativeModel.getModelName()).thenReturn("gemini-test");
        when(generativeModel.withGenerationConfig(any())).thenReturn(generativeModel);
        when(generativeModel.withTools(anyList())).thenReturn(generativeModel);
        when(generativeModel.withToolConfig(any())).thenReturn(generativeModel);
        return generativeModel;
    }

    private static GenerateContentResponse response(String text) {
        return GenerateContentResponse.newBuilder()
                .addCandidates(Candidate.newBuilder()
                        .setContent(Content.newBuilder()
                                .setRole("model")
                                .addParts(Part.newBuilder().setText(text)))
                        .setFinishReason(Candidate.FinishReason.STOP))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static ResponseStream<GenerateContentResponse> responseStream(GenerateContentResponse response) {
        return new ResponseStream<>(new ResponseStreamIteratorWithHistory<>(List.of(response).iterator()));
    }

    private static StreamingChatResponseHandler handler(CompletableFuture<ChatResponse> response) {
        return new StreamingChatResponseHandler() {
            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                response.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                response.completeExceptionally(error);
            }
        };
    }

    private ChatModelListener recordingListener() {
        return new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext context) {
                events.add("request");
                requestParameters.add(context.chatRequest().parameters());
            }

            @Override
            public void onResponse(ChatModelResponseContext context) {
                events.add("response");
            }

            @Override
            public void onError(ChatModelErrorContext context) {
                events.add("error");
            }
        };
    }

    private void assertConfiguredParameters() {
        assertThat(requestParameters).singleElement().satisfies(parameters -> {
            assertThat(parameters.modelName()).isEqualTo("gemini-test");
            assertThat(parameters.temperature()).isEqualTo(0.5);
            assertThat(parameters.topP()).isEqualTo(0.25);
            assertThat(parameters.maxOutputTokens()).isEqualTo(100);
        });
    }
}
