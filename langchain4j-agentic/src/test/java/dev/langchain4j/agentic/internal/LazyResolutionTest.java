package dev.langchain4j.agentic.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import dev.langchain4j.spi.ExecutorProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * A lazily resolved value is resolved by one attempt at a time, on a thread of the LangChain4j executor: the callers
 * waiting on an attempt share its result or its failure, a failure is not cached, and an attempt that does not
 * resolve the value within its timeout fails its callers and is interrupted (issue #6958).
 */
@Timeout(60)
class LazyResolutionTest {

    private static final Duration SHORT = Duration.ofMillis(200);

    /** A platform thread calling the resolution, keeping what the call returned or threw. */
    private static class Caller extends Thread {

        private final Callable<String> call;
        volatile String result;
        volatile Throwable failure;

        Caller(Callable<String> call) {
            this.call = call;
        }

        @Override
        public void run() {
            try {
                result = call.call();
            } catch (Throwable e) {
                failure = e;
            }
        }
    }

    private static List<Caller> startCallers(int count, Callable<String> call) {
        List<Caller> callers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Caller caller = new Caller(call);
            caller.start();
            callers.add(caller);
        }
        return callers;
    }

    /** Waits until every caller is parked, which is where a caller waits for the attempt it started or joined. */
    private static void awaitWaiting(List<Caller> callers) {
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> callers.stream().allMatch(caller -> caller.getState() == Thread.State.WAITING));
    }

    private static void joinAll(List<Caller> callers) throws InterruptedException {
        for (Caller caller : callers) {
            caller.join(10_000);
            assertThat(caller.isAlive()).isFalse();
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch, CountDownLatch interrupted) {
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
        }
    }

    @Test
    void concurrent_callers_resolve_the_value_once_on_a_thread_of_the_executor() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        AtomicReference<Thread> resolvingThread = new AtomicReference<>();
        CountDownLatch release = new CountDownLatch(1);
        LazyResolution<String> resolution = new LazyResolution<>(() -> {
            resolutions.incrementAndGet();
            resolvingThread.set(Thread.currentThread());
            awaitUninterruptibly(release, new CountDownLatch(1));
            return "value";
        });
        assertThat(resolution.peek()).isNull();

        List<Caller> callers = startCallers(8, () -> resolution.get(null));
        awaitWaiting(callers);
        release.countDown();
        joinAll(callers);

        assertThat(callers).allSatisfy(caller -> assertThat(caller.result).isEqualTo("value"));
        assertThat(resolutions).hasValue(1);
        assertThat(callers).doesNotContain((Caller) null).noneMatch(caller -> caller == resolvingThread.get());
        assertThat(resolution.peek()).isEqualTo("value");
        assertThat(resolution.get(null)).isEqualTo("value");
        assertThat(resolutions).hasValue(1);
    }

    @Test
    void the_callers_waiting_on_a_failed_attempt_all_fail_with_its_failure() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        IllegalStateException down = new IllegalStateException("down");
        LazyResolution<String> resolution = new LazyResolution<>(() -> {
            resolutions.incrementAndGet();
            awaitUninterruptibly(release, new CountDownLatch(1));
            throw down;
        });

        List<Caller> callers = startCallers(8, () -> resolution.get(null));
        awaitWaiting(callers);
        release.countDown();
        joinAll(callers);

        assertThat(callers).allSatisfy(caller -> assertThat(caller.failure).isSameAs(down));
        assertThat(resolutions).hasValue(1);
    }

    @Test
    void a_call_after_a_failure_starts_a_new_attempt() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        LazyResolution<String> resolution = new LazyResolution<>(() -> {
            if (resolutions.incrementAndGet() == 1) {
                throw new IllegalStateException("down");
            }
            return "value";
        });

        assertThatThrownBy(() -> resolution.get(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("down");
        assertThat(resolution.peek()).isNull();
        assertThat(resolution.get(null)).isEqualTo("value");
        assertThat(resolutions).hasValue(2);
    }

    @Test
    void an_attempt_that_does_not_end_within_its_timeout_fails_and_is_interrupted() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        CountDownLatch interrupted = new CountDownLatch(1);
        LazyResolution<String> resolution = new LazyResolution<>(() -> {
            if (resolutions.incrementAndGet() > 1) {
                return "value";
            }
            try {
                new CountDownLatch(1).await();
                return "never";
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw new IllegalStateException(e);
            }
        });

        assertThatThrownBy(() -> resolution.get(SHORT))
                .isInstanceOf(TimeoutException.class)
                .hasMessageContaining("200 ms");
        assertThat(interrupted.await(10, TimeUnit.SECONDS)).isTrue();

        // once the interrupted thread has ended, the next call starts a new attempt
        await().atMost(10, TimeUnit.SECONDS)
                .ignoreExceptionsInstanceOf(TimeoutException.class)
                .untilAsserted(() -> assertThat(resolution.get(SHORT)).isEqualTo("value"));
        assertThat(resolutions).hasValue(2);
    }

    @Test
    void an_attempt_is_interrupted_at_its_deadline_even_when_nobody_waits_on_it_any_more() throws Exception {
        CountDownLatch interrupted = new CountDownLatch(1);
        LazyResolution<String> resolution = new LazyResolution<>(() -> {
            try {
                new CountDownLatch(1).await();
                return "never";
            } catch (InterruptedException e) {
                interrupted.countDown();
                throw new IllegalStateException(e);
            }
        });

        List<Caller> callers = startCallers(1, () -> resolution.get(Duration.ofMillis(500)));
        awaitWaiting(callers);
        callers.get(0).interrupt();
        joinAll(callers);

        assertThat(callers.get(0).failure).isInstanceOf(InterruptedException.class);
        assertThat(interrupted.await(10, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void an_attempt_ignoring_the_interrupt_stays_the_current_one_and_its_late_value_is_kept() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        LazyResolution<String> resolution = new LazyResolution<>(() -> {
            resolutions.incrementAndGet();
            awaitUninterruptibly(release, interrupted);
            return "late value";
        });

        assertThatThrownBy(() -> resolution.get(SHORT)).isInstanceOf(TimeoutException.class);
        assertThat(interrupted.await(10, TimeUnit.SECONDS)).isTrue();

        // a call arriving while the thread of the attempt has not ended fails at once, without resolving again
        long start = System.nanoTime();
        assertThatThrownBy(() -> resolution.get(Duration.ofMinutes(1))).isInstanceOf(TimeoutException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
        assertThat(resolutions).hasValue(1);

        release.countDown();
        await().atMost(10, TimeUnit.SECONDS).until(() -> resolution.peek() != null);
        assertThat(resolution.get(SHORT)).isEqualTo("late value");
        assertThat(resolutions).hasValue(1);
    }

    @Test
    void a_caller_interrupted_while_waiting_fails_alone() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        LazyResolution<String> resolution = new LazyResolution<>(() -> {
            resolutions.incrementAndGet();
            awaitUninterruptibly(release, new CountDownLatch(1));
            return "value";
        });

        List<Caller> callers = startCallers(3, () -> resolution.get(null));
        awaitWaiting(callers);
        Caller interruptedCaller = callers.remove(0);
        interruptedCaller.interrupt();
        joinAll(List.of(interruptedCaller));
        release.countDown();
        joinAll(callers);

        assertThat(interruptedCaller.failure).isInstanceOf(InterruptedException.class);
        assertThat(callers).allSatisfy(caller -> assertThat(caller.result).isEqualTo("value"));
        assertThat(resolutions).hasValue(1);
    }

    @Test
    void an_attempt_rejected_by_the_executor_fails_and_the_next_call_starts_a_new_one() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        LazyResolution<String> resolution = new LazyResolution<>(() -> {
            resolutions.incrementAndGet();
            return "value";
        });

        ExecutorProvider previous = ExecutorProvider.get();
        ExecutorProvider.set(() -> task -> {
            throw new RejectedExecutionException("saturated");
        });
        try {
            assertThatThrownBy(() -> resolution.get(SHORT))
                    .isInstanceOf(RejectedExecutionException.class)
                    .hasMessage("saturated");
        } finally {
            ExecutorProvider.set(previous);
        }

        assertThat(resolutions).hasValue(0);
        assertThat(resolution.get(SHORT)).isEqualTo("value");
        assertThat(resolutions).hasValue(1);
    }

    @Test
    void a_resolution_can_be_created_already_resolved() throws Exception {
        LazyResolution<String> resolution = LazyResolution.resolved("value");

        assertThat(resolution.peek()).isEqualTo("value");
        assertThat(resolution.get(SHORT)).isEqualTo("value");
    }

    @Test
    void a_timeout_is_described_in_seconds_when_it_is_a_whole_number_of_them() {
        assertThat(LazyResolution.describe(Duration.ofSeconds(30))).isEqualTo("30 s");
        assertThat(LazyResolution.describe(Duration.ofMillis(200))).isEqualTo("200 ms");
        assertThat(LazyResolution.describe(Duration.ofMillis(1500))).isEqualTo("1500 ms");
    }
}
