package dev.langchain4j.model.chat.response;

import dev.langchain4j.exception.UnsupportedFeatureException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingChatResponseHandlerTest {

    @Test
    void onPartialResponse_with_context_delegates_to_string_version() {
        RecordingHandler handler = new RecordingHandler();

        PartialResponse partial = new PartialResponse("hello");
        PartialResponseContext context = new PartialResponseContext(new DummyStreamingHandle());

        handler.onPartialResponse(partial, context);

        assertThat(handler.partialResponseText).isEqualTo("hello");
    }

    @Test
    void onPartialThinking_with_context_delegates_to_single_arg_version() {
        RecordingHandler handler = new RecordingHandler();

        PartialThinking partialThinking = new PartialThinking("thinking...");
        PartialThinkingContext context = new PartialThinkingContext(new DummyStreamingHandle());

        handler.onPartialThinking(partialThinking, context);

        assertThat(handler.partialThinking).isSameAs(partialThinking);
    }

    @Test
    void onPartialToolCall_with_context_delegates_to_single_arg_version() {
        RecordingHandler handler = new RecordingHandler();

        PartialToolCall partialToolCall = PartialToolCall.builder()
                .index(0)
                .id("call-1")
                .name("tool")
                .partialArguments("{")
                .build();
        PartialToolCallContext context = new PartialToolCallContext(new DummyStreamingHandle());

        handler.onPartialToolCall(partialToolCall, context);

        assertThat(handler.partialToolCall).isSameAs(partialToolCall);
    }

    /**
     * Test double that records which default methods are invoked.
     */
    static class RecordingHandler implements StreamingChatResponseHandler {

        String partialResponseText;
        PartialThinking partialThinking;
        PartialToolCall partialToolCall;

        @Override
        public void onPartialResponse(String partialResponse) {
            this.partialResponseText = partialResponse;
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            this.partialThinking = partialThinking;
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            this.partialToolCall = partialToolCall;
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            // not relevant for these tests
        }

        @Override
        public void onError(Throwable error) {
            // not relevant for these tests
        }
    }

    static class DummyStreamingHandle implements StreamingHandle {

        @Override
        public void cancel() {
            throw new UnsupportedFeatureException("cancellation not supported");
        }

        @Override
        public boolean isCancelled() {
            throw new UnsupportedFeatureException("cancellation not supported");
        }
    }
}