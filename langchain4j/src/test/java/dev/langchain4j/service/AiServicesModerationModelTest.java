package dev.langchain4j.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.moderation.Moderation;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiServicesModerationModelTest {

    private static final String STREAMING_TEST_MESSAGE = "ai service with streaming language model moderation test";
    private static final String REGULAR_TEST_MESSAGE = "ai service with language model moderation test";

    interface StreamModeratedChat {
        @Moderate
        TokenStream chat(String message);
    }

    interface ModeratedChat {
        @Moderate
        String chat(String message);
    }


    @Test
    @DisplayName("Should flag content when using streaming chat model")
    void shouldFlagContentWhenUsingStreamingChatModel() {
        StreamModeratedChat moderatedChat = AiServices.builder(StreamModeratedChat.class)
                .streamingChatLanguageModel(new NoOpStreamingChatLanguageModel())
                .moderationModel(new FlaggingAllModerationModel())
                .build();

        ModerationException exception = assertThrows(
                ModerationException.class,
                () -> moderatedChat.chat(STREAMING_TEST_MESSAGE)
        );

        assertThat(exception.getMessage()).contains(STREAMING_TEST_MESSAGE);
    }

    @Test
    @DisplayName("Should flag content when using regular chat model")
    void shouldFlagContentWhenUsingRegularChatModel() {
        ModeratedChat moderatedChat = AiServices.builder(ModeratedChat.class)
                .chatLanguageModel(new NoOpChatLanguageModel())
                .moderationModel(new FlaggingAllModerationModel())
                .build();

        ModerationException exception = assertThrows(
                ModerationException.class,
                () -> moderatedChat.chat(REGULAR_TEST_MESSAGE)
        );

        assertThat(exception.getMessage()).contains(REGULAR_TEST_MESSAGE);
    }

    /**
     * Moderation model that always flags content
     */
    private static class FlaggingAllModerationModel implements ModerationModel {
        @Override
        public Response<Moderation> moderate(String text) {
            return Response.from(Moderation.flagged(text));
        }

        @Override
        public Response<Moderation> moderate(List<ChatMessage> messages) {
            return Response.from(Moderation.flagged(((UserMessage) messages.get(0)).singleText()));
        }
    }

    /**
     * Minimal implementation of ChatLanguageModel for testing
     */
    private static class NoOpChatLanguageModel implements ChatLanguageModel {
        @Override
        public ChatResponse doChat(final ChatRequest chatRequest) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(""))
                    .build();
        }
    }

    /**
     * Minimal implementation of StreamingChatLanguageModel for testing
     */
    private static class NoOpStreamingChatLanguageModel implements StreamingChatLanguageModel {
        // Empty implementation is sufficient for the test
    }
}
