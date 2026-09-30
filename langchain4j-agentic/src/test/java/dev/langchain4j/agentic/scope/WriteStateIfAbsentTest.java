package dev.langchain4j.agentic.scope;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class WriteStateIfAbsentTest {

    @Test
    void should_write_when_key_is_absent() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        scope.writeStateIfAbsent("key", "value");

        assertThat(scope.readState("key")).isEqualTo("value");
    }

    @Test
    void should_not_overwrite_when_key_is_present() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("key", "original");

        scope.writeStateIfAbsent("key", "replacement");

        assertThat(scope.readState("key")).isEqualTo("original");
    }

    @Test
    void should_overwrite_blank_string_consistently_with_hasState() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("key", "   ");
        assertThat(scope.hasState("key")).isFalse();

        scope.writeStateIfAbsent("key", "replacement");

        assertThat(scope.readState("key")).isEqualTo("replacement");
    }

    @Test
    void should_ignore_null_value() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();

        scope.writeStateIfAbsent("key", null);

        assertThat(scope.hasState("key")).isFalse();
        assertThat(scope.readState("key")).isNull();
    }

    @Test
    void should_write_after_key_removed() {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        scope.writeState("key", "original");
        scope.writeState("key", null);

        scope.writeStateIfAbsent("key", "new-value");

        assertThat(scope.readState("key")).isEqualTo("new-value");
    }

    @Test
    void only_one_writer_wins_under_contention() throws Exception {
        DefaultAgenticScope scope = DefaultAgenticScope.ephemeralAgenticScope();
        int threadCount = 16;
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            int value = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                scope.writeStateIfAbsent("key", value);
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        Object result = scope.readState("key");
        assertThat(result).isInstanceOf(Integer.class);
        assertThat((int) result).isBetween(0, threadCount - 1);
    }
}
