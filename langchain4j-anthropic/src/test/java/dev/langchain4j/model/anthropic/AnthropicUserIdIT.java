package dev.langchain4j.model.anthropic;

import static dev.langchain4j.model.anthropic.AnthropicChatModelName.CLAUDE_3_5_HAIKU_20241022;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicContent;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageRequest;
import dev.langchain4j.model.anthropic.internal.api.AnthropicCreateMessageResponse;
import dev.langchain4j.model.anthropic.internal.api.AnthropicUsage;
import dev.langchain4j.model.anthropic.internal.client.AnthropicClient;
import dev.langchain4j.model.anthropic.internal.client.AnthropicCreateMessageOptions;
import dev.langchain4j.model.anthropic.internal.client.ParsedAndRawResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AnthropicUserIdIT {

    private static final String TEST_USER_ID = "test-user-123";

    @Test
    void should_include_userId_in_chat_model_request() {
        // given
        AnthropicClient mockClient = createMockClientWithResponse();
        ChatModel model = createChatModelWithMock(mockClient, TEST_USER_ID);

        // when
        model.chat(UserMessage.from("Hello"));

        // then
        ArgumentCaptor<AnthropicCreateMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(AnthropicCreateMessageRequest.class);
        verify(mockClient).createMessageWithRawResponse(requestCaptor.capture());

        AnthropicCreateMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNotNull();
        assertThat(capturedRequest.getMetadata().getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    void should_include_userId_in_streaming_chat_model_request() {
        // given
        AnthropicClient mockClient = mock(AnthropicClient.class);
        StreamingChatModel model = createStreamingChatModelWithMock(mockClient, TEST_USER_ID);

        // when
        model.chat(List.of(UserMessage.from("Hello")), mock(StreamingChatResponseHandler.class));

        // then
        ArgumentCaptor<AnthropicCreateMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(AnthropicCreateMessageRequest.class);
        verify(mockClient)
                .createMessage(
                        requestCaptor.capture(),
                        any(AnthropicCreateMessageOptions.class),
                        any(StreamingChatResponseHandler.class));

        AnthropicCreateMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNotNull();
        assertThat(capturedRequest.getMetadata().getUserId()).isEqualTo(TEST_USER_ID);
    }

    @Test
    void should_not_include_metadata_when_userId_is_null() {
        // given
        AnthropicClient mockClient = createMockClientWithResponse();
        ChatModel model = createChatModelWithMock(mockClient, null);

        // when
        model.chat(UserMessage.from("Hello"));

        // then
        ArgumentCaptor<AnthropicCreateMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(AnthropicCreateMessageRequest.class);
        verify(mockClient).createMessageWithRawResponse(requestCaptor.capture());

        AnthropicCreateMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNull();
    }

    @Test
    void should_not_include_metadata_when_userId_is_empty() {
        // given
        AnthropicClient mockClient = createMockClientWithResponse();
        ChatModel model = createChatModelWithMock(mockClient, "");

        // when
        model.chat(UserMessage.from("Hello"));

        // then
        ArgumentCaptor<AnthropicCreateMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(AnthropicCreateMessageRequest.class);
        verify(mockClient).createMessageWithRawResponse(requestCaptor.capture());

        AnthropicCreateMessageRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.getMetadata()).isNull();
    }

    // Helper methods to reduce duplication
    private static AnthropicClient createMockClientWithResponse() {
        AnthropicClient mockClient = mock(AnthropicClient.class);
        AnthropicCreateMessageResponse mockResponse = AnthropicCreateMessageResponse.builder()
                .id("test-id")
                .type("message")
                .role("assistant")
                .content(Collections.singletonList(createTextContent("Hello response")))
                .model(CLAUDE_3_5_HAIKU_20241022.toString())
                .stopReason("end_turn")
                .stopSequence(null)
                .usage(createUsage())
                .build();

        SuccessfulHttpResponse rawResponse =
                SuccessfulHttpResponse.builder().statusCode(200).build();
        ParsedAndRawResponse parsedAndRawResponse = new ParsedAndRawResponse(mockResponse, rawResponse);

        when(mockClient.createMessageWithRawResponse(any(AnthropicCreateMessageRequest.class)))
                .thenReturn(parsedAndRawResponse);
        return mockClient;
    }

    private static ChatModel createChatModelWithMock(AnthropicClient mockClient, String userId) {
        ChatModel model = AnthropicChatModel.builder()
                .apiKey("dummy-api-key")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .userId(userId)
                .maxTokens(10)
                .build();

        injectMockClient(model, mockClient);
        return model;
    }

    private static StreamingChatModel createStreamingChatModelWithMock(AnthropicClient mockClient, String userId) {
        StreamingChatModel model = AnthropicStreamingChatModel.builder()
                .apiKey("dummy-api-key")
                .modelName(CLAUDE_3_5_HAIKU_20241022)
                .userId(userId)
                .maxTokens(10)
                .build();

        injectMockClient(model, mockClient);
        return model;
    }

    private static void injectMockClient(Object model, AnthropicClient mockClient) {
        try {
            java.lang.reflect.Field clientField = model.getClass().getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(model, mockClient);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject mock client", e);
        }
    }

    private static AnthropicContent createTextContent(String text) {
        return AnthropicContent.builder().type("text").text(text).build();
    }

    private static AnthropicUsage createUsage() {
        AnthropicUsage usage = new AnthropicUsage();
        usage.inputTokens = 10;
        usage.outputTokens = 5;
        usage.cacheCreationInputTokens = null;
        usage.cacheReadInputTokens = null;
        return usage;
    }
}
