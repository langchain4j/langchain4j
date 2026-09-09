package dev.langchain4j.code.graalvm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.code.CodeExecutionEngine;
import org.graalvm.polyglot.PolyglotException;
import org.junit.jupiter.api.Test;

class GraalVmPythonExecutionEngineTest {

    CodeExecutionEngine engine = new GraalVmPythonExecutionEngine();

    @Test
    void should_return_result_when_code_only_evaluates_to_value() {

        String code = """
                def fibonacci(n):
                    if n <= 1:
                        return n
                    else:
                        return fibonacci(n-1) + fibonacci(n-2)

                fibonacci(10)
                """;

        String result = engine.execute(code);

        assertThat(result).isEqualTo("55");
    }

    @Test
    void should_return_output_when_code_only_prints() {

        String result = engine.execute("print('hello')");

        assertThat(result).isEqualTo("Output:\nhello");
    }

    @Test
    void should_return_output_printed_to_stderr() {

        String code = """
                import sys
                print('bad', file=sys.stderr)
                """;

        String result = engine.execute(code);

        assertThat(result).isEqualTo("Output:\nbad");
    }

    @Test
    void should_return_both_output_and_result_when_code_prints_and_evaluates_to_value() {

        String code = """
                print('hello')
                print('world')
                42
                """;

        String result = engine.execute(code);

        assertThat(result).isEqualTo("Output:\nhello\nworld\nResult:\n42");
    }

    @Test
    void should_return_empty_string_when_code_neither_prints_nor_evaluates_to_value() {

        String result = engine.execute("x = 1");

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_string_when_code_evaluates_to_module() {

        String code = """
                import math
                math
                """;

        String result = engine.execute(code);

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_string_result() {

        String code = """
                print('hello')
                'world'
                """;

        String result = engine.execute(code);

        assertThat(result).isEqualTo("Output:\nhello\nResult:\nworld");
    }

    @Test
    void should_throw_when_code_fails() {

        assertThatThrownBy(() -> engine.execute("1/0"))
                .isInstanceOf(PolyglotException.class)
                .hasMessageContaining("ZeroDivisionError");
    }
}
