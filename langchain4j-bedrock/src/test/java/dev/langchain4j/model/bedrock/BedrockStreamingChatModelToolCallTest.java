package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetrics;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;

class BedrockStreamingChatModelToolCallTest {

    private static final String TOOL_ID = "tooluse_1";
    private static final String TOOL_NAME = "getWeather";
    private static final String ARGUMENTS = "{\"city\": \"Munich\", \"day\": \"today\"}";

    @Test
    void should_report_the_same_tool_call_while_streaming_and_in_the_complete_response() throws Exception {

        StreamingChatModel model = streamingChatModel(toolCallEvents());

        Handler handler = new Handler();
        model.chat("What is the weather in Munich?", handler);
        ChatResponse response = handler.await();

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolRequest.id()).isEqualTo(TOOL_ID);
        assertThat(toolRequest.name()).isEqualTo(TOOL_NAME);
        // the arguments are the model's own JSON, not a re-serialized copy of it
        assertThat(toolRequest.arguments()).isEqualTo(ARGUMENTS);

        assertThat(handler.completeToolCalls).hasSize(1);
        assertThat(handler.completeToolCalls.get(0).toolExecutionRequest()).isEqualTo(toolRequest);
    }

    @Test
    void should_report_a_tool_call_without_arguments_that_follows_text() throws Exception {

        StreamingChatModel model = streamingChatModel(textThenToolCallWithoutArgumentsEvents());

        Handler handler = new Handler();
        model.chat("What time is it?", handler);
        ChatResponse response = handler.await();

        AiMessage aiMessage = response.aiMessage();
        assertThat(aiMessage.text()).isEqualTo("Let me check.");
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);

        ToolExecutionRequest toolRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolRequest.name()).isEqualTo("currentTime");
        assertThat(toolRequest.arguments()).isEqualTo("{}");

        assertThat(handler.completeToolCalls).hasSize(1);
        assertThat(handler.completeToolCalls.get(0).toolExecutionRequest()).isEqualTo(toolRequest);
    }

    static class Handler implements StreamingChatResponseHandler {

        final List<CompleteToolCall> completeToolCalls = new ArrayList<>();
        private final CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        @Override
        public void onPartialResponse(String partialResponse) {}

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            completeToolCalls.add(completeToolCall);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            future.complete(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            future.completeExceptionally(error);
        }

        ChatResponse await() throws Exception {
            return future.get(10, TimeUnit.SECONDS);
        }
    }

    private static StreamingChatModel streamingChatModel(List<ConverseStreamOutput> events) {
        return BedrockStreamingChatModel.builder()
                .client(mockStreamingClient(events))
                .modelId("test-model")
                .build();
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

    private static List<ConverseStreamOutput> toolCallEvents() {
        return List.of(
                MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build(),
                toolUseStart(TOOL_ID, TOOL_NAME),
                toolUseDelta("{\"city\": \"Mun"),
                toolUseDelta("ich\", \"day\": \"today\"}"),
                ContentBlockStopEvent.builder().build(),
                MessageStopEvent.builder().stopReason(StopReason.TOOL_USE).build(),
                metadata());
    }

    private static List<ConverseStreamOutput> textThenToolCallWithoutArgumentsEvents() {
        return List.of(
                MessageStartEvent.builder().role(ConversationRole.ASSISTANT).build(),
                ContentBlockDeltaEvent.builder()
                        .delta(ContentBlockDelta.fromText("Let me check."))
                        .build(),
                ContentBlockStopEvent.builder().build(),
                toolUseStart("tooluse_2", "currentTime"),
                ContentBlockStopEvent.builder().build(),
                MessageStopEvent.builder().stopReason(StopReason.TOOL_USE).build(),
                metadata());
    }

    private static ContentBlockStartEvent toolUseStart(String toolUseId, String name) {
        return ContentBlockStartEvent.builder()
                .start(ContentBlockStart.fromToolUse(
                        ToolUseBlockStart.builder().toolUseId(toolUseId).name(name).build()))
                .build();
    }

    private static ContentBlockDeltaEvent toolUseDelta(String input) {
        return ContentBlockDeltaEvent.builder()
                .delta(ContentBlockDelta.fromToolUse(
                        ToolUseBlockDelta.builder().input(input).build()))
                .build();
    }

    private static ConverseStreamMetadataEvent metadata() {
        return ConverseStreamMetadataEvent.builder()
                .usage(TokenUsage.builder()
                        .inputTokens(10)
                        .outputTokens(20)
                        .totalTokens(30)
                        .build())
                .metrics(ConverseStreamMetrics.builder().latencyMs(100L).build())
                .build();
    }
}
