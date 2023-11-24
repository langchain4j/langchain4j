package dev.langchain4j.code.graal;

import dev.langchain4j.code.CodeExecutionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GraalPythonExecutionEngineTest {

    CodeExecutionEngine engine = new GraalPythonExecutionEngine();

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
}