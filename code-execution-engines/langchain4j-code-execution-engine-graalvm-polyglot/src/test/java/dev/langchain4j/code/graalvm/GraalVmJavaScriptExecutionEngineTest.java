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
    void should_return_captured_output() {

        String result = engine.execute("console.log('hello'); console.error('bad');");

        assertThat(result).isEqualTo("hello\nbad");
        assertThat(engine.execute("console.log('hello'); 42")).isEqualTo("hello\n42");
    }
}
