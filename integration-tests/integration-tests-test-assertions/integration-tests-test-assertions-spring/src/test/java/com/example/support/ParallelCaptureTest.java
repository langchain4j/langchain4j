package com.example.support;

import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.support.assistant.StreamingSupportAssistant;
import com.example.support.assistant.SupportAssistant;
import dev.langchain4j.test.service.AiServiceInvocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * The tests of this class run in parallel (see junit-platform.properties) and share one Spring context.
 */
@SpringBootTest
@Import(TestModels.class)
@Execution(ExecutionMode.CONCURRENT)
class ParallelCaptureTest {

    @Autowired
    SupportAssistant supportAssistant;

    @Autowired
    StreamingSupportAssistant streamingSupportAssistant;

    @RepeatedTest(10)
    void parallel_tests_capture_only_their_own_invocations(RepetitionInfo repetition) {
        String bookingNumber = "MS-P" + repetition.getCurrentRepetition();

        AiServiceInvocation invocation =
                capture(() -> supportAssistant.chat("Please cancel " + bookingNumber)).invocation();

        assertThat(invocation)
                .hasToolsExecutedExactlyInAnyOrder("getBookingDetails", "cancelBooking")
                .hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", bookingNumber));
    }

    @RepeatedTest(5)
    void parallel_streaming_tests_capture_only_their_own_invocations() {
        AiServiceInvocation invocation =
                capture(() -> streamingSupportAssistant.chat("Hi").ignoreErrors().start()).invocation();

        assertThat(invocation.outputText()).isEqualTo(TestModels.STREAMED_ANSWER);
    }

    @Test
    void recordings_on_many_threads_at_once_do_not_see_each_other() throws Exception {
        int threads = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<AiServiceInvocation>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String bookingNumber = "MS-T" + i;
                futures.add(executor.submit(() -> {
                    start.await();
                    return capture(() -> supportAssistant.chat("Please cancel " + bookingNumber)).invocation();
                }));
            }
            start.countDown();
            for (int i = 0; i < threads; i++) {
                AiServiceInvocation invocation = futures.get(i).get(30, TimeUnit.SECONDS);
                assertThat(invocation).hasToolExecutedWith("cancelBooking", Map.of("bookingNumber", "MS-T" + i));
                assertThat(invocation.toolExecutions()).hasSize(2);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
