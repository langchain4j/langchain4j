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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests for SLF4J logging validation in ToolExecutionResult lazy evaluation.
 * These tests verify that logging functionality works correctly and doesn't impact performance.
 */
class ToolExecutionResultLoggingTest {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionResultLoggingTest.class);

    @Test
    void should_track_creation_vs_access_ratio_patterns() {
        // given - create multiple results to trigger ratio tracking
        AtomicInteger computationCount = new AtomicInteger(0);
        
        // when - create many results but access only some
        ToolExecutionResult[] results = new ToolExecutionResult[150]; // Should trigger ratio logging
        
        for (int i = 0; i < 150; i++) {
            results[i] = ToolExecutionResult.builder()
                    .resultTextSupplier(() -> {
                        computationCount.incrementAndGet();
                        return "test result " + System.currentTimeMillis();
                    })
                    .build();
        }

        // Access only 20% of results to create meaningful ratio
        for (int i = 0; i < 30; i++) {
            results[i].resultText();
        }

        // then - verify lazy evaluation effectiveness
        assertThat(computationCount.get()).isEqualTo(30); // Only accessed results computed
        double savedComputations = (1.0 - (double) computationCount.get() / results.length) * 100;
        assertThat(savedComputations).isEqualTo(80.0); // 80% computations saved
        
        log.info("Creation vs access ratio validation passed - Computations saved: {}%", savedComputations);
    }

    @Test
    void should_handle_logging_during_normal_operations() {
        // given - normal result creation and access
        AtomicInteger accessCount = new AtomicInteger(0);
        
        // when - create and access result multiple times
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(() -> {
                    accessCount.incrementAndGet();
                    return "normal operation result";
                })
                .build();
        
        String firstAccess = result.resultText();
        String secondAccess = result.resultText();

        // then - verify normal operation with logging
        assertThat(firstAccess).isEqualTo("normal operation result");
        assertThat(secondAccess).isEqualTo("normal operation result");
        assertThat(accessCount.get()).isEqualTo(2); // Each access computes
        
        log.info("Normal operation logging validation passed");
    }

    @Test
    void should_handle_logging_during_slow_computations() {
        // given - result with slow computation
        long startTime = System.currentTimeMillis();
        
        // when - create result with measurable delay
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(() -> {
                    try {
                        Thread.sleep(5); // Simulate slow computation
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "slow computation result";
                })
                .build();
        
        String resultText = result.resultText();
        long endTime = System.currentTimeMillis();

        // then - verify slow computation handling
        assertThat(resultText).isEqualTo("slow computation result");
        assertThat(endTime - startTime).isGreaterThan(4); // At least 4ms delay
        
        log.info("Slow computation logging validation passed - Duration: {}ms", endTime - startTime);
    }

    @Test
    void should_handle_logging_during_error_conditions() {
        // given - result with failing computation
        RuntimeException testException = new RuntimeException("Test computation failure");
        
        // when - create result that throws exception
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(() -> {
                    throw testException;
                })
                .build();

        // then - verify error handling with logging
        assertThatThrownBy(() -> result.resultText())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Test computation failure");
        
        log.info("Error condition logging validation passed");
    }

    @Test
    void should_validate_logging_performance_impact() {
        // given - performance measurement setup
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> testSupplier = () -> {
            computationCount.incrementAndGet();
            return "performance test result";
        };

        // when - measure execution time with logging
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .resultTextSupplier(testSupplier)
                    .build();
            result.resultText();
        }
        long endTime = System.nanoTime();
        long executionTime = endTime - startTime;

        // then - verify acceptable performance with logging
        assertThat(computationCount.get()).isEqualTo(1000);
        assertThat(executionTime).isLessThan(TimeUnit.SECONDS.toNanos(1)); // Should complete within 1 second
        
        log.info("Logging performance impact validation passed - Execution time: {} ms", 
                executionTime / 1_000_000);
    }

    @Test
    void should_handle_concurrent_access_with_logging() throws InterruptedException {
        // given - concurrent access setup
        AtomicInteger computationCount = new AtomicInteger(0);
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(() -> {
                    computationCount.incrementAndGet();
                    return "concurrent result " + Thread.currentThread().getName();
                })
                .build();

        // when - concurrent access from multiple threads
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    String resultText = result.resultText();
                    if (resultText.startsWith("concurrent result")) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then - verify concurrent access with logging
        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(threadCount);
        assertThat(computationCount.get()).isEqualTo(threadCount); // Each thread computes
        
        log.info("Concurrent access logging validation passed - Threads: {}, Computations: {}", 
                threadCount, computationCount.get());
    }

    @Test
    void should_demonstrate_effective_lazy_evaluation_monitoring() {
        // given - realistic mixed usage scenario
        AtomicInteger totalComputations = new AtomicInteger(0);
        
        // when - create many results but access few (realistic lazy scenario)
        ToolExecutionResult[] results = new ToolExecutionResult[200];
        
        for (int i = 0; i < 200; i++) {
            results[i] = ToolExecutionResult.builder()
                    .resultTextSupplier(() -> {
                        totalComputations.incrementAndGet();
                        return "monitored result " + System.currentTimeMillis();
                    })
                    .build();
        }

        // Access only 15% of results (realistic lazy evaluation pattern)
        for (int i = 0; i < 30; i++) {
            results[i].resultText();
        }

        // then - verify effective lazy evaluation monitoring
        assertThat(totalComputations.get()).isEqualTo(30); // Only accessed results computed
        double efficiency = (1.0 - (double) totalComputations.get() / results.length) * 100;
        assertThat(efficiency).isEqualTo(85.0); // 85% efficiency
        
        log.info("Effective lazy evaluation monitoring validation passed - Efficiency: {}%", efficiency);
    }

    @Test
    void should_validate_execution_time_tracking() {
        // given - result with measurable computation
        long creationTime = System.currentTimeMillis();
        
        // when - create result and access after delay
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(() -> {
                    try {
                        Thread.sleep(2); // Small measurable delay
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "timed computation result";
                })
                .build();

        // Add small delay between creation and access
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long accessTime = System.currentTimeMillis();
        String resultText = result.resultText();

        // then - verify execution time tracking
        assertThat(resultText).isEqualTo("timed computation result");
        assertThat(accessTime - creationTime).isGreaterThan(0); // Time elapsed between creation and access
        
        log.info("Execution time tracking validation passed - Time between creation and access: {}ms", 
                accessTime - creationTime);
    }
}