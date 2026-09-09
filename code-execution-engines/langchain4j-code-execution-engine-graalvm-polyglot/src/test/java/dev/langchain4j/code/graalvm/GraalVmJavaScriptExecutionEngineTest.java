package dev.langchain4j.code.graalvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.code.CodeExecutionEngine;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.Test;

class GraalVmJavaScriptExecutionEngineTest {

    CodeExecutionEngine engine = new GraalVmJavaScriptExecutionEngine();

    @Test
    void should_return_result_when_code_only_evaluates_to_value() {

        String code = """
                function fibonacci(n) {
                    if (n <= 1) return n;
                    return fibonacci(n - 1) + fibonacci(n - 2);
                }

                fibonacci(10)
                """;

        String result = engine.execute(code);

        assertThat(result).isEqualTo("55");
    }

    @Test
    void should_return_output_when_code_only_prints() {

        String result = engine.execute("console.log('hello');");

        assertThat(result).isEqualTo("Output:\nhello");
    }

    @Test
    void should_return_output_printed_to_stderr() {

        String result = engine.execute("console.error('bad');");

        assertThat(result).isEqualTo("Output:\nbad");
    }

    @Test
    void should_return_both_output_and_result_when_code_prints_and_evaluates_to_value() {

        String result = engine.execute("console.log('hello'); console.log('world'); 42");

        assertThat(result).isEqualTo("Output:\nhello\nworld\nResult:\n42");
    }

    @Test
    void should_return_empty_string_when_code_neither_prints_nor_evaluates_to_value() {

        String result = engine.execute("var x = 1;");

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_string_that_reads_like_undefined_as_result() {

        String result = engine.execute("console.log('hello'); 'undefined'");

        assertThat(result).isEqualTo("Output:\nhello\nResult:\nundefined");
    }

    @Test
    void should_throw_when_code_fails() {

        assertThatThrownBy(() -> engine.execute("throw new Error('boom');"))
                .isInstanceOf(PolyglotException.class)
                .hasMessageContaining("boom");
    }
}
