package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.test.service.AiServiceInvocation;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Captures the invocations of a streaming {@code @RegisterAiService} that returns a {@code Multi} or a
 * {@code TokenStream}.
 */
@QuarkusTest
class StreamingCaptureTest {

    @Inject
    StreamingSupportAssistant streamingSupportAssistant;

    @Test
    void reads_a_returned_multi_to_the_end() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling reads_a_returned_multi_to_the_endOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::reads_a_returned_multi_to_the_endOnceCaptured);
    }

    private void reads_a_returned_multi_to_the_endOnceCaptured() {
        AiServiceInvocation invocation = capture(() -> streamingSupportAssistant.stream("Hi")).invocation();

        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(invocation).hasNoToolExecutions();
        // Quarkus extension does not notify the listeners of its streaming chat model (ISSUE-LINK-MODEL-LISTENERS, to be opened); flip this assert when it does
        assertThatThrownBy(() -> assertThat(invocation).hasFinishReason(FinishReason.STOP))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the chat model did not notify its listeners");
        // Quarkus extension does not notify the listeners of its streaming chat model (ISSUE-LINK-MODEL-LISTENERS, to be opened); flip this assert when it does
        assertThat(invocation.tokenUsage()).isNull();
        // Quarkus extension does not notify the listeners of its streaming chat model (ISSUE-LINK-MODEL-LISTENERS, to be opened); flip this assert when it does
        assertThatThrownBy(invocation::lastRequest)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("the chat model did not notify its listeners");
    }

    @Test
    void captures_a_multi_that_the_captured_code_reads_itself() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling captures_a_multi_that_the_captured_code_reads_itselfOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(this::captures_a_multi_that_the_captured_code_reads_itselfOnceCaptured);
    }

    private void captures_a_multi_that_the_captured_code_reads_itselfOnceCaptured() {
        AiServiceInvocation invocation = capture(() -> streamingSupportAssistant
                        .stream("Hi")
                        .collect()
                        .asList()
                        .await()
                        .atMost(Duration.ofSeconds(10)))
                .invocation();

        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void captures_the_items_of_a_returned_multi() {
        List<Object> items = capture(() -> streamingSupportAssistant.stream("Hi")).lambdaOutputItems();

        assertThat(String.join("", items.stream().map(String::valueOf).toList()))
                .isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void tells_that_the_ai_service_does_not_report_the_events_of_its_token_stream() {
        // Quarkus extension does not pass the providers of the application to LangChain4j yet (ISSUE-PROVIDER-LOOKUP, to be opened); flip this assert when it does, by calling tells_that_the_ai_service_does_not_report_the_events_of_its_token_streamOnceCaptured() directly
        ExtensionCapture.assertNotCaptured(() -> capture(() -> streamingSupportAssistant
                        .chat("Hi")
                        .onPartialResponse(token -> {})
                        .onError(error -> {})
                        .start())
                .invocation());
    }

    private void tells_that_the_ai_service_does_not_report_the_events_of_its_token_streamOnceCaptured() {
        // Quarkus extension does not pass the invocation id to its TokenStream in the pinned version, so its events cannot be captured (fixed on quarkus-langchain4j main: https://github.com/quarkiverse/quarkus-langchain4j/pull/3062); flip this assert when the pin moves to a release that contains it
        assertThatThrownBy(() -> capture(() -> streamingSupportAssistant
                        .chat("Hi")
                        .onPartialResponse(token -> {})
                        .onError(error -> {})
                        .start()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not report the events of its TokenStream");
    }
}
