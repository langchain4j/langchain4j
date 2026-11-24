package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.LangChain4jAssertions.assertThatThrownBy;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import com.example.support.assistant.StreamingSupportAssistant;
import com.example.support.assistant.SupportAssistant;
import com.example.support.booking.BookingSummary;
import dev.langchain4j.service.AiServiceStreamingEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * What a capture holds for the Reactor return types of an AI Service method (with langchain4j-reactor).
 */
@Tag("reactor") // needs langchain4j-reactor: see the test-assertions-frameworks profile in pom.xml
@SpringBootTest
@Import(TestModels.class)
class ReactorReturnTypesTest {

    @Autowired
    SupportAssistant supportAssistant;

    @Autowired
    StreamingSupportAssistant streamingSupportAssistant;

    @Test
    void flux_of_strings() {
        AiServiceCapture capture = capture(() -> streamingSupportAssistant.chatFlux("Hi"));

        assertThat(capture.output()).isEqualTo(TestModels.STREAMED_ANSWER); // the whole streamed text
        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(capture.lambdaOutputItems()).isNotEmpty().allSatisfy(item -> assertThat(item).isInstanceOf(String.class));
        // already consumed: subscribing again calls the model again
        assertThat(capture.lambdaOutput()).isInstanceOf(Flux.class);
    }

    @Test
    void flux_mapped_by_your_code() {
        AiServiceCapture capture = capture(() -> streamingSupportAssistant
                .chatFlux("Hi")
                .map(token -> ServerSentEvent.builder(token).build()));

        List<Object> sentToBrowser = capture.lambdaOutputItems(); // what a client of the controller would receive
        assertThat(sentToBrowser).isNotEmpty().allSatisfy(item -> assertThat(item).isInstanceOf(ServerSentEvent.class));
        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void flux_of_streaming_events() {
        AiServiceCapture capture =
                capture(() -> streamingSupportAssistant.chatEvents("Hi"));

        assertThat(capture.output()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(capture.lambdaOutputItems()).allSatisfy(item -> assertThat(item).isInstanceOf(AiServiceStreamingEvent.class));
        assertThat(capture.lambdaOutputItems()).last().isInstanceOf(FinalResponseEvent.class);
    }

    @Test
    void flux_collected_by_the_code() {
        AiServiceCapture capture =
                capture(() -> streamingSupportAssistant.chatFlux("Hi").collectList().block());

        List<?> items = capture.lambdaOutput(List.class); // the items your code collected
        assertThat(items).isNotEmpty();
        assertThat(capture.output()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void mono_of_string() {
        AiServiceCapture capture = capture(() -> supportAssistant.chatLater("Hi"));

        assertThat(capture.output()).isEqualTo(SupportScript.GREETING); // the value of the Mono
        assertThat(capture.outputText()).isEqualTo(SupportScript.GREETING);
        assertThat(capture.lambdaOutput()).isInstanceOf(Mono.class); // already consumed
        assertThatThrownBy(capture::lambdaOutputItems).hasMessageContaining("not a stream"); // a Mono holds one value
    }

    @Test
    void mono_blocked_in_the_lambda() {
        AiServiceCapture capture =
                capture(() -> supportAssistant.summarizeBookingLater("Please summarize MS-312").block());

        BookingSummary summary = capture.lambdaOutput(BookingSummary.class);
        assertThat(summary).isEqualTo(capture.output(BookingSummary.class)); // the same object
        assertThat(capture).hasToolExecutedTimes("getBookingDetails", 1);
    }

    @Test
    void mono_of_structured_output() {
        AiServiceCapture capture =
                capture(() -> supportAssistant.summarizeBookingLater("Please summarize MS-312"));

        BookingSummary summary = capture.output(BookingSummary.class); // no need to block the Mono
        assertThat(summary).isEqualTo(new BookingSummary("MS-312", "compact car"));
        assertThatThrownBy(capture::outputText).hasMessageContaining("not text: use output() instead");
        assertThat(capture).hasToolExecutedTimes("getBookingDetails", 1).hasOwnChatModelCallCount(2);
    }
}
