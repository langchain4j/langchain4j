package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappingTrackingStreamingChatResponseHandlerTest {

    @Test
    void typed_callbacks_mark_exposure_and_are_delegated() {
        RecordingHandler delegate = new RecordingHandler();
        MappingTrackingStreamingChatResponseHandler handler = new MappingTrackingStreamingChatResponseHandler(delegate);

        handler.resetMappingTracking();
        assertThat(handler.wasMapped()).isFalse();

        handler.onPartialResponse("token");

        assertThat(handler.wasMapped()).isTrue();
        assertThat(delegate.partialResponses).containsExactly("token");
    }

    @Test
    void raw_events_do_not_mark_exposure_but_are_delegated() {
        RecordingHandler delegate = new RecordingHandler();
        MappingTrackingStreamingChatResponseHandler handler = new MappingTrackingStreamingChatResponseHandler(delegate);

        handler.resetMappingTracking();
        Object rawEvent = "raw";

        handler.onUnmappedRawEvent(rawEvent);

        assertThat(handler.wasMapped()).isFalse();
        assertThat(delegate.rawEvents).containsExactly(rawEvent);
    }

    @Test
    void reset_clears_exposure() {
        RecordingHandler delegate = new RecordingHandler();
        MappingTrackingStreamingChatResponseHandler handler = new MappingTrackingStreamingChatResponseHandler(delegate);

        handler.onPartialResponse("token");
        assertThat(handler.wasMapped()).isTrue();

        handler.resetMappingTracking();
        assertThat(handler.wasMapped()).isFalse();
    }

    @Test
    void terminal_callbacks_mark_exposure() {
        RecordingHandler delegate = new RecordingHandler();
        MappingTrackingStreamingChatResponseHandler handler = new MappingTrackingStreamingChatResponseHandler(delegate);

        handler.resetMappingTracking();
        handler.onError(new RuntimeException("boom"));
        assertThat(handler.wasMapped()).isTrue();

        handler.resetMappingTracking();
        handler.onCompleteResponse(ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("done")).build());
        assertThat(handler.wasMapped()).isTrue();
    }

    private static class RecordingHandler implements StreamingChatResponseHandler {

        final List<String> partialResponses = new ArrayList<>();
        final List<Object> rawEvents = new ArrayList<>();

        @Override
        public void onPartialResponse(String partialResponse) {
            partialResponses.add(partialResponse);
        }

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            rawEvents.add(rawEvent);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {}

        @Override
        public void onError(Throwable error) {}
    }
}
