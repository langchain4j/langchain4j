package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class ToolExecutionResultLazyTest {

    @Test
    void should_not_compute_result_text_until_first_access() {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            return "computed result";
        };

        // when
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(lazySupplier)
                .build();

        // then - result text should not be computed yet (but isResultComputed always returns true now)
        assertThat(computationCount.get()).isEqualTo(0);
        assertThat(result.isResultComputed()).isTrue(); // Always true since caching is removed
    }

    @Test
    void should_compute_result_text_on_first_access() {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            return "computed result";
        };

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(lazySupplier)
                .build();

        // when
        String resultText = result.resultText();

        // then
        assertThat(resultText).isEqualTo("computed result");
        assertThat(computationCount.get()).isEqualTo(1);
        assertThat(result.isResultComputed()).isTrue();
    }

    @Test
    void should_call_supplier_on_each_access_without_caching() {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            return "computed result";
        };

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(lazySupplier)
                .build();

        // when - access result text multiple times
        String firstAccess = result.resultText();
        String secondAccess = result.resultText();
        String thirdAccess = result.resultText();

        // then - computation should happen on each access since caching is removed
        assertThat(firstAccess).isEqualTo("computed result");
        assertThat(secondAccess).isEqualTo("computed result");
        assertThat(thirdAccess).isEqualTo("computed result");
        assertThat(computationCount.get()).isEqualTo(3); // Called 3 times without caching
        assertThat(result.isResultComputed()).isTrue();
    }

    @Test
    void should_call_supplier_concurrently_without_thread_safety() throws InterruptedException {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            // Simulate some computation time
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "computed result";
        };

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(lazySupplier)
                .build();

        // when - access from multiple threads concurrently
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        String[] results = new String[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    results[index] = result.resultText();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then - computation happens on each thread access since caching is removed
        assertThat(computationCount.get()).isEqualTo(10); // Called once per thread
        for (String threadResult : results) {
            assertThat(threadResult).isEqualTo("computed result");
        }
        assertThat(result.isResultComputed()).isTrue();
    }

    @Test
    void should_maintain_backward_compatibility_with_string_constructor() {
        // given
        String directResult = "direct result";

        // when
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultText(directResult)
                .build();

        // then
        assertThat(result.resultText()).isEqualTo(directResult);
        assertThat(result.isResultComputed()).isTrue(); // Should be immediately computed for backward compatibility
    }

    @Test
    void should_handle_supplier_exceptions_gracefully() {
        // given
        Supplier<String> failingSupplier = () -> {
            throw new RuntimeException("Computation failed");
        };

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(failingSupplier)
                .build();

        // when/then
        assertThatThrownBy(() -> result.resultText())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Computation failed");
    }

    @Test
    void should_return_null_from_supplier() {
        // given
        Supplier<String> nullSupplier = () -> null;

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(nullSupplier)
                .build();

        // when/then - null is now allowed since we removed the validation
        assertThat(result.resultText()).isNull();
    }
}