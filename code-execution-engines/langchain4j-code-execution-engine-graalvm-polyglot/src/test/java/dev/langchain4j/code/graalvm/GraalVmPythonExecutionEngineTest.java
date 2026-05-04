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
    void should_return_captured_output() {

        String result = engine.execute("""
                print('hello')
                import sys
                sys.stderr.write('bad\\n')
                None
                """);

        assertThat(result).isEqualTo("hello\nbad");
        assertThat(engine.execute("print('hello')\n42")).isEqualTo("hello\n42");
    }
}
