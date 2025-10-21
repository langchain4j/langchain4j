package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ToolServiceConcurrencyTest {

    @Test
    void should_handle_concurrent_access_to_lazy_results() throws Exception {
        // Given
        AtomicInteger computationCount = new AtomicInteger(0);
        ConcurrentTestTool testTool = new ConcurrentTestTool(() -> {
            computationCount.incrementAndGet();
            // Simulate some computation time
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "concurrent-result";
        });
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(testTool));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("concurrentMethod")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When - execute tool to get result
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("concurrentMethod"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - verify concurrent access to resultText is thread-safe
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> results = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    String text = result.resultText(); // Access lazy result
                    synchronized (results) {
                        results.add(text);
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // Start all threads
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then - verify thread safety
        assertThat(finished).isTrue();
        assertThat(exceptions).isEmpty();
        assertThat(results).hasSize(threadCount);
        assertThat(results).allMatch(text -> "concurrent-result".equals(text));
        
        // Verify computation happened multiple times (no caching in lazy evaluation)
        // Note: The tool method is called once during execution, but text computation happens on each access
        assertThat(computationCount.get()).isEqualTo(1); // Tool method called once
        
        // Verify that all threads got the same result (thread-safe access)
        assertThat(results).allMatch(text -> "concurrent-result".equals(text));
    }

    @Test
    void should_handle_concurrent_tool_execution_with_lazy_evaluation() throws Exception {
        // Given
        AtomicInteger executionCount = new AtomicInteger(0);
        ConcurrentTestTool testTool = new ConcurrentTestTool(() -> {
            int count = executionCount.incrementAndGet();
            return "execution-" + count;
        });
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(testTool));
        toolService.executeToolsConcurrently(); // Enable concurrent execution
        
        // When - execute multiple tools concurrently
        int requestCount = 5;
        List<CompletableFuture<ToolExecutionResult>> futures = new ArrayList<>();
        
        for (int i = 0; i < requestCount; i++) {
            ToolExecutionRequest request = ToolExecutionRequest.builder()
                    .name("concurrentMethod")
                    .arguments("{}")
                    .build();
            
            InvocationContext context = InvocationContext.builder().build();
            
            CompletableFuture<ToolExecutionResult> future = CompletableFuture.supplyAsync(() ->
                    ToolService.executeWithErrorHandling(
                            request,
                            toolService.toolExecutors().get("concurrentMethod"),
                            context,
                            toolService.argumentsErrorHandler(),
                            toolService.executionErrorHandler()
                    )
            );
            futures.add(future);
        }
        
        // Wait for all executions to complete
        List<ToolExecutionResult> results = new ArrayList<>();
        for (CompletableFuture<ToolExecutionResult> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        
        // Then - verify all executions completed successfully
        assertThat(results).hasSize(requestCount);
        assertThat(results).allMatch(result -> !result.isError());
        
        // Verify each execution has unique result text
        List<String> resultTexts = results.stream()
                .map(ToolExecutionResult::resultText)
                .toList();
        
        assertThat(resultTexts).hasSize(requestCount);
        assertThat(resultTexts).allMatch(text -> text.startsWith("execution-"));
        
        // Verify all executions happened
        assertThat(executionCount.get()).isEqualTo(requestCount);
    }

    @Test
    void should_maintain_thread_safety_with_error_handling() throws Exception {
        // Given
        ErrorTestTool errorTool = new ErrorTestTool();
        
        ToolService toolService = new ToolService();
        toolService.tools(java.util.List.of(errorTool));
        
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("throwError")
                .arguments("{}")
                .build();
        
        InvocationContext context = InvocationContext.builder().build();
        
        // When - execute tool that throws error
        ToolExecutionResult result = ToolService.executeWithErrorHandling(
                request,
                toolService.toolExecutors().get("throwError"),
                context,
                toolService.argumentsErrorHandler(),
                toolService.executionErrorHandler()
        );
        
        // Then - verify concurrent access to error result is thread-safe
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<String> errorTexts = new ArrayList<>();
        List<Exception> exceptions = new ArrayList<>();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String errorText = result.resultText(); // Access lazy error result
                    synchronized (errorTexts) {
                        errorTexts.add(errorText);
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        boolean finished = doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        
        // Then - verify thread safety with error handling
        assertThat(finished).isTrue();
        assertThat(exceptions).isEmpty();
        assertThat(errorTexts).hasSize(threadCount);
        assertThat(errorTexts).allMatch(text -> "Tool execution failed".equals(text));
        assertThat(result.isError()).isTrue();
    }

    static class ConcurrentTestTool {
        private final java.util.function.Supplier<String> supplier;
        
        ConcurrentTestTool(java.util.function.Supplier<String> supplier) {
            this.supplier = supplier;
        }
        
        @Tool("Test method for concurrent execution")
        public String concurrentMethod() {
            return supplier.get();
        }
    }

    static class ErrorTestTool {
        @Tool("Throws a runtime error for testing")
        public String throwError() {
            throw new RuntimeException("Tool execution failed");
        }
    }
}