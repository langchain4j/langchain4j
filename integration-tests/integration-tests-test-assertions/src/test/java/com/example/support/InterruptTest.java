package com.example.support;

import static com.example.support.Bookings.bookings;
import static dev.langchain4j.test.LangChain4jAssertions.assertThat;
import static dev.langchain4j.test.service.AiServiceCapture.capture;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * capture(...) has no timeout of its own: it stops waiting when its thread is interrupted, for example by JUnit's
 * {@code @Timeout} with the default SAME_THREAD mode.
 */
@Timeout(30)
class InterruptTest {

    @Test
    void should_stop_waiting_and_cancel_the_returned_future_when_interrupted() throws Exception {
        CountDownLatch modelCalled = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AsyncCustomerSupportAgent agent = CustomerSupport.asyncCustomerSupportAgent(
                MockChatModel.thatResponds(request -> {
                    modelCalled.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return AiMessage.from("too late");
                }),
                bookings());
        AtomicReference<CompletableFuture<String>> future = new AtomicReference<>();
        AtomicReference<Throwable> thrown = new AtomicReference<>();

        Thread testThread = new Thread(() -> {
            try {
                capture(() -> {
                    future.set(agent.chat("Hi"));
                    return future.get();
                });
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        try {
            testThread.start();
            assertThat(modelCalled.await(10, TimeUnit.SECONDS)).isTrue();
            testThread.interrupt();
            testThread.join(10_000);

            assertThat(testThread.isAlive()).isFalse();
            assertThat(thrown.get())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("Interrupted while waiting for");
            assertThat(future.get()).isCancelled();
        } finally {
            release.countDown();
        }
    }
}
