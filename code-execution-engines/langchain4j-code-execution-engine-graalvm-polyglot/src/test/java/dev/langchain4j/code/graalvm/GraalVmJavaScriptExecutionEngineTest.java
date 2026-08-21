package dev.langchain4j.code.graalvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.code.CodeExecutionEngine;
import org.junit.jupiter.api.Test;

class GraalVmJavaScriptExecutionEngineTest {

    CodeExecutionEngine engine = new GraalVmJavaScriptExecutionEngine();

    @Test
    void should_execute_code() {

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
    void should_return_stdout() {

        String result = engine.execute("console.log('hello');");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void should_return_stderr() {

        String result = engine.execute("console.error('bad');");

        assertThat(result).isEqualTo("bad");
    }

    @Test
    void should_return_stdout_and_result() {

        String result = engine.execute("console.log('hello'); 42");

        assertThat(result).isEqualTo("hello\n42");
    }

    @Test
    void should_return_stdout_and_string_null_result() {

        String result = engine.execute("console.log('hello'); 'null'");

        assertThat(result).isEqualTo("hello\nnull");
    }

    @Test
    void should_return_stdout_and_string_undefined_result() {

        String result = engine.execute("console.log('hello'); 'undefined'");

        assertThat(result).isEqualTo("hello\nundefined");
    }
}
