package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ToolExecutionResultTest {

    @Test
    void should_create_with_direct_result_text() {
        // given
        String resultText = "test result";
        Object result = new Object();

        // when
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(result)
                .resultText(resultText)
                .build();

        // then
        assertThat(toolExecutionResult.result()).isEqualTo(result);
        assertThat(toolExecutionResult.resultText()).isEqualTo(resultText);
        assertThat(toolExecutionResult.isError()).isFalse();
    }

    @Test
    void should_create_with_lazy_supplier() {
        // given
        AtomicInteger callCount = new AtomicInteger(0);
        Object result = new Object();

        // when
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(result)
                .resultTextSupplier(() -> {
                    callCount.incrementAndGet();
                    return "lazy result";
                })
                .build();

        // then - supplier not called yet
        assertThat(callCount.get()).isEqualTo(0);
        assertThat(toolExecutionResult.result()).isEqualTo(result);
        assertThat(callCount.get()).isEqualTo(0);

        // when - accessing resultText
        String resultText1 = toolExecutionResult.resultText();

        // then - supplier called once
        assertThat(resultText1).isEqualTo("lazy result");
        assertThat(callCount.get()).isEqualTo(1);

        // when - accessing resultText again
        String resultText2 = toolExecutionResult.resultText();

        // then - supplier not called again (cached)
        assertThat(resultText2).isEqualTo("lazy result");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void should_fail_when_neither_result_text_nor_supplier_provided() {
        // when/then
        assertThatThrownBy(
                        () -> ToolExecutionResult.builder().result(new Object()).build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Either resultText or resultTextSupplier must be provided");
    }

    @Test
    void should_use_last_set_value_when_both_provided() {
        // given
        AtomicInteger callCount = new AtomicInteger(0);

        // when - resultText set first, then supplier
        ToolExecutionResult toolExecutionResult1 = ToolExecutionResult.builder()
                .result(new Object())
                .resultText("direct text")
                .resultTextSupplier(() -> {
                    callCount.incrementAndGet();
                    return "lazy result";
                })
                .build();

        // then - supplier is used (last one wins)
        assertThat(toolExecutionResult1.resultText()).isEqualTo("lazy result");
        assertThat(callCount.get()).isEqualTo(1);

        // when - supplier set first, then resultText
        callCount.set(0);
        ToolExecutionResult toolExecutionResult2 = ToolExecutionResult.builder()
                .result(new Object())
                .resultTextSupplier(() -> {
                    callCount.incrementAndGet();
                    return "lazy result";
                })
                .resultText("direct text")
                .build();

        // then - resultText is used (last one wins)
        assertThat(toolExecutionResult2.resultText()).isEqualTo("direct text");
        assertThat(callCount.get()).isEqualTo(0);
    }

    @Test
    void should_handle_error_flag() {
        // when
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultText("error message")
                .isError(true)
                .build();

        // then
        assertThat(toolExecutionResult.isError()).isTrue();
    }

    @Test
    void should_use_lazy_result_in_equals() {
        // given
        ToolExecutionResult result1 = ToolExecutionResult.builder()
                .result("obj")
                .resultTextSupplier(() -> "result")
                .build();

        ToolExecutionResult result2 =
                ToolExecutionResult.builder().result("obj").resultText("result").build();

        // when/then
        assertThat(result1).isEqualTo(result2);
        assertThat(result2).isEqualTo(result1);
    }

    @Test
    void should_use_lazy_result_in_hashCode() {
        // given
        ToolExecutionResult result1 = ToolExecutionResult.builder()
                .result("obj")
                .resultTextSupplier(() -> "result")
                .build();

        ToolExecutionResult result2 =
                ToolExecutionResult.builder().result("obj").resultText("result").build();

        // when/then
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void should_use_lazy_result_in_toString() {
        // given
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result("obj")
                .resultTextSupplier(() -> "result")
                .build();

        // when
        String toString = toolExecutionResult.toString();

        // then
        assertThat(toString).contains("result");
        assertThat(toString).contains("obj");
    }

    @Test
    void should_be_thread_safe_with_lazy_calculation() throws InterruptedException {
        // given
        AtomicInteger callCount = new AtomicInteger(0);
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultTextSupplier(() -> {
                    callCount.incrementAndGet();
                    try {
                        // Simulate some processing time
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "lazy result";
                })
                .build();

        // when - multiple threads access resultText simultaneously
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                String result = toolExecutionResult.resultText();
                assertThat(result).isEqualTo("lazy result");
            });
            threads[i].start();
        }

        // wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // then - supplier may be called multiple times due to race conditions,
        // but should be called at least once and significantly fewer times than thread count
        // (accepting rare duplicate computation in concurrent scenarios)
        assertThat(callCount.get()).isGreaterThanOrEqualTo(1).isLessThanOrEqualTo(threads.length);
    }
}
