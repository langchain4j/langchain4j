package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ToolExecutionResultIntegrationTest {

    @Nested
    @DisplayName("Integration with DefaultToolExecutor")
    class DefaultToolExecutorIntegrationTests {

        @Test
        @DisplayName("Should work with existing DefaultToolExecutor for no-arg methods")
        void shouldWorkWithExistingDefaultToolExecutor() throws NoSuchMethodException {
            // Given
            TestTools tools = new TestTools();
            DefaultToolExecutor executor =
                    new DefaultToolExecutor(tools, TestTools.class.getDeclaredMethod("noArgTool"));

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("noArgTool")
                    .arguments("{}")
                    .build();

            // When
            ToolExecutionResult result = executor.executeWithContext(request, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isError()).isFalse();
            assertThat(result.result()).isEqualTo("no-arg result");
            assertThat(result.resultText()).isEqualTo("no-arg result");
            assertThat(result.isResultTextComputed()).isTrue();
        }

        @Test
        @DisplayName("Should handle complex object serialization")
        void shouldHandleComplexObjectSerialization() throws NoSuchMethodException {
            // Given
            TestTools tools = new TestTools();
            DefaultToolExecutor executor =
                    new DefaultToolExecutor(tools, TestTools.class.getDeclaredMethod("complexObjectTool"));

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("complexObjectTool")
                    .arguments("{}")
                    .build();

            // When
            ToolExecutionResult result = executor.executeWithContext(request, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isError()).isFalse();
            assertThat(result.result()).isInstanceOf(ComplexObject.class);

            ComplexObject complexResult = (ComplexObject) result.result();
            assertThat(complexResult.getName()).isEqualTo("John");
            assertThat(complexResult.getId()).isEqualTo(42);

            String resultText = result.resultText();
            assertThat(resultText).contains("John");
            assertThat(resultText).contains("42");
        }

        @Test
        @DisplayName("Should handle void return type")
        void shouldHandleVoidReturnType() throws NoSuchMethodException {
            // Given
            TestTools tools = new TestTools();
            DefaultToolExecutor executor =
                    new DefaultToolExecutor(tools, TestTools.class.getDeclaredMethod("voidTool"));

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("voidTool")
                    .arguments("{}")
                    .build();

            // When
            ToolExecutionResult result = executor.executeWithContext(request, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isError()).isFalse();
            assertThat(result.result()).isNull();
            assertThat(result.resultText()).isEqualTo("Success");
        }

        @Test
        @DisplayName("Should handle tool execution errors")
        void shouldHandleToolExecutionErrors() throws NoSuchMethodException {
            // Given
            TestTools tools = new TestTools();
            DefaultToolExecutor executor =
                    new DefaultToolExecutor(tools, TestTools.class.getDeclaredMethod("errorTool"));

            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("errorTool")
                    .arguments("{}")
                    .build();

            // When
            ToolExecutionResult result = executor.executeWithContext(request, null);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.isError()).isTrue();
            assertThat(result.result()).isNull();
            assertThat(result.resultText()).isEqualTo("Tool execution failed");
        }
    }

    @Nested
    @DisplayName("Backward Compatibility Tests")
    class BackwardCompatibilityTests {

        @Test
        @DisplayName("Should maintain compatibility with existing ToolExecutionResult usage")
        void shouldMaintainCompatibilityWithExistingUsage() {
            // Given - legacy usage pattern
            String resultText = "legacy result";
            Object result = "legacy object";

            // When - using old constructor pattern
            ToolExecutionResult executionResult = ToolExecutionResult.builder()
                    .isError(false)
                    .result(result)
                    .resultText(resultText)
                    .build();

            // Then - should work exactly as before
            assertThat(executionResult.isError()).isFalse();
            assertThat(executionResult.result()).isEqualTo(result);
            assertThat(executionResult.resultText()).isEqualTo(resultText);
            assertThat(executionResult.isResultTextComputed()).isTrue();
        }

        @Test
        @DisplayName("Should work with existing equals and hashCode contracts")
        void shouldWorkWithExistingEqualsAndHashCodeContracts() {
            // Given
            String resultText = "same result";
            Object result = "same object";

            ToolExecutionResult result1 = ToolExecutionResult.builder()
                    .isError(false)
                    .result(result)
                    .resultText(resultText)
                    .build();

            ToolExecutionResult result2 = ToolExecutionResult.builder()
                    .isError(false)
                    .result(result)
                    .resultText(resultText)
                    .build();

            // When & Then
            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
            assertThat(result1.toString()).contains(resultText);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should demonstrate lazy evaluation performance benefit")
        void shouldDemonstrateLazyEvaluationPerformanceBenefit() {
            // Given
            AtomicInteger expensiveComputationCount = new AtomicInteger(0);
            ComplexObject expensiveResult = new ComplexObject("expensive", 999);

            // Create lazy result that tracks computation
            ToolExecutionResult lazyResult = ToolExecutionResult.builder()
                    .result(expensiveResult)
                    .lazyResultText(() -> {
                        expensiveComputationCount.incrementAndGet();
                        // Simulate expensive JSON serialization
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return expensiveResult;
                    })
                    .build();

            // When - creating the result (should not trigger computation)
            assertThat(expensiveComputationCount.get()).isEqualTo(0);
            assertThat(lazyResult.isResultTextComputed()).isFalse();

            // When - accessing result text (should trigger computation)
            String resultText = lazyResult.resultText();

            // Then - computation happened exactly once
            assertThat(expensiveComputationCount.get()).isEqualTo(1);
            assertThat(lazyResult.isResultTextComputed()).isTrue();
            assertThat(resultText).contains("expensive");

            // When - accessing again (should use cached result)
            String resultText2 = lazyResult.resultText();

            // Then - no additional computation
            assertThat(expensiveComputationCount.get()).isEqualTo(1);
            assertThat(resultText2).isSameAs(resultText);
        }
    }

    // Test helper classes
    private static class TestTools {

        @Tool("No argument tool")
        public String noArgTool() {
            return "no-arg result";
        }

        @Tool("Complex object creation tool")
        public ComplexObject complexObjectTool() {
            return new ComplexObject("John", 42);
        }

        @Tool("Void return tool")
        public void voidTool() {
            // Does nothing, returns void
        }

        @Tool("Error throwing tool")
        public String errorTool() {
            throw new RuntimeException("Tool execution failed");
        }
    }

    private static class ComplexObject {
        private final String name;
        private final int id;

        public ComplexObject(String name, int id) {
            this.name = name;
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public int getId() {
            return id;
        }

        @Override
        public String toString() {
            return "ComplexObject{name='" + name + "', id=" + id + "}";
        }
    }
}
