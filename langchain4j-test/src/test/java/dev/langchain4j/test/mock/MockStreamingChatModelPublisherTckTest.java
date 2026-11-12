package dev.langchain4j.test.mock;

import static org.reactivestreams.FlowAdapters.toPublisher;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import java.util.Collections;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

/**
 * Checks the {@code Publisher} returned by {@link MockStreamingChatModel#doChat(ChatRequest)} against the Reactive
 * Streams TCK.
 */
public class MockStreamingChatModelPublisherTckTest extends PublisherVerification<ChatModelStreamingEvent> {

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("Hi")).build();

    // each subscription starts a new thread, so expected signals get some slack on a loaded CI runner
    private static final long DEFAULT_TIMEOUT_MILLIS = 5_000L;
    private static final long DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS = 500L;
    private static final long DEFAULT_POLL_TIMEOUT_MILLIS = 50L;
    private static final long PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS = 300L;
    private static final long MAX_ELEMENTS = 100L;

    public MockStreamingChatModelPublisherTckTest() {
        super(
                new TestEnvironment(
                        DEFAULT_TIMEOUT_MILLIS, DEFAULT_NO_SIGNALS_TIMEOUT_MILLIS, DEFAULT_POLL_TIMEOUT_MILLIS),
                PUBLISHER_REFERENCE_CLEANUP_TIMEOUT_MILLIS);
    }

    @Override
    public long maxElementsFromPublisher() {
        return MAX_ELEMENTS;
    }

    @Override
    public Publisher<ChatModelStreamingEvent> createPublisher(long elements) {
        // the tokens are followed by the complete response, so even 0 elements give 1 event
        int tokens = (int) Math.max(0, elements - 1);
        MockStreamingChatModel model = MockStreamingChatModel.thatStreams(Collections.nCopies(tokens, "token"))
                .thenRepeats();
        return toPublisher(model.doChat(REQUEST));
    }

    @Override
    public Publisher<ChatModelStreamingEvent> createFailedPublisher() {
        MockStreamingChatModel model =
                MockStreamingChatModel.thatFails(new RuntimeException("failure")).thenRepeats();
        return toPublisher(model.doChat(REQUEST));
    }
}
