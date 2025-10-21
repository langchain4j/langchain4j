package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

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
 * Performance tests for lazy evaluation of ToolExecutionResult.
 * These tests focus on measuring performance improvements and resource usage.
 */
class ToolExecutionResultPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionResultPerformanceTest.class);

    @Test
    void should_demonstrate_memory_efficiency_with_unused_results() {
        // given - simulate expensive computation that won't be used
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> expensiveSupplier = () -> {
            computationCount.incrementAndGet();
            // Simulate expensive JSON marshalling or computation
            StringBuilder largeResult = new StringBuilder();
            for (int i = 0; i < 1000; i++) {
                largeResult.append("expensive computation result ").append(i).append("\n");
            }
            return largeResult.toString();
        };

        // when - create result but don't access it
        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(expensiveSupplier)
                .build();

        // then - expensive computation should not have occurred
        assertThat(computationCount.get()).isEqualTo(0);
        log.info("Memory efficiency test passed - expensive computation deferred");
    }

    @Test
    void should_measure_lazy_vs_eager_creation_time() {
        // given
        long startTime, endTime;
        int iterations = 1000;

        // Measure lazy creation time
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ToolExecutionResult.builder()
                    .resultTextSupplier(() -> "expensive result " + System.currentTimeMillis())
                    .build();
        }
        endTime = System.nanoTime();
        long lazyCreationTime = endTime - startTime;

        // Measure eager creation time
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ToolExecutionResult.builder()
                    .resultText("expensive result " + System.currentTimeMillis())
                    .build();
        }
        endTime = System.nanoTime();
        long eagerCreationTime = endTime - startTime;

        // then - lazy creation should be faster or comparable
        log.info("Lazy creation time: {} ns, Eager creation time: {} ns", lazyCreationTime, eagerCreationTime);
        assertThat(lazyCreationTime).isLessThanOrEqualTo(eagerCreationTime * 2); // Allow some overhead
    }

    @Test
    void should_handle_high_concurrency_access_patterns() throws InterruptedException {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> concurrentSupplier = () -> {
            computationCount.incrementAndGet();
            return "concurrent result " + Thread.currentThread().getName();
        };

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(concurrentSupplier)
                .build();

        // when - high concurrency access
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    result.resultText();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        long endTime = System.nanoTime();
        executor.shutdown();

        // then - should handle high concurrency without significant performance degradation
        long totalTime = endTime - startTime;
        log.info("High concurrency test completed in {} ns with {} computations", totalTime, computationCount.get());
        assertThat(computationCount.get()).isEqualTo(threadCount); // Each thread computes once
        assertThat(totalTime).isLessThan(TimeUnit.SECONDS.toNanos(5)); // Should complete within 5 seconds
    }

    @Test
    void should_measure_computation_overhead_vs_direct_access() {
        // given
        String directResult = "direct result";
        Supplier<String> supplierResult = () -> "supplier result";

        // Measure direct access time
        long startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            ToolExecutionResult.builder()
                    .resultText(directResult)
                    .build()
                    .resultText();
        }
        long endTime = System.nanoTime();
        long directAccessTime = endTime - startTime;

        // Measure supplier access time
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            ToolExecutionResult.builder()
                    .resultTextSupplier(supplierResult)
                    .build()
                    .resultText();
        }
        endTime = System.nanoTime();
        long supplierAccessTime = endTime - startTime;

        // then - supplier overhead should be reasonable
        log.info("Direct access time: {} ns, Supplier access time: {} ns", directAccessTime, supplierAccessTime);
        assertThat(supplierAccessTime).isLessThan(directAccessTime * 10); // Allow reasonable overhead
    }

    @Test
    void should_demonstrate_performance_with_json_marshalling_simulation() {
        // given - simulate expensive JSON marshalling
        AtomicInteger marshallingCount = new AtomicInteger(0);
        Supplier<String> jsonMarshallingSupplier = () -> {
            marshallingCount.incrementAndGet();
            // Simulate JSON marshalling overhead
            try {
                Thread.sleep(1); // Simulate marshalling time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "{\"result\": \"marshalled data\", \"timestamp\": " + System.currentTimeMillis() + "}";
        };

        long startTime = System.nanoTime();

        // when - create multiple results but only access some
        ToolExecutionResult[] results = new ToolExecutionResult[100];
        for (int i = 0; i < 100; i++) {
            results[i] = ToolExecutionResult.builder()
                    .resultTextSupplier(jsonMarshallingSupplier)
                    .build();
        }

        // Access only 10% of results
        for (int i = 0; i < 10; i++) {
            results[i].resultText();
        }

        long endTime = System.nanoTime();

        // then - only accessed results should trigger marshalling
        assertThat(marshallingCount.get()).isEqualTo(10);
        log.info("JSON marshalling simulation completed in {} ns with {} marshalling operations", 
                endTime - startTime, marshallingCount.get());
    }

    @Test
    void should_track_creation_vs_access_ratio_for_monitoring() {
        // given
        AtomicInteger creationCount = new AtomicInteger(0);
        AtomicInteger accessCount = new AtomicInteger(0);

        Supplier<String> monitoringSupplier = () -> {
            accessCount.incrementAndGet();
            return "monitored result";
        };

        // when - create many results but access few
        for (int i = 0; i < 100; i++) {
            creationCount.incrementAndGet();
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .resultTextSupplier(monitoringSupplier)
                    .build();

            // Access only 20% of results
            if (i % 5 == 0) {
                result.resultText();
            }
        }

        // then - track creation vs access ratio
        double accessRatio = (double) accessCount.get() / creationCount.get();
        log.info("Creation count: {}, Access count: {}, Access ratio: {}", 
                creationCount.get(), accessCount.get(), accessRatio);
        
        assertThat(creationCount.get()).isEqualTo(100);
        assertThat(accessCount.get()).isEqualTo(20);
        assertThat(accessRatio).isEqualTo(0.2);
    }

    @Test
    void should_measure_memory_usage_pattern_simulation() {
        // given - simulate memory usage patterns
        AtomicInteger largeObjectCreations = new AtomicInteger(0);
        
        Supplier<String> memoryIntensiveSupplier = () -> {
            largeObjectCreations.incrementAndGet();
            // Simulate creating large objects that would consume memory
            return "large_object_" + System.nanoTime() + "_" + "x".repeat(1000);
        };

        // when - create many lazy results
        ToolExecutionResult[] results = new ToolExecutionResult[1000];
        long startTime = System.nanoTime();
        
        for (int i = 0; i < 1000; i++) {
            results[i] = ToolExecutionResult.builder()
                    .resultTextSupplier(memoryIntensiveSupplier)
                    .build();
        }
        
        long creationTime = System.nanoTime() - startTime;

        // Access only a small subset
        startTime = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            results[i].resultText();
        }
        long accessTime = System.nanoTime() - startTime;

        // then - memory-intensive objects should only be created when accessed
        assertThat(largeObjectCreations.get()).isEqualTo(50);
        log.info("Memory usage simulation - Creation time: {} ns, Access time: {} ns, Objects created: {}", 
                creationTime, accessTime, largeObjectCreations.get());
    }

    @Test
    void should_validate_performance_under_error_conditions() {
        // given
        AtomicInteger errorCount = new AtomicInteger(0);
        Supplier<String> errorProneSupplier = () -> {
            errorCount.incrementAndGet();
            if (errorCount.get() % 3 == 0) {
                throw new RuntimeException("Simulated error");
            }
            return "success result";
        };

        // when - create results and handle errors
        int successCount = 0;
        int exceptionCount = 0;
        long startTime = System.nanoTime();

        for (int i = 0; i < 100; i++) {
            ToolExecutionResult result = ToolExecutionResult.builder()
                    .resultTextSupplier(errorProneSupplier)
                    .build();

            try {
                result.resultText();
                successCount++;
            } catch (RuntimeException e) {
                exceptionCount++;
            }
        }

        long endTime = System.nanoTime();

        // then - error handling should not significantly impact performance
        log.info("Error handling performance test completed in {} ns - Success: {}, Errors: {}", 
                endTime - startTime, successCount, exceptionCount);
        
        assertThat(successCount + exceptionCount).isEqualTo(100);
        assertThat(exceptionCount).isGreaterThan(0); // Some errors should occur
        assertThat(endTime - startTime).isLessThan(TimeUnit.SECONDS.toNanos(1));
    }

    @Test
    void should_benchmark_lazy_evaluation_effectiveness() {
        // given - benchmark scenario with mixed usage patterns
        AtomicInteger totalComputations = new AtomicInteger(0);
        
        Supplier<String> benchmarkSupplier = () -> {
            totalComputations.incrementAndGet();
            // Simulate realistic computation
            return "benchmark_result_" + System.currentTimeMillis() + "_" + Thread.currentThread().getId();
        };

        long startTime = System.nanoTime();

        // when - realistic usage pattern: create many, use few
        ToolExecutionResult[] results = new ToolExecutionResult[500];
        
        // Creation phase
        for (int i = 0; i < 500; i++) {
            results[i] = ToolExecutionResult.builder()
                    .resultTextSupplier(benchmarkSupplier)
                    .build();
        }
        
        long creationPhaseTime = System.nanoTime() - startTime;

        // Access phase - realistic 15% access rate
        startTime = System.nanoTime();
        for (int i = 0; i < 75; i++) {
            results[i].resultText();
        }
        long accessPhaseTime = System.nanoTime() - startTime;

        // then - validate lazy evaluation effectiveness
        assertThat(totalComputations.get()).isEqualTo(75);
        
        double computationSavings = 1.0 - ((double) totalComputations.get() / results.length);
        log.info("Lazy evaluation benchmark - Creation: {} ns, Access: {} ns, Computation savings: {}%", 
                creationPhaseTime, accessPhaseTime, computationSavings * 100);
        
        assertThat(computationSavings).isGreaterThan(0.8); // At least 80% savings
    }
}