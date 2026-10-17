package dev.langchain4j.agentic.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HumanInTheLoopBuilderTest {

    @Test
    void multiple_listeners_are_composed() {
        List<String> notifiedListeners = new ArrayList<>();

        HumanInTheLoop humanInTheLoop = AgenticServices.humanInTheLoopBuilder()
                .responseProvider(() -> "approved")
                .outputKey("approval")
                .listener(notifyingListener("first", notifiedListeners))
                .listener(notifyingListener("second", notifiedListeners))
                .build();

        Object result = AgenticServices.sequenceBuilder()
                .subAgents(humanInTheLoop)
                .outputKey("approval")
                .build()
                .invoke(Map.of());

        assertThat(result).isEqualTo("approved");
        // ComposedAgentListener doesn't guarantee any notification order
        assertThat(notifiedListeners).containsExactlyInAnyOrder("first", "second");
    }

    private static AgentListener notifyingListener(String name, List<String> notifiedListeners) {
        return new AgentListener() {
            @Override
            public void beforeAgentInvocation(AgentRequest agentRequest) {
                notifiedListeners.add(name);
            }
        };
    }
}
