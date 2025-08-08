package dev.langchain4j.agentic;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class A2AAgentIT {

    private static final String A2A_SERVER_URL = "http://localhost:8080";

    @Test
    void a2a_agent_loop_tests() {
        assertThat(
                assertThrows(UnsupportedOperationException.class,
                        () -> AgenticServices.a2aBuilder(A2A_SERVER_URL))
        ).hasMessageContaining("langchain4j-agentic-a2a");
    }
}
