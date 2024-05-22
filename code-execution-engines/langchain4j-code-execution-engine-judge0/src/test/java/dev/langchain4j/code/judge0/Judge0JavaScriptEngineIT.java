package dev.langchain4j.code.judge0;

import dev.langchain4j.code.CodeExecutionEngine;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class Judge0JavaScriptEngineIT {

    private static final int JAVASCRIPT = 93;

    @Test
    void should_execute_code() {

        // given
        CodeExecutionEngine codeExecutionEngine = new Judge0JavaScriptEngine(
                System.getenv("RAPID_API_KEY"),
                JAVASCRIPT,
                Duration.ofSeconds(60)
        );

        // when
        String result = codeExecutionEngine.execute("console.log('hello world');");

        // then
        assertThat(result).isEqualTo("hello world");
    }
}