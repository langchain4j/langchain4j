package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.AbstractBedrockChatModel.THINKING_SIGNATURE_KEY;
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
    private static final String SIGNATURE = "EoAECpABCBEYAipARsBWFsXRge7q";

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

    @Test
    void should_keep_the_signature_of_a_reasoning_block_that_has_no_text() {
        // given a stream whose reasoning block carries only a signature and no text: this is what Claude models with
        // adaptive thinking return by default
        StreamingChatModel model = BedrockStreamingChatModel.builder()
                .client(mockStreamingClient(signatureOnlyReasoningAndAnswerEvents()))
                .modelId("test-model")
                .returnThinking(true)
                .build();

        TestStreamingChatResponseHandler handler = spy(new TestStreamingChatResponseHandler());
        model.chat("What is the capital of Germany?", handler);

        // then the signature is stored, so that it can be sent back in a follow-up request
        AiMessage aiMessage = handler.get().aiMessage();
        assertThat(aiMessage.text()).isEqualTo(ANSWER);
        assertThat(aiMessage.thinking()).isNull();
        assertThat(aiMessage.attribute(THINKING_SIGNATURE_KEY, String.class)).isEqualTo(SIGNATURE);
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

    private static List<ConverseStreamOutput> signatureOnlyReasoningAndAnswerEvents() {
        return List.of(
                MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build(),
                ContentBlockDeltaEvent.builder()
                        .delta(ContentBlockDelta.fromReasoningContent(ReasoningContentBlockDelta.builder()
                                .signature(SIGNATURE)
                                .build()))
                        .build(),
                ContentBlockStopEvent.builder().build(),
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
