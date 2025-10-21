package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

class ToolExecutionLazyTest {

    @Test
    void should_create_tool_execution_with_lazy_result() {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            return "lazy result";
        };

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .name("testTool")
                .build();

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(lazySupplier)
                .build();

        // when
        ToolExecution execution = ToolExecution.builder()
                .request(request)
                .result(result)
                .build();

        // then
        assertThat(execution.request()).isEqualTo(request);
        assertThat(execution.result()).isEqualTo("lazy result");
        assertThat(computationCount.get()).isEqualTo(1);
    }

    @Test
    void should_call_supplier_on_each_access_without_caching() {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            return "result without caching";
        };

        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("testTool").build())
                .result(ToolExecutionResult.builder().resultTextSupplier(lazySupplier).build())
                .build();

        // when - access result multiple times
        String firstAccess = execution.result();
        String secondAccess = execution.result();
        String thirdAccess = execution.result();

        // then - supplier is called each time since caching is removed
        assertThat(firstAccess).isEqualTo("result without caching");
        assertThat(secondAccess).isEqualTo("result without caching");
        assertThat(thirdAccess).isEqualTo("result without caching");
        assertThat(computationCount.get()).isEqualTo(3); // computed on each access
    }

    @Test
    void should_handle_null_result_object_with_lazy_text() {
        // given
        Supplier<String> lazySupplier = () -> "text from supplier";

        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("testTool").build())
                .result(ToolExecutionResult.builder()
                        .result(null)
                        .resultTextSupplier(lazySupplier)
                        .build())
                .build();

        // when & then
        assertThat(execution.resultObject()).isNull();
        assertThat(execution.result()).isEqualTo("text from supplier");
    }

    @Test
    void should_call_supplier_concurrently_without_thread_safety() throws InterruptedException {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            try {
                Thread.sleep(10); // Simulate computation time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "concurrent result";
        };

        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("testTool").build())
                .result(ToolExecutionResult.builder().resultTextSupplier(lazySupplier).build())
                .build();

        // when - access from multiple threads
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        String[] results = new String[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    results[index] = execution.result();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // then - supplier is called by each thread since caching is removed
        assertThat(computationCount.get()).isEqualTo(10);
        for (String threadResult : results) {
            assertThat(threadResult).isEqualTo("concurrent result");
        }
    }

    @Test
    void should_maintain_backward_compatibility_with_string_result() {
        // given
        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("testTool").build())
                .result(ToolExecutionResult.builder().resultText("direct string").build())
                .build();

        // when & then
        assertThat(execution.result()).isEqualTo("direct string");
    }

    @Test
    void should_handle_supplier_exceptions_in_tool_execution() {
        // given
        Supplier<String> failingSupplier = () -> {
            throw new RuntimeException("Computation failed");
        };

        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("testTool").build())
                .result(ToolExecutionResult.builder().resultTextSupplier(failingSupplier).build())
                .build();

        // when & then
        assertThatThrownBy(() -> execution.result())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Computation failed");
    }

    @Test
    void should_work_with_error_results() {
        // given
        Supplier<String> errorSupplier = () -> "Error: Tool execution failed";

        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("testTool").build())
                .result(ToolExecutionResult.builder()
                        .isError(true)
                        .resultTextSupplier(errorSupplier)
                        .build())
                .build();

        // when & then
        assertThat(execution.hasFailed()).isTrue();
        assertThat(execution.result()).isEqualTo("Error: Tool execution failed");
    }

    @Test
    void should_support_complex_object_with_lazy_serialization() {
        // given
        ComplexObject complexObject = new ComplexObject("test", 42);
        AtomicInteger serializationCount = new AtomicInteger(0);
        
        Supplier<String> lazySerializer = () -> {
            serializationCount.incrementAndGet();
            return "{\"name\":\"" + complexObject.name + "\",\"value\":" + complexObject.value + "}";
        };

        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder().name("testTool").build())
                .result(ToolExecutionResult.builder()
                        .result(complexObject)
                        .resultTextSupplier(lazySerializer)
                        .build())
                .build();

        // when & then
        assertThat(execution.resultObject()).isEqualTo(complexObject);
        assertThat(serializationCount.get()).isEqualTo(0); // Not serialized yet
        
        String serialized = execution.result();
        assertThat(serialized).isEqualTo("{\"name\":\"test\",\"value\":42}");
        assertThat(serializationCount.get()).isEqualTo(1); // Serialized once
        
        // Second access calls supplier again since caching is removed
        String secondSerialized = execution.result();
        assertThat(secondSerialized).isEqualTo("{\"name\":\"test\",\"value\":42}");
        assertThat(serializationCount.get()).isEqualTo(2); // Serialized twice
    }

    private static class ComplexObject {
        final String name;
        final int value;

        ComplexObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComplexObject that = (ComplexObject) o;
            return value == that.value && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + value;
        }
    }
}