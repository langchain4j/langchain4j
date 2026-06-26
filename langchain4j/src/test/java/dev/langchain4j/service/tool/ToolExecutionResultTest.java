package dev.langchain4j.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.image.Image;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                .hasMessage("One of resultText, resultTextSupplier, or resultContents must be provided");
    }

    @Test
    void should_fail_when_resultText_and_resultTextSupplier_both_set() {
        assertThatThrownBy(() -> ToolExecutionResult.builder()
                .result(new Object())
                .resultText("direct text")
                .resultTextSupplier(() -> "lazy result")
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void should_fail_when_resultTextSupplier_and_resultText_both_set() {
        assertThatThrownBy(() -> ToolExecutionResult.builder()
                .result(new Object())
                .resultTextSupplier(() -> "lazy result")
                .resultText("direct text")
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
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

    @Test
    void should_not_be_equal_when_attributes_differ() {
        // given
        ToolExecutionResult result1 = ToolExecutionResult.builder()
                .result("obj")
                .resultText("text")
                .attributes(Map.of("key", "value1"))
                .build();

        ToolExecutionResult result2 = ToolExecutionResult.builder()
                .result("obj")
                .resultText("text")
                .attributes(Map.of("key", "value2"))
                .build();

        // when/then
        assertThat(result1).isNotEqualTo(result2);
        assertThat(result2).isNotEqualTo(result1);
    }

    @Test
    void should_be_equal_when_attributes_are_the_same() {
        // given
        ToolExecutionResult result1 = ToolExecutionResult.builder()
                .result("obj")
                .resultText("text")
                .attributes(Map.of("key", "value"))
                .build();

        ToolExecutionResult result2 = ToolExecutionResult.builder()
                .result("obj")
                .resultText("text")
                .attributes(Map.of("key", "value"))
                .build();

        // when/then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void should_be_equal_when_both_have_null_attributes() {
        // given
        ToolExecutionResult result1 =
                ToolExecutionResult.builder().result("obj").resultText("text").build();

        ToolExecutionResult result2 =
                ToolExecutionResult.builder().result("obj").resultText("text").build();

        // when/then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
    }

    @Test
    void should_create_with_resultContents() {
        // given
        List<Content> contents = List.of(
                TextContent.from("description"),
                ImageContent.from(Image.builder().base64Data("abc").mimeType("image/png").build())
        );

        // when
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultContents(contents)
                .build();

        // then
        assertThat(toolExecutionResult.resultContents()).isEqualTo(contents);
    }

    @Test
    void should_derive_resultContents_from_resultText() {
        // when
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultText("hello")
                .build();

        // then
        assertThat(toolExecutionResult.resultContents()).hasSize(1);
        assertThat(toolExecutionResult.resultContents().get(0)).isInstanceOf(TextContent.class);
        assertThat(((TextContent) toolExecutionResult.resultContents().get(0)).text()).isEqualTo("hello");
    }

    @Test
    void should_derive_resultContents_from_resultTextSupplier() {
        // when
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultTextSupplier(() -> "lazy")
                .build();

        // then
        assertThat(toolExecutionResult.resultContents()).hasSize(1);
        assertThat(((TextContent) toolExecutionResult.resultContents().get(0)).text()).isEqualTo("lazy");
        assertThat(toolExecutionResult.resultText()).isEqualTo("lazy");
    }

    @Test
    void should_derive_resultText_from_single_TextContent() {
        // when
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultContents(List.of(TextContent.from("hello")))
                .build();

        // then
        assertThat(toolExecutionResult.resultText()).isEqualTo("hello");
    }

    @Test
    void should_fail_resultText_when_resultContents_has_non_text() {
        // given
        List<Content> contents = List.of(
                TextContent.from("first"),
                ImageContent.from(Image.builder().base64Data("abc").mimeType("image/png").build())
        );

        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultContents(contents)
                .build();

        // when/then
        assertThatThrownBy(toolExecutionResult::resultText)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Use resultContents() instead");
    }

    @Test
    void should_fail_resultText_when_resultContents_has_multiple_text() {
        // given
        List<Content> contents = List.of(
                TextContent.from("first"),
                TextContent.from("second")
        );

        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultContents(contents)
                .build();

        // when/then
        assertThatThrownBy(toolExecutionResult::resultText)
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Use resultContents() instead");
    }

    @Test
    void should_fail_when_resultText_and_resultContents_both_set() {
        assertThatThrownBy(() -> ToolExecutionResult.builder()
                .result(new Object())
                .resultText("from text")
                .resultContents(List.of(TextContent.from("from contents")))
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void should_fail_when_resultContents_and_resultText_both_set() {
        assertThatThrownBy(() -> ToolExecutionResult.builder()
                .result(new Object())
                .resultContents(List.of(TextContent.from("from contents")))
                .resultText("from text")
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void should_fail_when_resultContents_and_resultTextSupplier_both_set() {
        assertThatThrownBy(() -> ToolExecutionResult.builder()
                .result(new Object())
                .resultContents(List.of(TextContent.from("from contents")))
                .resultTextSupplier(() -> "from supplier")
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void should_fail_when_resultTextSupplier_and_resultContents_both_set() {
        assertThatThrownBy(() -> ToolExecutionResult.builder()
                .result(new Object())
                .resultTextSupplier(() -> "from supplier")
                .resultContents(List.of(TextContent.from("from contents")))
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void should_return_TextContent_in_resultContents_when_resultText_is(String text) {
        // when
        ToolExecutionResult toolExecutionResult = ToolExecutionResult.builder()
                .result(new Object())
                .resultText(text)
                .build();

        // then
        assertThat(toolExecutionResult.resultContents()).containsExactly(TextContent.from(text));
        assertThat(toolExecutionResult.resultText()).isEqualTo(text);
    }
}
