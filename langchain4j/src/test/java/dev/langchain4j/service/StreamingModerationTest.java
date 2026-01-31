package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Tests for streaming moderation support in AiServices.
 * Verifies that the @Moderate annotation works correctly with streaming chat models.
 */
@ExtendWith(MockitoExtension.class)
class StreamingModerationTest {

    interface ModeratedStreamingChat {
        @Moderate
        TokenStream chat(String message);
    }

    interface NonModeratedStreamingChat {
        TokenStream chat(String message);
    }

    @Test
    void should_throw_ModerationException_when_content_is_flagged_in_streaming() throws Exception {
        // Given - a streaming model that returns a simple response
        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        doAnswer(invocation -> {
                    ChatRequest request = invocation.getArgument(0);
                    StreamingChatResponseHandler handler = invocation.getArgument(1);

                    // Simulate streaming response
                    handler.onPartialResponse("Hello ");
                    handler.onPartialResponse("World!");

                    ChatResponse response = ChatResponse.builder()
                            .aiMessage(AiMessage.from("Hello World!"))
                            .metadata(ChatResponseMetadata.builder()
                                    .tokenUsage(new TokenUsage(10, 5))
                                    .build())
                            .build();
                    handler.onCompleteResponse(response);
                    return null;
                })
                .when(streamingModel)
                .chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        // Given - a moderation model that flags content
        ModerationModel moderationModel = mock(ModerationModel.class);
        when(moderationModel.moderate(anyList()))
                .thenReturn(Response.from(Moderation.flagged("inappropriate content")));

        ModeratedStreamingChat chat = AiServices.builder(ModeratedStreamingChat.class)
                .streamingChatModel(streamingModel)
                .moderationModel(moderationModel)
                .build();

        // When
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean completedNormally = new AtomicBoolean(false);
        CompletableFuture<Void> future = new CompletableFuture<>();

        chat.chat("Hello!")
                .onPartialResponse(partial -> {})
                .onCompleteResponse(response -> {
                    completedNormally.set(true);
                    future.complete(null);
                })
                .onError(error -> {
                    errorRef.set(error);
                    future.complete(null);
                })
                .start();

        future.get(10, TimeUnit.SECONDS);

        // Then - should have received moderation exception
        assertThat(errorRef.get())
                .isNotNull()
                .isInstanceOf(ModerationException.class)
                .hasMessageContaining("violates content policy");
        assertThat(completedNormally.get()).isFalse();

        // Verify moderation model was called
        verify(moderationModel).moderate(anyList());
    }

    @Test
    void should_complete_normally_when_content_is_not_flagged_in_streaming() throws Exception {
        // Given - a streaming model that returns a simple response
        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        doAnswer(invocation -> {
                    ChatRequest request = invocation.getArgument(0);
                    StreamingChatResponseHandler handler = invocation.getArgument(1);

                    // Simulate streaming response
                    handler.onPartialResponse("Hello ");
                    handler.onPartialResponse("World!");

                    ChatResponse response = ChatResponse.builder()
                            .aiMessage(AiMessage.from("Hello World!"))
                            .metadata(ChatResponseMetadata.builder()
                                    .tokenUsage(new TokenUsage(10, 5))
                                    .build())
                            .build();
                    handler.onCompleteResponse(response);
                    return null;
                })
                .when(streamingModel)
                .chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        // Given - a moderation model that does NOT flag content
        ModerationModel moderationModel = mock(ModerationModel.class);
        when(moderationModel.moderate(anyList())).thenReturn(Response.from(Moderation.notFlagged()));

        ModeratedStreamingChat chat = AiServices.builder(ModeratedStreamingChat.class)
                .streamingChatModel(streamingModel)
                .moderationModel(moderationModel)
                .build();

        // When
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<ChatResponse> responseRef = new AtomicReference<>();
        CompletableFuture<Void> future = new CompletableFuture<>();

        chat.chat("Hello!")
                .onPartialResponse(partial -> {})
                .onCompleteResponse(response -> {
                    responseRef.set(response);
                    future.complete(null);
                })
                .onError(error -> {
                    errorRef.set(error);
                    future.complete(null);
                })
                .start();

        future.get(10, TimeUnit.SECONDS);

        // Then - should have completed without error
        assertThat(errorRef.get()).isNull();
        assertThat(responseRef.get()).isNotNull();
        assertThat(responseRef.get().aiMessage().text()).isEqualTo("Hello World!");

        // Verify moderation model was called
        verify(moderationModel).moderate(anyList());
    }

