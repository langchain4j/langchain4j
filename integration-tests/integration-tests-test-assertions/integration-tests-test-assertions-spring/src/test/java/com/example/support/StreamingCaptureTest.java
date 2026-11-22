package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.support.assistant.StreamingSupportAssistant;
import com.example.support.web.ReactiveSupportService;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.TaskExecutor;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@SpringBootTest
@Import(TestModels.class)
class StreamingCaptureTest {

    @Autowired
    StreamingSupportAssistant streamingSupportAssistant;

    @Autowired
    ReactiveSupportService reactiveSupportService;

    @Autowired
    TaskExecutor taskExecutor;

    @Test
    void records_a_returned_flux_and_reads_it_to_the_end() {
        AiServiceInvocation invocation = capture(() -> reactiveSupportService.streamAnswer("Hi")).invocation();

        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(invocation).hasNoToolExecutions().hasFinishReason(FinishReason.STOP);
    }

    @Test
    void records_a_flux_that_the_captured_code_reads_itself() {
        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = capture(() -> reactiveSupportService
                        .streamAnswer("Hi")
                        .doOnNext(tokens::add)
                        .blockLast())
                .invocation();

        assertThat(String.join("", tokens)).isEqualTo(TestModels.STREAMED_ANSWER);
        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void does_not_capture_a_flux_subscribed_on_another_scheduler() {
        Flux<String> flux = reactiveSupportService.streamAnswer("Hi").subscribeOn(Schedulers.boundedElastic());

        AiServiceCapture invocations = capture(() -> flux);

        assertThat(invocations.invocations()).isEmpty();
        assertThatThrownBy(invocations::invocation).hasMessageContaining("subscribeOn(...)");
    }

    @Test
    void records_a_token_stream_started_in_the_lambda() {
        AiServiceInvocation invocation =
                capture(() -> streamingSupportAssistant.chat("Hi").ignoreErrors().start()).invocation();

        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void records_a_token_stream_started_on_a_spring_task_executor() {
        List<String> tokens = new CopyOnWriteArrayList<>();

        AiServiceInvocation invocation = capture(() -> {
                    TokenStream tokenStream = streamingSupportAssistant.chat("Hi");
                    taskExecutor.execute(() -> tokenStream
                            .onPartialResponse(tokens::add)
                            .onError(error -> {})
                            .start());
                })
                .invocation();

        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void fails_when_a_token_stream_is_never_started() {
        assertThatThrownBy(() -> capture(() -> {
                    streamingSupportAssistant.chat("Hi");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(StreamingSupportAssistant.class.getName() + ".chat was not started")
                .hasMessageContaining("call start() in the lambda");
    }
}
