package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("LazyJsonString Integration Tests")
class LazyJsonStringIntegrationTest {

    @Nested
    @DisplayName("Real-world Scenarios")
    class RealWorldScenarios {

        @Test
        @DisplayName("Should handle complex nested objects")
        void shouldHandleComplexNestedObjects() {
            ComplexObject complexObject = new ComplexObject(
                    "test-id",
                    LocalDateTime.now(),
                    new BigDecimal("123.45"),
                    List.of("item1", "item2", "item3"),
                    Map.of("key1", "value1", "key2", "value2"));

            Supplier<Object> supplier = () -> complexObject;
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);

            String result = lazyJsonString.getValue();

            assertThat(result).contains("test-id");
            assertThat(result).contains("123.45");
            assertThat(result).contains("item1");
            assertThat(result).contains("key1");
            assertThat(lazyJsonString.hasError()).isFalse();
        }

        @Test
        @DisplayName("Should handle async computation results")
        void shouldHandleAsyncComputationResults() throws ExecutionException, InterruptedException {
            CompletableFuture<String> asyncResult = CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(100); // Simulate async work
                    return "async-result";
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });

            Supplier<Object> supplier = () -> {
                try {
                    return asyncResult.get();
                } catch (Exception e) {
                    throw new RuntimeException("Async computation failed", e);
                }
            };

            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            String result = lazyJsonString.getValue();

            assertThat(result).isEqualTo("async-result");
            assertThat(lazyJsonString.hasError()).isFalse();
        }

        @Test
        @DisplayName("Should handle database-like objects with lazy loading")
        void shouldHandleDatabaseLikeObjects() {
            DatabaseEntity entity = new DatabaseEntity("user-123");

            Supplier<Object> supplier = () -> entity.loadData();
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);

            String result = lazyJsonString.getValue();

            assertThat(result).contains("user-123");
            assertThat(result).contains("loaded-data");
            assertThat(lazyJsonString.hasError()).isFalse();
        }
    }

    @Nested
    @DisplayName("Error Recovery Scenarios")
    class ErrorRecoveryScenarios {

        @Test
        @DisplayName("Should recover from transient failures")
        void shouldRecoverFromTransientFailures() {
            TransientFailureSupplier supplier = new TransientFailureSupplier();
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);

            // First call fails
            String result1 = lazyJsonString.getValue();
            assertThat(result1).startsWith("LazyEvaluation Error:");
            assertThat(lazyJsonString.hasError()).isTrue();

            // Second call succeeds
            String result2 = lazyJsonString.getValue();
            assertThat(result2).isEqualTo("success-after-retry");
            assertThat(lazyJsonString.hasError()).isFalse();
        }

        @Test
        @DisplayName("Should handle cascading failures gracefully")
        void shouldHandleCascadingFailures() {
            CascadingFailureObject obj = new CascadingFailureObject();
            Supplier<Object> supplier = () -> obj;
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);

            String result = lazyJsonString.getValue();

            assertThat(result).startsWith("LazyEvaluation Error:");
            assertThat(lazyJsonString.hasError()).isTrue();
            assertThat(lazyJsonString.getLastError()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Performance and Memory Scenarios")
    class PerformanceScenarios {

        @Test
        @DisplayName("Should handle large objects efficiently")
        void shouldHandleLargeObjectsEfficiently() {
            LargeObject largeObject = new LargeObject(10000);
            Supplier<Object> supplier = () -> largeObject;
            LazyJsonString lazyJsonString = new LazyJsonString(supplier);

            long startTime = System.currentTimeMillis();
            String result = lazyJsonString.getValue();
            long endTime = System.currentTimeMillis();

            assertThat(result).contains("item-0");
            assertThat(result).contains("item-9999");
            assertThat(endTime - startTime).isLessThan(5000); // Should complete within 5 seconds
            assertThat(lazyJsonString.hasError()).isFalse();
        }

        @Test
        @DisplayName("Should handle memory-intensive operations")
        void shouldHandleMemoryIntensiveOperations() {
            Supplier<Object> supplier = () -> {
                // Simulate memory-intensive operation
                List<String> largeList = new ArrayList<>();
                for (int i = 0; i < 1000; i++) {
                    largeList.add("data-" + i);
                }
                return Map.of("data", largeList, "size", largeList.size());
            };

            LazyJsonString lazyJsonString = new LazyJsonString(supplier);
            String result = lazyJsonString.getValue();

            assertThat(result).contains("data-0");
            assertThat(result).contains("data-999");
            assertThat(result).contains("1000");
            assertThat(lazyJsonString.hasError()).isFalse();
        }
    }

    // Helper classes for integration testing
    private static class ComplexObject {
        private final String id;
        private final LocalDateTime timestamp;
        private final BigDecimal amount;
        private final List<String> items;
        private final Map<String, String> metadata;

        public ComplexObject(
                String id,
                LocalDateTime timestamp,
                BigDecimal amount,
                List<String> items,
                Map<String, String> metadata) {
            this.id = id;
            this.timestamp = timestamp;
            this.amount = amount;
            this.items = items;
            this.metadata = metadata;
        }

        // Getters
        public String getId() {
            return id;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public List<String> getItems() {
            return items;
        }

        public Map<String, String> getMetadata() {
            return metadata;
        }
    }

    private static class DatabaseEntity {
        private final String id;

        public DatabaseEntity(String id) {
            this.id = id;
        }

        public Map<String, Object> loadData() {
            // Simulate database loading
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("data", "loaded-data");
            data.put("timestamp", System.currentTimeMillis());
            return data;
        }
    }

    private static class TransientFailureSupplier implements Supplier<Object> {
        private int callCount = 0;

        @Override
        public Object get() {
            callCount++;
            if (callCount == 1) {
                throw new RuntimeException("Transient failure");
            }
            return "success-after-retry";
        }
    }

    private static class CascadingFailureObject {
        @Override
        public String toString() {
            throw new RuntimeException("toString() also fails");
        }
    }

    private static class LargeObject {
        private final List<String> items;

        public LargeObject(int size) {
            this.items = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                items.add("item-" + i);
            }
        }

        public List<String> getItems() {
            return items;
        }
    }
}
