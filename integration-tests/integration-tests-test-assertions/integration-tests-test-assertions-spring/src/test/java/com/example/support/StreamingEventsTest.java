package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.assistant.StreamingSupportAssistant;
import com.example.support.web.ReactiveSupportService;
import dev.langchain4j.service.AiServiceStreamingEvent.FinalResponseEvent;
import dev.langchain4j.service.AiServiceStreamingEvent.PartialResponseEvent;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import reactor.core.publisher.Flux;

/**
 * Same configuration as {@link StreamingCaptureTest}, so the Spring context is reused from the cache.
 */
@SpringBootTest
@Import(TestModels.class)
class StreamingEventsTest {

    @Autowired
    StreamingSupportAssistant streamingSupportAssistant;

    @Autowired
    ReactiveSupportService reactiveSupportService;

    @Autowired
    TaskExecutor taskExecutor;

    @Test
    @Tag("reactor")
    void records_the_text_of_a_flux_of_strings_returned_by_the_bean() {
        AiServiceCapture capture = capture(() -> streamingSupportAssistant.chatFlux("Hi"));

        assertThat(capture.output()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(capture.lambdaOutput()).isInstanceOf(Flux.class);
        assertThat(capture).hasSucceeded().hasOwnChatModelCallCount(1).hasToolExecutedTimes("getBookingDetails", 0);
    }

    @Test
    @Tag("reactor")
    void records_the_items_of_a_flux_of_strings_returned_by_the_bean() {
        AiServiceCapture capture = capture(() -> streamingSupportAssistant.chatFlux("Hi"));

        List<Object> items = capture.lambdaOutputItems();
        assertThat(items).isNotEmpty().allMatch(String.class::isInstance);
        assertThat(items.stream().map(String.class::cast).reduce("", String::concat))
                .isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    @Tag("reactor")
    void records_the_items_of_a_flux_of_streaming_events_returned_by_the_bean() {
        AiServiceCapture capture = capture(() -> streamingSupportAssistant.chatEvents("Hi"));

        List<Object> items = capture.lambdaOutputItems();
        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(items).hasAtLeastOneElementOfType(PartialResponseEvent.class);
        assertThat(items.get(items.size() - 1))
                .isInstanceOfSatisfying(FinalResponseEvent.class, event -> assertThat(
                                event.chatResponse().aiMessage().text())
                        .isEqualTo(TestModels.STREAMED_ANSWER));
        assertThat(capture).hasSucceeded().hasOwnChatModelCallCount(1);
    }

    @Test
    void records_the_items_of_a_flow_publisher_that_a_service_turns_into_a_flux() {
        AiServiceCapture capture = capture(() -> reactiveSupportService.streamAnswer("Hi"));

        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(capture.lambdaOutputItems()).isNotEmpty();
    }

    @Test
    void records_a_token_stream_started_in_the_lambda() {
        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceCapture capture = capture(() -> streamingSupportAssistant
                .chat("Hi")
                .onPartialResponse(tokens::add)
                .ignoreErrors()
                .start());

        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(String.join("", tokens)).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    @Tag("reactor")
    void records_a_flux_the_code_subscribed_to_itself() {
        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceCapture capture = capture(() -> streamingSupportAssistant.chatFlux("Hi").doOnNext(tokens::add).blockLast());

        assertThat(String.join("", tokens)).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void records_a_token_stream_started_by_the_code_on_another_thread() {
        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceCapture capture = capture(() -> {
            TokenStream tokenStream = streamingSupportAssistant.chat("Hi");
            taskExecutor.execute(() -> tokenStream
                    .onPartialResponse(tokens::add)
                    .onError(error -> {})
                    .start());
        });

        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(String.join("", tokens)).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(capture).hasSucceeded();
    }

    @Test
    void fails_for_a_returned_token_stream_that_was_not_started() {
        assertThatThrownBy(() -> capture(() -> streamingSupportAssistant.chat("Hi").onPartialResponse(token -> {})))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was not started")
                .hasMessageContaining("call start() in the lambda");
    }
}
