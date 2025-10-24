package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolExecutionResultTest {

    @Nested
    @DisplayName("Lazy Evaluation Tests")
    class LazyEvaluationTests {

        @Test
        @DisplayName("Should support lazy evaluation with supplier")
        void shouldSupportLazyEvaluationWithSupplier() {
            // Given
            TestObject result = new TestObject("lazy");
            AtomicInteger computationCount = new AtomicInteger(0);
            Supplier<Object> lazySupplier = () -> {
                computationCount.incrementAndGet();
                return result;
            };

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result(result)
                    .lazyResultText(lazySupplier)
                    .build();

            // Then - not yet computed
            assertThat(executionResult.isResultTextComputed()).isFalse();
            assertThat(computationCount.get()).isEqualTo(0);

            // When - first access
            String resultText = executionResult.resultText();

            // Then - computed once
            assertThat(executionResult.isResultTextComputed()).isTrue();
            assertThat(computationCount.get()).isEqualTo(1);
            assertThat(resultText).contains("lazy");

            // When - second access
            String resultText2 = executionResult.resultText();

            // Then - cached result, no additional computation
            assertThat(resultText2).isSameAs(resultText);
            assertThat(computationCount.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should handle string return type without JSON processing")
        void shouldHandleStringReturnTypeWithoutJsonProcessing() {
            // Given
            String stringResult = "direct string result";
            Supplier<Object> supplier = () -> stringResult;

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result(stringResult)
                    .lazyResultText(supplier)
                    .build();

            // Then
            String resultText = executionResult.resultText();
            assertThat(resultText).isEqualTo(stringResult);
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }

        @Test
        @DisplayName("Should handle void return type")
        void shouldHandleVoidReturnType() {
            // Given
            Supplier<Object> supplier = () -> null;

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result(null)
                    .lazyResultText(supplier)
                    .build();

            // Then
            String resultText = executionResult.resultText();
            assertThat(resultText).isEqualTo("Success");
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }

        @Test
        @DisplayName("Should handle serialization errors gracefully")
        void shouldHandleSerializationErrorsGracefully() {
            // Given
            Object problematicObject = new Object() {
                @Override
                public String toString() {
                    return "fallback-string";
                }
            };
            Supplier<Object> supplier = () -> problematicObject;

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result(problematicObject)
                    .lazyResultText(supplier)
                    .build();

            // Then
            String resultText = executionResult.resultText();
            assertThat(resultText).isEqualTo("fallback-string");
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }

        @Test
        @DisplayName("Should handle supplier exceptions gracefully")
        void shouldHandleSupplierExceptionsGracefully() {
            // Given
            Supplier<Object> supplier = () -> {
                throw new RuntimeException("Supplier failed");
            };

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result("test")
                    .lazyResultText(supplier)
                    .build();

            // Then
            String resultText = executionResult.resultText();
            assertThat(resultText).startsWith("Error:");
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Eager Evaluation Tests")
    class EagerEvaluationTests {

        @Test
        @DisplayName("Should support eager evaluation")
        void shouldSupportEagerEvaluation() {
            // Given
            TestObject result = new TestObject("eager");
            String precomputedText = "{\"value\":\"eager\"}";

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result(result)
                    .resultText(precomputedText)
                    .build();

            // Then
            assertThat(executionResult.isResultTextComputed()).isTrue();
            assertThat(executionResult.resultText()).isEqualTo(precomputedText);
        }

        @Test
        @DisplayName("Should maintain backward compatibility")
        void shouldMaintainBackwardCompatibility() {
            // Given - legacy builder usage
            TestObject result = new TestObject("legacy");
            String resultText = "{\"value\":\"legacy\"}";

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result(result)
                    .resultText(resultText)
                    .build();

            // Then - works as before
            assertThat(executionResult.resultText()).isEqualTo(resultText);
            assertThat(executionResult.result()).isEqualTo(result);
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("Should be thread-safe for concurrent access")
        void shouldBeThreadSafeForConcurrentAccess() throws InterruptedException {
            // Given
            TestObject result = new TestObject("concurrent");
            AtomicInteger computationCount = new AtomicInteger(0);
            Supplier<Object> supplier = () -> {
                computationCount.incrementAndGet();
                // Simulate some computation time
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return result;
            };

            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result(result)
                    .lazyResultText(supplier)
                    .build();

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            // When - multiple threads access simultaneously
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        String resultText = executionResult.resultText();
                        assertThat(resultText).contains("concurrent");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await(5, TimeUnit.SECONDS);
            executor.shutdown();

            // Then - computed only once despite concurrent access
            assertThat(computationCount.get()).isEqualTo(1);
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should support builder pattern with lazy evaluation")
        void shouldSupportBuilderPatternWithLazyEvaluation() {
            // Given
            TestObject result = new TestObject("builder");
            Supplier<Object> supplier = () -> result;

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .isError(false)
                    .result(result)
                    .lazyResultText(supplier)
                    .useLazyEvaluation(true)
                    .build();

            // Then
            assertThat(executionResult.isError()).isFalse();
            assertThat(executionResult.result()).isEqualTo(result);
            assertThat(executionResult.isResultTextComputed()).isFalse();
        }

        @Test
        @DisplayName("Should support explicit lazy evaluation control")
        void shouldSupportExplicitLazyEvaluationControl() {
            // Given
            TestObject result = new TestObject("explicit");
            Supplier<Object> supplier = () -> result;

            // When - explicitly disable lazy evaluation
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .result(result)
                    .lazyResultText(supplier)
                    .useLazyEvaluation(false)
                    .build();

            // Then - should not use lazy evaluation
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Should be equal when computed values are equal")
        void shouldBeEqualWhenComputedValuesAreEqual() {
            // Given
            TestObject result = new TestObject("equal");
            Supplier<Object> supplier1 = () -> result;
            Supplier<Object> supplier2 = () -> result;

            ToolExecutionResult result1 = ToolExecutionResult.builder()
                    .result(result)
                    .lazyResultText(supplier1)
                    .build();

            ToolExecutionResult result2 = ToolExecutionResult.builder()
                    .result(result)
                    .lazyResultText(supplier2)
                    .build();

            // When & Then
            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        @DisplayName("Should not be equal when computed values differ")
        void shouldNotBeEqualWhenComputedValuesDiffer() {
            // Given
            TestObject result1 = new TestObject("different1");
            TestObject result2 = new TestObject("different2");

            ToolExecutionResult executionResult1 = ToolExecutionResult.builder()
                    .result(result1)
                    .lazyResultText(() -> result1)
                    .build();

            ToolExecutionResult executionResult2 = ToolExecutionResult.builder()
                    .result(result2)
                    .lazyResultText(() -> result2)
                    .build();

            // When & Then
            assertThat(executionResult1).isNotEqualTo(executionResult2);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle error results with lazy evaluation")
        void shouldHandleErrorResultsWithLazyEvaluation() {
            // Given
            String errorMessage = "Tool execution failed";
            Supplier<Object> supplier = () -> errorMessage;

            // When
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .isError(true)
                    .result(null)
                    .lazyResultText(supplier)
                    .build();

            // Then
            assertThat(executionResult.isError()).isTrue();
            assertThat(executionResult.result()).isNull();
            assertThat(executionResult.resultText()).isEqualTo(errorMessage);
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }
    }

    // Test helper class
    private static class TestObject {
        private final String value;

        public TestObject(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return "TestObject{value='" + value + "'}";
        }
    }
}
