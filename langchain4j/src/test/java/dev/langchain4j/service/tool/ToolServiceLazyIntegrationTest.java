package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ToolServiceLazyIntegrationTest {

    @Test
    void should_create_lazy_tool_execution_result_through_service() {
        // Given
        AtomicInteger computationCount = new AtomicInteger(0);
        TestTool testTool = new TestTool(() -> {
            computationCount.incrementAndGet();
            return "computed-result";
        });
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(testTool));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("testMethod")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("testMethod"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - result is created and computation has happened (tool method was invoked)
        assertThat(result).isNotNull();
        assertThat(computationCount.get()).isEqualTo(1); // Tool method was called
        
        // When accessing result text (lazy text computation)
        String resultText = result.resultText();
        
        // Then - text computation happens on first access
        assertThat(resultText).isEqualTo("computed-result");
        assertThat(computationCount.get()).isEqualTo(1); // Still 1, only tool method was called
        
        // When accessing again
        String resultText2 = result.resultText();
        
        // Then - text computation happens again (no caching)
        assertThat(resultText2).isEqualTo("computed-result");
        assertThat(computationCount.get()).isEqualTo(1); // Still 1, tool method not called again
    }

    @Test
    void should_handle_concurrent_tool_execution_with_lazy_evaluation() throws InterruptedException {
        // Given
        AtomicInteger computationCount = new AtomicInteger(0);
        TestTool testTool = new TestTool(() -> {
            computationCount.incrementAndGet();
            try {
                Thread.sleep(10); // Simulate some work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "concurrent-result";
        });
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(testTool));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("testMethod")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When executing concurrently
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger accessCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ToolExecutionResult result = ToolService.executeWithErrorHandling(
                            request,
                            toolService.toolExecutors().get("testMethod"),
                            context,
                            toolService.argumentsErrorHandler(),
                            toolService.executionErrorHandler()
                    );
                    
                    // Access result text to trigger computation
                    String resultText = result.resultText();
                    assertThat(resultText).isEqualTo("concurrent-result");
                    accessCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // Then
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(accessCount.get()).isEqualTo(threadCount);
        // Each thread should trigger its own computation
        assertThat(computationCount.get()).isEqualTo(threadCount);
        
        executor.shutdown();
    }

    @Test
    void should_handle_error_during_lazy_computation() {
        // Given
        TestTool testTool = new TestTool(() -> {
            throw new RuntimeException("Computation failed");
        });
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(testTool));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("testMethod")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("testMethod"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - error is handled by ToolService error handling
        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).isEqualTo("Computation failed");
    }

    @Test
    void should_maintain_lazy_behavior_with_custom_error_handlers() {
        // Given
        AtomicInteger computationCount = new AtomicInteger(0);
        TestTool testTool = new TestTool(() -> {
            computationCount.incrementAndGet();
            return "custom-handler-result";
        });
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(testTool));
        
        // Custom error handlers
        toolService.argumentsErrorHandler((error, context) -> ToolErrorHandlerResult.text("Custom args error"));
        toolService.executionErrorHandler((error, context) -> ToolErrorHandlerResult.text("Custom exec error"));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("testMethod")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("testMethod"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - tool method is called during execution
        assertThat(computationCount.get()).isEqualTo(1);
        
        String resultText = result.resultText();
        assertThat(resultText).isEqualTo("custom-handler-result");
        assertThat(computationCount.get()).isEqualTo(1); // Still 1, tool method not called again
    }

    @Test
    void should_support_null_result_from_lazy_computation() {
        // Given
        TestTool testTool = new TestTool(() -> null);
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(testTool));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("testMethod")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("testMethod"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then
        assertThat(result.resultText()).isNull();
    }

    // Test tool class for integration testing
    static class TestTool {
        private final Supplier<String> resultSupplier;
        
        TestTool(Supplier<String> resultSupplier) {
            this.resultSupplier = resultSupplier;
        }
        
        @Tool("Test method for lazy evaluation")
        public String testMethod() {
            return resultSupplier.get();
        }
    }
}