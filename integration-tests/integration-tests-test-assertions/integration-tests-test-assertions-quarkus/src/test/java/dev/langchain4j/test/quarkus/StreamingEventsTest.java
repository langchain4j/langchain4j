package dev.langchain4j.test.quarkus;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.quarkus.Models.useStreamingChatModel;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static dev.langchain4j.test.service.AiServiceCapture.captureExpectingFailure;

import com.example.support.SupportService;
import dev.langchain4j.test.mock.MockStreamingChatModel;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.PartialResponseEvent;
import dev.langchain4j.test.service.AiServiceCapture;
import io.smallrye.mutiny.Multi;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

@QuarkusTest
class StreamingEventsTest {

    @Inject
    SupportService supportService;

    @Test
    void records_items_of_returned_multi_of_strings() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("Red ", "shoes"));

        AiServiceCapture capture = capture(() -> supportService.recommendProducts("Something red"));

        assertThat(capture.output()).isEqualTo("Red shoes"); // the whole streamed text
        assertThat(capture.outputText()).isEqualTo("Red shoes");
        assertThat(capture.lambdaOutput()).isInstanceOf(Multi.class); // already consumed
        assertThat(capture.lambdaOutputItems()).containsExactly("Red ", "shoes");
        assertThat(capture).hasSucceeded().hasOwnChatModelCallCount(1);
    }

    @Test
    void records_guarded_answer_of_returned_multi_as_a_single_item() {
        // the output guardrail must see the whole answer, so the Multi emits it as one item
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("a", "b", "c"));

        AiServiceCapture capture = capture(() -> supportService.streamAnswer("Hi"));

        assertThat(capture.outputText()).isEqualTo("abc");
        assertThat(capture.lambdaOutputItems()).containsExactly("abc");
    }

    @Test
    void records_items_of_returned_multi_of_streaming_events() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("Red ", "shoes"));

        AiServiceCapture capture =
                capture(() -> supportService.recommendProductsWithEvents("Something red"));

        assertThat(capture.output()).isEqualTo("Red shoes");
        assertThat(capture.lambdaOutputItems())
                .allSatisfy(item -> assertThat(item).isInstanceOf(AiServiceStreamingEvent.class));
        List<Object> events = capture.lambdaOutputItems();
        assertThat(events)
                .filteredOn(PartialResponseEvent.class::isInstance)
                .map(event -> ((PartialResponseEvent) event).partialResponse().text())
                .containsExactly("Red ", "shoes");
        assertThat(events).last().isInstanceOf(FinalResponseEvent.class);
        assertThat(events)
                .allSatisfy(event -> assertThat(((AiServiceStreamingEvent) event).invocationContext().invocationId())
                        .isEqualTo(capture.invocation().invocationContext().invocationId()));
    }

    @Test
    void records_invocation_when_application_subscribes_to_multi_of_streaming_events() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("Red ", "shoes"));
        List<AiServiceStreamingEvent> received = new CopyOnWriteArrayList<>();

        AiServiceCapture capture = capture(
                () -> supportService.recommendProductsWithEvents("Something red").subscribe().with(received::add));

        assertThat(received).isNotEmpty();
        assertThat(capture.outputText()).isEqualTo("Red shoes");
    }

    @Test
    void records_invocation_when_application_subscribes_to_multi() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("x", "y"));
        List<String> items = new CopyOnWriteArrayList<>();

        AiServiceCapture capture = capture(() -> supportService.recommendProducts("Hi").subscribe().with(items::add));

        assertThat(items).containsExactly("x", "y");
        assertThat(capture.outputText()).isEqualTo("xy");
    }

    @Test
    void records_invocation_when_returned_uni_collects_the_multi() {
        // capture(...) subscribes to the Uni, the items of the Multi go to the collector of the application
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("a", "b", "c"));

        AiServiceCapture capture = capture(() -> supportService.recommendProducts("Hi").collect().asList());

        assertThat(capture.outputText()).isEqualTo("abc");
    }

    @Test
    void has_succeeded_fails_for_failed_stream() {
        useStreamingChatModel(MockStreamingChatModel.thatFails(new RuntimeException("Stream broke")));

        AiServiceCapture capture = captureExpectingFailure(() -> supportService.recommendProducts("Hi"));

        assertThatThrownBy(() -> assertThat(capture).hasSucceeded())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the invocation to succeed, but it failed with")
                .hasMessageContaining("Stream broke");
        Multi<?> multi = capture.lambdaOutput(Multi.class);
        assertThat(multi).as("the code itself returned the Multi without throwing").isNotNull();
    }

    @Test
    void has_succeeded_fails_for_cancelled_stream() {
        useStreamingChatModel(MockStreamingChatModel.thatStreamsTokens("one ", "two ", "three"));

        AiServiceCapture capture = capture(() -> supportService.recommendProducts("Count").select().first());

        assertThatThrownBy(() -> assertThat(capture).hasSucceeded())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected the invocation to succeed, but it was cancelled");
    }
}