    @Test
    void should_not_call_moderation_when_Moderate_annotation_is_absent_in_streaming() throws Exception {
        // Given - a streaming model that returns a simple response
        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        doAnswer(invocation -> {
                    ChatRequest request = invocation.getArgument(0);
                    StreamingChatResponseHandler handler = invocation.getArgument(1);

                    // Simulate streaming response
                    handler.onPartialResponse("Hello ");
                    handler.onPartialResponse("World!");

                    ChatResponse response = ChatResponse.builder()
                            .aiMessage(AiMessage.from("Hello World!"))
                            .metadata(ChatResponseMetadata.builder()
                                    .tokenUsage(new TokenUsage(10, 5))
                                    .build())
                            .build();
                    handler.onCompleteResponse(response);
                    return null;
                })
                .when(streamingModel)
                .chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        // Given - a moderation model (should not be called)
        ModerationModel moderationModel = mock(ModerationModel.class);

        NonModeratedStreamingChat chat = AiServices.builder(NonModeratedStreamingChat.class)
                .streamingChatModel(streamingModel)
                .moderationModel(moderationModel)
                .build();

        // When
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicReference<ChatResponse> responseRef = new AtomicReference<>();
        CompletableFuture<Void> future = new CompletableFuture<>();

        chat.chat("Hello!")
                .onPartialResponse(partial -> {})
                .onCompleteResponse(response -> {
                    responseRef.set(response);
                    future.complete(null);
                })
                .onError(error -> {
                    errorRef.set(error);
                    future.complete(null);
                })
                .start();

        future.get(10, TimeUnit.SECONDS);

        // Then - should have completed without error
        assertThat(errorRef.get()).isNull();
        assertThat(responseRef.get()).isNotNull();

        // Verify moderation model was NOT called (no @Moderate annotation)
        verify(moderationModel, never()).moderate(anyList());
    }

    @Test
    void should_preserve_moderation_in_exception_when_flagged_in_streaming() throws Exception {
        // Given
        String flaggedText = "this is flagged content";
        Moderation expectedModeration = Moderation.flagged(flaggedText);

        StreamingChatModel streamingModel = mock(StreamingChatModel.class);
        doAnswer(invocation -> {
                    StreamingChatResponseHandler handler = invocation.getArgument(1);
                    ChatResponse response = ChatResponse.builder()
                            .aiMessage(AiMessage.from("Response"))
                            .metadata(ChatResponseMetadata.builder()
                                    .tokenUsage(new TokenUsage(5, 5))
                                    .build())
                            .build();
                    handler.onCompleteResponse(response);
                    return null;
                })
                .when(streamingModel)
                .chat(any(ChatRequest.class), any(StreamingChatResponseHandler.class));

        ModerationModel moderationModel = mock(ModerationModel.class);
        when(moderationModel.moderate(anyList())).thenReturn(Response.from(expectedModeration));

        ModeratedStreamingChat chat = AiServices.builder(ModeratedStreamingChat.class)
                .streamingChatModel(streamingModel)
                .moderationModel(moderationModel)
                .build();

        // When
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CompletableFuture<Void> future = new CompletableFuture<>();

        chat.chat("test")
                .onPartialResponse(partial -> {})
                .onCompleteResponse(response -> future.complete(null))
                .onError(error -> {
                    errorRef.set(error);
                    future.complete(null);
                })
                .start();

        future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(errorRef.get()).isInstanceOf(ModerationException.class);
        ModerationException exception = (ModerationException) errorRef.get();
        assertThat(exception.moderation()).isSameAs(expectedModeration);
        assertThat(exception.moderation().flaggedText()).isEqualTo(flaggedText);
    }
}
