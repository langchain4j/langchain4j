package dev.langchain4j.code.judge0;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.code.CodeExecutionEngine;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for the Judge0JavaScriptEngine.
 *
 * Note: These tests require a valid RAPID_API_KEY environment variable to be set.
 */
@EnabledIfEnvironmentVariable(named = "RAPID_API_KEY", matches = ".+")
class Judge0JavaScriptEngineIT {

    private static final Duration TEST_TIMEOUT = Duration.ofSeconds(60);
    private CodeExecutionEngine codeExecutionEngine;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("RAPID_API_KEY");
        codeExecutionEngine =
                new Judge0JavaScriptEngine(apiKey, Judge0JavaScriptExecutionTool.JAVASCRIPT, TEST_TIMEOUT);
    }

    @Test
    void should_execute_simple_code() {
        // when
        String result = codeExecutionEngine.execute("console.log('hello world');");

        // then
        assertThat(result).isEqualTo("hello world");
    }

    @Test
    void should_execute_mathematical_calculations() {
        // when
        String result =
                codeExecutionEngine.execute("function calculateCompoundInterest(principal, rate, time, compounds) {\n"
                        + "    const amount = principal * Math.pow(1 + rate/compounds, compounds * time);\n"
                        + "    const interest = amount - principal;\n"
                        + "    return { principal, amount, interest };\n"
                        + "}\n\n"
                        + "const result = calculateCompoundInterest(1000, 0.05, 5, 12);\n"
                        + "console.log(JSON.stringify(result));");

        assertThat(result)
                .contains("\"principal\":1000")
                .contains("\"amount\":1283.3586785035118")
                .contains("\"interest\":283.3586785035118");
    }
}
