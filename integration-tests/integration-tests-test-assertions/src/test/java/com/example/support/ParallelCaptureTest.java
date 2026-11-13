package com.example.support;

import static com.example.support.Bookings.bookings;
import static com.example.support.ModelScripts.callToolsThenAnswer;
import static com.example.support.ModelScripts.toolCall;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.test.service.AiServiceInvocation;
import dev.langchain4j.test.service.AiServiceCapture;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Tests that run in parallel and share one AI Service: each capture sees only the invocations of its own thread.
 */
@Timeout(60)
@Execution(ExecutionMode.CONCURRENT)
class ParallelCaptureTest {

    static final AtomicInteger RUNNING = new AtomicInteger();
    static final CountDownLatch OVERLAPPED = new CountDownLatch(1);

    // the model waits until two model calls run at the same time, so that the invocations of the tests overlap
    static final ChatModelMock MODEL = ChatModelMock.thatResponds(ParallelCaptureTest::answerOnceOverlapped);

    static final CustomerSupportAgent AGENT = CustomerSupport.customerSupportAgent(
            MODEL, bookings(), CustomerSupport.policyExpert(ChatModelMock.thatAlwaysResponds("unused")));

    static AiMessage answerOnceOverlapped(ChatRequest request) {
        if (RUNNING.incrementAndGet() > 1) {
            OVERLAPPED.countDown();
        }
        try {
            if (!OVERLAPPED.await(30, TimeUnit.SECONDS)) {
                throw new AssertionError("No other model call ran at the same time: the tests did not run in parallel");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } finally {
            RUNNING.decrementAndGet();
        }
        String message = ModelScripts.lastUserMessage(request);
        String bookingNumber = message.substring(message.indexOf("MS-"));
        return callToolsThenAnswer(
                        "Details of " + bookingNumber,
                        toolCall("getBookingDetails", "{\"bookingNumber\":\"" + bookingNumber + "\"}"))
                .apply(request);
    }

    @RepeatedTest(8)
    void should_capture_only_the_invocations_of_its_own_thread(RepetitionInfo repetition) {
        String bookingNumber = repetition.getCurrentRepetition() % 2 == 0 ? "MS-777" : "MS-888";
        String customer = "customer-" + repetition.getCurrentRepetition();

        AiServiceCapture invocations = capture(() -> AGENT.chat(customer, "Show " + bookingNumber));

        AiServiceInvocation invocation = invocations.invocation();
        assertThat(invocation.invocationContext().methodArguments()).containsExactly(customer, "Show " + bookingNumber);
        assertThat(invocation.outputText()).isEqualTo("Details of " + bookingNumber);
        assertThat(invocation)
                .hasToolsExecutedExactlyInAnyOrder("getBookingDetails")
                .hasToolExecutedWith("getBookingDetails", Map.of("bookingNumber", bookingNumber))
                .hasNoFailedToolExecutions();
    }

    @Test
    void should_not_capture_the_invocations_of_other_threads() {
        ExecutorService otherThread = Executors.newSingleThreadExecutor();
        try {
            AiServiceCapture invocations = capture(() -> CompletableFuture.supplyAsync(
                            () -> AGENT.chat("customer-other", "Show MS-777"), otherThread)
                    .join());

            assertThat(invocations.lambdaOutput()).isEqualTo("Details of MS-777");
            assertThat(invocations.invocations()).isEmpty();
        } finally {
            otherThread.shutdownNow();
        }
    }
}
