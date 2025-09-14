package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.anthropic.internal.client.AnthropicCreateMessageOptions;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@Disabled
class AnthropicUserIdIT {

    private static final String TEST_USER_ID = "test-user-123";

    @Test
    void should_include_userId_in_chat_model_request() {
        // given
        AnthropicClient mockClient = mock(AnthropicClient.class);

        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .userId(TEST_USER_ID)
                .maxTokens(10)
                .build();

        // Use reflection to replace the client with our mock
        try {
            java.lang.reflect.Field clientField = AnthropicChatModel.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(model, mockClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        UserMessage userMessage = UserMessage.from("Hello");

        // when
        model.chat(userMessage);

        // then
        ArgumentCaptor<AnthropicCreateMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(AnthropicCreateMessageRequest.class);
        verify(mockClient).createMessage(requestCaptor.capture());

        AnthropicCreateMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNotNull();
        assertThat(capturedRequest.getMetadata().getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    void should_include_userId_in_streaming_chat_model_request() {
        // given
        AnthropicClient mockClient = mock(AnthropicClient.class);

        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .userId(TEST_USER_ID)
                .maxTokens(10)
                .build();

        // Use reflection to replace the client with our mock
        try {
            java.lang.reflect.Field clientField = AnthropicStreamingChatModel.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(model, mockClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        UserMessage userMessage = UserMessage.from("Hello");
        StreamingChatResponseHandler handler = mock(StreamingChatResponseHandler.class);

        // when
        model.chat(List.of(userMessage), handler);

        // then
        ArgumentCaptor<AnthropicCreateMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(AnthropicCreateMessageRequest.class);
        verify(mockClient)
                .createMessage(requestCaptor.capture(), any(AnthropicCreateMessageOptions.class), eq(handler));

        AnthropicCreateMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNotNull();
        assertThat(capturedRequest.getMetadata().getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    void should_not_include_metadata_when_userId_is_null() {
        // given
        AnthropicClient mockClient = mock(AnthropicClient.class);

        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .maxTokens(10)
                .build();

        // Use reflection to replace the client with our mock
        try {
            java.lang.reflect.Field clientField = AnthropicChatModel.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(model, mockClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        UserMessage userMessage = UserMessage.from("Hello");

        // when
        model.chat(userMessage);

        // then
        ArgumentCaptor<AnthropicCreateMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(AnthropicCreateMessageRequest.class);
        verify(mockClient).createMessage(requestCaptor.capture());

        AnthropicCreateMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNull();
    }

    @Test
    void should_not_include_metadata_when_userId_is_empty() {
        // given
        AnthropicClient mockClient = mock(AnthropicClient.class);

        ChatModel model = AnthropicChatModel.builder()
                .apiKey(System.getenv("ANTHROPIC_API_KEY"))
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .userId("")
                .maxTokens(10)
                .build();

        // Use reflection to replace the client with our mock
        try {
            java.lang.reflect.Field clientField = AnthropicChatModel.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(model, mockClient);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        UserMessage userMessage = UserMessage.from("Hello");

        // when
        model.chat(userMessage);

        // then
        ArgumentCaptor<AnthropicCreateMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(AnthropicCreateMessageRequest.class);
        verify(mockClient).createMessage(requestCaptor.capture());

        AnthropicCreateMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNull();
    }
}
