package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Tests for the hasResult() method in ToolExecution class.
 */
class ToolExecutionHasResultTest {

    @Test
    void should_always_return_true_since_caching_is_removed() {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            return "lazy result";
        };

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("test-tool")
                .arguments("{}")
                .build();

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(lazySupplier)
                .build();

        ToolExecution execution = ToolExecution.builder()
                .request(request)
                .result(result)
                .build();

        // when & then - hasResult() always returns true since caching is removed
        assertThat(execution.hasResult()).isTrue();
        assertThat(computationCount.get()).isEqualTo(0); // Supplier not called yet
    }

    @Test
    void should_return_true_after_result_is_accessed() {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            return "lazy result";
        };

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("test-tool")
                .arguments("{}")
                .build();

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultTextSupplier(lazySupplier)
                .build();

        ToolExecution execution = ToolExecution.builder()
                .request(request)
                .result(result)
                .build();

        // when
        execution.result(); // Access the result to trigger computation

        // then
        assertThat(execution.hasResult()).isTrue();
        assertThat(computationCount.get()).isEqualTo(1);
    }

    @Test
    void should_return_true_for_non_lazy_results() {
        // given
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("test-id")
                .name("test-tool")
                .arguments("{}")
                .build();

        ToolExecutionResult result = ToolExecutionResult.builder()
                .resultText("immediate result")
                .build();

        ToolExecution execution = ToolExecution.builder()
                .request(request)
                .result(result)
                .build();

        // when & then
        assertThat(execution.hasResult()).isTrue();
    }

    @Test
    void should_always_return_true_after_accessing_only_result_object() {
        // given
        AtomicInteger computationCount = new AtomicInteger(0);
        Supplier<String> lazySupplier = () -> {
            computationCount.incrementAndGet();
            return "lazy text result";
        };

        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder()
                        .name("testTool")
                        .arguments("{}")
                        .build())
                .result(ToolExecutionResult.builder()
                        .result("object result")
                        .resultTextSupplier(lazySupplier)
                        .build())
                .build();

        // when
        Object resultObject = execution.resultObject();

        // then - hasResult() always returns true since caching is removed
        assertThat(resultObject).isEqualTo("object result");
        assertThat(execution.hasResult()).isTrue();
        assertThat(computationCount.get()).isEqualTo(0); // Text supplier not called
    }

    @Test
    void should_always_return_true_with_error_results() {
        // given
        ToolExecution execution = ToolExecution.builder()
                .request(ToolExecutionRequest.builder()
                        .name("testTool")
                        .arguments("{}")
                        .build())
                .result(ToolExecutionResult.builder()
                        .isError(true)
                        .resultText("Error occurred")
                        .build())
                .build();

        // when/then - hasResult() always returns true since caching is removed
        assertThat(execution.hasResult()).isTrue();
    }
}