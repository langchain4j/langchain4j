package dev.langchain4j.agentic;

import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.service.V;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.agentic.Models.baseModel;
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

    public interface RootA2AClientAgent {
        @A2AClientAgent(a2aServerUrl = A2A_SERVER_URL, outputKey = "story")
        String generateStory(@V("topic") String topic);
    }

    /**
     * Regression test for issue #5310: an interface annotated only with
     * {@code @A2AClientAgent} should be a valid root of an agentic system
     * when passed to {@link AgenticServices#createAgenticSystem(Class, dev.langchain4j.model.chat.ChatModel)}.
     * It must be routed through the A2AService SPI (which throws
     * {@link UnsupportedOperationException} when the A2A module is missing),
     * not fall through to the AgentBuilder path that requires {@code @Agent}.
     */
    @Test
    void createAgenticSystem_should_accept_a2aClientAgent_root() {
        assertThat(
                assertThrows(UnsupportedOperationException.class,
                        () -> AgenticServices.createAgenticSystem(RootA2AClientAgent.class, baseModel()))
        ).hasMessageContaining("langchain4j-agentic-a2a");
    }
}
