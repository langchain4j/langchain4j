package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.mock.StreamingChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Validates that {@link AiServices#forceToolChoiceAutoAfterFirstIteration(boolean)} also
 * applies in the streaming response handler — on follow-up streamed inference requests, a
 * caller-supplied {@link ToolChoice#REQUIRED} must be rewritten to {@link ToolChoice#AUTO}
 * so the LLM can terminate the loop.
 */
class StreamingToolChoiceAutoProtectionTest {

    interface StreamingAssistant {
        TokenStream chat(String message);
    }

    static class StickyTool {
        final AtomicInteger calls = new AtomicInteger();

        @Tool
        public String stick(String arg) {
            calls.incrementAndGet();
            return "ok";
        }
    }

    /**
     * Returns a tool call when ToolChoice.REQUIRED, a final text message when AUTO/null.
     */
    private static StreamingChatModelMock toolChoiceAwareModel() {
        // This mock returns a single response tied to the first chat call. To distinguish
        // iterations 0 vs 1, we pre-queue both responses: iter 0 returns a tool call, iter 1
        // returns a final text. We rely on the test's transformer to produce REQUIRED only on
        // iter 0 and on the loop to rewrite REQUIRED -> AUTO between iterations when the flag
        // is on. The mock itself doesn't inspect the request — but we still observe that the
        // loop terminates after iteration 1 (i.e. the model produces a final text), proving
        // that the loop did not continue calling tools forever.
        AiMessage iter0 = AiMessage.from(ToolExecutionRequest.builder()
                .id("c1")
                .name("stick")
                .arguments("{\"arg0\":\"x\"}")
                .build());
        AiMessage iter1 = AiMessage.from("done");
        return StreamingChatModelMock.thatAlwaysStreams(iter0, iter1);
    }

    @Test
    void with_flag_required_tool_choice_is_rewritten_to_auto_after_first_iteration() throws Exception {
        // The first chat request transformer sets REQUIRED only on iteration 0. With the flag
        // on, the loop's rewrite then carries AUTO forward and the loop terminates.
        StickyTool tool = new StickyTool();
        StreamingChatModelMock model = toolChoiceAwareModel();

        AtomicInteger transformerCalls = new AtomicInteger();
        AtomicInteger followUpToolChoiceObserved = new AtomicInteger();
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
                .tools(tool)
                .maxSequentialToolsInvocations(5)
                .forceToolChoiceAutoAfterFirstIteration(true)
                .chatRequestTransformer((req, memId) -> {
                    int n = transformerCalls.getAndIncrement();
                    if (n == 0) {
                        return ChatRequest.builder()
                                .messages(req.messages())
                                .parameters(req.parameters()
                                        .overrideWith(ChatRequestParameters.builder()
                                                .toolChoice(ToolChoice.REQUIRED)
                                                .build()))
                                .build();
                    }
                    // Capture the toolChoice on the follow-up request — proves the rewrite happened.
                    if (req.parameters().toolChoice() == ToolChoice.AUTO) {
                        followUpToolChoiceObserved.set(1);
                    } else if (req.parameters().toolChoice() == ToolChoice.REQUIRED) {
                        followUpToolChoiceObserved.set(2);
                    }
                    return req;
                })
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("done");
        assertThat(tool.calls.get()).isEqualTo(1);
        assertThat(followUpToolChoiceObserved.get())
                .as("follow-up streaming request must carry ToolChoice.AUTO after first iteration")
                .isEqualTo(1);
    }

    @Test
    void without_flag_streaming_follow_up_does_not_inject_tool_choice_auto() throws Exception {
        // Without the flag, the streaming handler does NOT inject ToolChoice.AUTO on follow-up.
        // (The historical behavior is that toolChoice is not carried forward by the streaming
        // handler at all, so the follow-up request's toolChoice is simply unset/null.)
        StickyTool tool = new StickyTool();
        StreamingChatModelMock model = toolChoiceAwareModel();

        AtomicInteger transformerCalls = new AtomicInteger();
        AtomicInteger followUpAutoObserved = new AtomicInteger();
        StreamingAssistant assistant = AiServices.builder(StreamingAssistant.class)
                .streamingChatModel(model)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(50))
                .tools(tool)
                .maxSequentialToolsInvocations(5)
                // forceToolChoiceAutoAfterFirstIteration NOT set — defaults to false.
                .chatRequestTransformer((req, memId) -> {
                    int n = transformerCalls.getAndIncrement();
                    if (n == 0) {
                        return ChatRequest.builder()
                                .messages(req.messages())
                                .parameters(req.parameters()
                                        .overrideWith(ChatRequestParameters.builder()
                                                .toolChoice(ToolChoice.REQUIRED)
                                                .build()))
                                .build();
                    }
                    if (req.parameters().toolChoice() == ToolChoice.AUTO) {
                        followUpAutoObserved.incrementAndGet();
                    }
                    return req;
                })
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        assistant
                .chat("go")
                .onPartialResponse(ignored -> {})
                .onCompleteResponse(future::complete)
                .onError(future::completeExceptionally)
                .start();

        ChatResponse response = future.get(10, TimeUnit.SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("done");
        // Without the flag, the handler must NOT inject ToolChoice.AUTO into the follow-up
        // request (some downstream callers may want to set REQUIRED on follow-up via their
        // own transformer; the handler should not pre-empt them).
        assertThat(followUpAutoObserved.get())
                .as("without flag, the streaming handler must not inject ToolChoice.AUTO on follow-up")
                .isZero();
    }
}
