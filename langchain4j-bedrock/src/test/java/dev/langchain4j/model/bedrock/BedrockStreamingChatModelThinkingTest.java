package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.TestStreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;

class BedrockStreamingChatModelThinkingTest {

    private static final String THINKING = "The user is asking about Germany.";
    private static final String ANSWER = "Berlin";

    @Test
    void should_invoke_onPartialThinking_when_returnThinking_is_enabled() {

        StreamingChatModel model = streamingChatModel(true);

        TestStreamingChatResponseHandler handler = spy(new TestStreamingChatResponseHandler());
        model.chat("What is the capital of Germany?", handler);

        AiMessage aiMessage = handler.get().aiMessage();
        assertThat(aiMessage.text()).isEqualTo(ANSWER);
        assertThat(aiMessage.thinking()).isEqualTo(THINKING);
        assertThat(handler.getThinking()).isEqualTo(THINKING);
        verify(handler).onPartialThinking(any(), any());
    }

    @Test
    void should_NOT_invoke_onPartialThinking_when_returnThinking_is_disabled() {

        StreamingChatModel model = streamingChatModel(false);

        TestStreamingChatResponseHandler handler = spy(new TestStreamingChatResponseHandler());
        model.chat("What is the capital of Germany?", handler);

        AiMessage aiMessage = handler.get().aiMessage();
        assertThat(aiMessage.text()).isEqualTo(ANSWER);
        assertThat(aiMessage.thinking()).isNull();
        assertThat(handler.getThinking()).isEmpty();
        verify(handler, never()).onPartialThinking(any(), any());
    }

    @Test
    void should_NOT_invoke_onPartialThinking_when_returnThinking_is_not_set() {

        StreamingChatModel model = streamingChatModel(null);

        TestStreamingChatResponseHandler handler = spy(new TestStreamingChatResponseHandler());
        model.chat("What is the capital of Germany?", handler);

        AiMessage aiMessage = handler.get().aiMessage();
        assertThat(aiMessage.text()).isEqualTo(ANSWER);
        assertThat(aiMessage.thinking()).isNull();
        assertThat(handler.getThinking()).isEmpty();
        verify(handler, never()).onPartialThinking(any(), any());
    }

    private static StreamingChatModel streamingChatModel(Boolean returnThinking) {
        BedrockStreamingChatModel.Builder builder = BedrockStreamingChatModel.builder()
                .client(mockStreamingClient(thinkingAndAnswerEvents()))
                .modelId("test-model");

        if (returnThinking != null) {
            builder.returnThinking(returnThinking);
        }

        return builder.build();
    }

    private static BedrockRuntimeAsyncClient mockStreamingClient(List<ConverseStreamOutput> events) {
        BedrockRuntimeAsyncClient client = mock(BedrockRuntimeAsyncClient.class);
        when(client.converseStream(any(ConverseStreamRequest.class), any(ConverseStreamResponseHandler.class)))
                .thenAnswer(invocation -> {
                    ConverseStreamResponseHandler responseHandler = invocation.getArgument(1);
                    responseHandler.onEventStream(SdkPublisher.fromIterable(events));
                    return CompletableFuture.completedFuture(null);
                });
        return client;
    }

    private static List<ConverseStreamOutput> thinkingAndAnswerEvents() {
        return List.of(
                MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build(),
                ContentBlockDeltaEvent.builder()
                        .delta(ContentBlockDelta.fromReasoningContent(ReasoningContentBlockDelta.builder()
                                .text(THINKING)
                                .build()))
                        .build(),
                ContentBlockDeltaEvent.builder()
                        .delta(ContentBlockDelta.fromText(ANSWER))
                        .build(),
                ContentBlockStopEvent.builder().build(),
                MessageStopEvent.builder().stopReason(StopReason.END_TURN).build(),
                ConverseStreamMetadataEvent.builder()
                        .usage(TokenUsage.builder()
                                .inputTokens(10)
                                .outputTokens(20)
                                .totalTokens(30)
                                .build())
                        .metrics(ConverseStreamMetrics.builder().latencyMs(1L).build())
                        .build());
    }
}
