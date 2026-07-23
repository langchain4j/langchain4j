package dev.langchain4j.code.graalvm;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.code.CodeExecutionEngine;
import org.junit.jupiter.api.Test;

class GraalVmPythonExecutionEngineTest {

    CodeExecutionEngine engine = new GraalVmPythonExecutionEngine();

    @Test
    void should_execute_code() {

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
    void should_return_stdout() {

        String result = engine.execute("print('hello')");

        assertThat(result).isEqualTo("hello");
    }

    @Test
    void should_return_stderr() {

        String result = engine.execute("""
                import sys
                sys.stderr.write('bad\\n')
                None
                """);

        assertThat(result).isEqualTo("bad");
    }

    @Test
    void should_return_stdout_and_result() {

        String result = engine.execute("""
                print('hello')
                42
                """);

        assertThat(result).isEqualTo("hello\n42");
    }

    @Test
    void should_return_stdout_and_string_none_result() {

        String result = engine.execute("""
                print('hello')
                'None'
                """);

        assertThat(result).isEqualTo("hello\nNone");
    }
}
