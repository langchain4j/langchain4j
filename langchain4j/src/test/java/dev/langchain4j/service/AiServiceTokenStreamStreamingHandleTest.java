package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AiServiceTokenStreamStreamingHandleTest {

    interface Assistant {

        TokenStream chat(String userMessage);
    }

    @Test
    void should_observe_streaming_handle_while_using_plain_partial_response_handler() {
        TestStreamingHandle streamingHandle = new TestStreamingHandle();

        Assistant assistant = AiServices.create(Assistant.class, new TestStreamingChatModel(streamingHandle));

        List<String> partialResponses = new ArrayList<>();
        AtomicReference<StreamingHandle> observedStreamingHandle = new AtomicReference<>();
        AtomicReference<ChatResponse> completeResponse = new AtomicReference<>();

        assistant
                .chat("Hello")
                .onStreamingHandle(observedStreamingHandle::set)
                .onPartialResponse(partialResponses::add)
                .onCompleteResponse(completeResponse::set)
                .onError(error -> fail(error.getMessage()))
                .start();

        assertThat(observedStreamingHandle).hasValue(streamingHandle);
        assertThat(partialResponses).containsExactly("hello", "!");
        assertThat(completeResponse.get())
                .isNotNull()
                .extracting(response -> response.aiMessage().text())
                .isEqualTo("hello!");
    }

    @Test
    void should_observe_streaming_handle_only_once_for_the_same_handle() {
        TestStreamingHandle streamingHandle = new TestStreamingHandle();

        Assistant assistant = AiServices.create(Assistant.class, new TestStreamingChatModel(streamingHandle));

        List<StreamingHandle> observedStreamingHandles = new ArrayList<>();
        List<String> partialResponses = new ArrayList<>();

        assistant
                .chat("Hello")
                .onStreamingHandle(observedStreamingHandles::add)
                .onPartialResponse(partialResponses::add)
                .onCompleteResponse(ignored -> {})
                .onError(error -> fail(error.getMessage()))
                .start();

        assertThat(observedStreamingHandles).containsExactly(streamingHandle);
        assertThat(partialResponses).containsExactly("hello", "!");
    }

    @Test
    void should_reuse_observed_streaming_handle_for_partial_response_without_context() {
        TestStreamingHandle streamingHandle = new TestStreamingHandle();

        Assistant assistant = AiServices.create(Assistant.class, new MixedContextStreamingChatModel(streamingHandle));

        List<StreamingHandle> contextStreamingHandles = new ArrayList<>();

        assistant
                .chat("Hello")
                .onPartialResponseWithContext(
                        (partialResponse, context) -> contextStreamingHandles.add(context.streamingHandle()))
                .onCompleteResponse(ignored -> {})
                .onError(error -> fail(error.getMessage()))
                .start();

        assertThat(contextStreamingHandles).containsExactly(streamingHandle, streamingHandle);
    }

    @Test
    void should_observe_streaming_handle_from_thinking_and_tool_call_contexts() {
        TestStreamingHandle streamingHandle = new TestStreamingHandle();

        Assistant assistant =
                AiServices.create(Assistant.class, new ThinkingAndToolCallStreamingChatModel(streamingHandle));

        List<StreamingHandle> observedStreamingHandles = new ArrayList<>();
        List<PartialThinking> partialThinkings = new ArrayList<>();
        List<PartialToolCall> partialToolCalls = new ArrayList<>();

        assistant
                .chat("Hello")
                .onStreamingHandle(observedStreamingHandles::add)
                .onPartialThinking(partialThinkings::add)
                .onPartialToolCall(partialToolCalls::add)
                .onCompleteResponse(ignored -> {})
                .onError(error -> fail(error.getMessage()))
                .start();

        assertThat(observedStreamingHandles).containsExactly(streamingHandle);
        assertThat(partialThinkings).extracting(PartialThinking::text).containsExactly("thinking");
        assertThat(partialToolCalls).extracting(PartialToolCall::name).containsExactly("search");
    }

    private record TestStreamingChatModel(StreamingHandle streamingHandle) implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse(new PartialResponse("hello"), new PartialResponseContext(streamingHandle));
            handler.onPartialResponse(new PartialResponse("!"), new PartialResponseContext(streamingHandle));
            handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("hello!")).build());
        }
    }

    private record ThinkingAndToolCallStreamingChatModel(StreamingHandle streamingHandle)
            implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialThinking(new PartialThinking("thinking"), new PartialThinkingContext(streamingHandle));
            handler.onPartialToolCall(
                    PartialToolCall.builder()
                            .index(0)
                            .id("tool-call-id")
                            .name("search")
                            .partialArguments("{")
                            .build(),
                    new PartialToolCallContext(streamingHandle));
            handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("done")).build());
        }
    }

    private record MixedContextStreamingChatModel(StreamingHandle streamingHandle) implements StreamingChatModel {

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handler.onPartialResponse(new PartialResponse("hello"), new PartialResponseContext(streamingHandle));
            handler.onPartialResponse("!");
            handler.onCompleteResponse(
                    ChatResponse.builder().aiMessage(AiMessage.from("hello!")).build());
        }
    }

    private static class TestStreamingHandle implements StreamingHandle {

        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
