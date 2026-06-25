package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExposureTrackingStreamingChatResponseHandlerTest {

    @Test
    void typed_callbacks_mark_exposure_and_are_delegated() {
        RecordingHandler delegate = new RecordingHandler();
        ExposureTrackingStreamingChatResponseHandler handler = new ExposureTrackingStreamingChatResponseHandler(delegate);

        handler.resetExposureTracking();
        assertThat(handler.wasExposed()).isFalse();

        handler.onPartialResponse("token");

        assertThat(handler.wasExposed()).isTrue();
        assertThat(delegate.partialResponses).containsExactly("token");
    }

    @Test
    void raw_events_do_not_mark_exposure_but_are_delegated() {
        RecordingHandler delegate = new RecordingHandler();
        ExposureTrackingStreamingChatResponseHandler handler = new ExposureTrackingStreamingChatResponseHandler(delegate);

        handler.resetExposureTracking();
        Object rawEvent = "raw";

        handler.onRawEvent(rawEvent);

        assertThat(handler.wasExposed()).isFalse();
        assertThat(delegate.rawEvents).containsExactly(rawEvent);
    }

    @Test
    void reset_clears_exposure() {
        RecordingHandler delegate = new RecordingHandler();
        ExposureTrackingStreamingChatResponseHandler handler = new ExposureTrackingStreamingChatResponseHandler(delegate);

        handler.onPartialResponse("token");
        assertThat(handler.wasExposed()).isTrue();

        handler.resetExposureTracking();
        assertThat(handler.wasExposed()).isFalse();
    }

    @Test
    void terminal_callbacks_mark_exposure() {
        RecordingHandler delegate = new RecordingHandler();
        ExposureTrackingStreamingChatResponseHandler handler = new ExposureTrackingStreamingChatResponseHandler(delegate);

        handler.resetExposureTracking();
        handler.onError(new RuntimeException("boom"));
        assertThat(handler.wasExposed()).isTrue();

        handler.resetExposureTracking();
        handler.onCompleteResponse(ChatResponse.builder().aiMessage(dev.langchain4j.data.message.AiMessage.from("done")).build());
        assertThat(handler.wasExposed()).isTrue();
    }

    private static class RecordingHandler implements StreamingChatResponseHandler {

        final List<String> partialResponses = new ArrayList<>();
        final List<Object> rawEvents = new ArrayList<>();

        @Override
        public void onPartialResponse(String partialResponse) {
            partialResponses.add(partialResponse);
        }

        @Override
        public void onRawEvent(Object rawEvent) {
            rawEvents.add(rawEvent);
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {}

        @Override
        public void onError(Throwable error) {}
    }
}
