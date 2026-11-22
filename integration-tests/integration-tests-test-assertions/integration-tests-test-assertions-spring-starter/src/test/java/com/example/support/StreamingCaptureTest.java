package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.test.service.AiServiceCapture;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Captures the invocations of a streaming {@code @AiService} that returns a {@code TokenStream} or a {@code Flux}.
 */
@SpringBootTest
@Import(TestModels.class)
class StreamingCaptureTest {

    @Autowired
    StreamingSupportAssistant streamingSupportAssistant;

    @Test
    void captures_a_token_stream_started_in_the_lambda() {
        AiServiceInvocation invocation =
                capture(() -> streamingSupportAssistant.chat("Hi").ignoreErrors().start()).invocation();

        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(invocation).hasNoToolExecutions().hasFinishReason(FinishReason.STOP);
        assertThat(invocation.tokenUsage().totalTokenCount()).isLessThanOrEqualTo(30);
    }

    @Test
    void captures_a_token_stream_started_by_the_captured_code() {
        List<String> tokens = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> done = new CompletableFuture<>();

        AiServiceInvocation invocation = capture(() -> streamingSupportAssistant
                        .chat("Hi")
                        .onPartialResponse(tokens::add)
                        .onCompleteResponse(response -> done.complete(null))
                        .onError(done::completeExceptionally)
                        .start())
                .invocation();

        done.join();
        assertThat(String.join("", tokens)).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(invocation.lastRequest()).hasUserMessageContaining("Hi");
    }

    @Test
    void reads_a_returned_flux_to_the_end() {
        AiServiceCapture capture = capture(() -> streamingSupportAssistant.stream("Hi"));

        assertThat(capture.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(capture.invocation()).hasNoToolExecutions().hasFinishReason(FinishReason.STOP);
        assertThat(capture.lambdaOutputItems()).isNotEmpty();
    }

    @Test
    void captures_a_flux_that_the_captured_code_reads_itself() {
        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = capture(() -> streamingSupportAssistant
                        .stream("Hi")
                        .doOnNext(tokens::add)
                        .blockLast())
                .invocation();

        assertThat(String.join("", tokens)).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void fails_when_a_token_stream_is_never_started() {
        assertThatThrownBy(() -> capture(() -> {
                    streamingSupportAssistant.chat("Hi");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("was not started")
                .hasMessageContaining("call start() in the lambda");
    }
}
