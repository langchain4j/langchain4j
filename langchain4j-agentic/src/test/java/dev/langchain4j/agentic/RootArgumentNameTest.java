package dev.langchain4j.agentic;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentRequest;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RootArgumentNameTest {

    public static class Categorizer {

        @Agent(outputKey = "category")
        public String categorize(@V("request") String request) {
            return "category of " + request;
        }
    }

    public interface NamedArgumentWorkflow {

        @Agent(outputKey = "category")
        String process(@P(name = "request") String userRequest);
    }

    public interface DescribedArgumentWorkflow {

        @Agent(outputKey = "category")
        String process(@V("request") @P("The request to categorize") String userRequest);
    }

    static class InputKeyRecordingListener implements AgentListener {

        private final List<Map<String, Object>> inputs = new ArrayList<>();

        @Override
        public void beforeAgentInvocation(AgentRequest agentRequest) {
            inputs.add(agentRequest.inputs());
        }

        Map<String, Object> firstInputs() {
            return inputs.get(0);
        }
    }

    @Test
    void root_argument_is_readable_under_the_name_declared_by_p() {
        NamedArgumentWorkflow workflow = AgenticServices.sequenceBuilder(NamedArgumentWorkflow.class)
                .subAgents(new Categorizer())
                .outputKey("category")
                .build();

        assertThat(workflow.process("refund")).isEqualTo("category of refund");
    }

    @Test
    void root_argument_keeps_the_v_name_when_p_only_carries_a_description() {
        DescribedArgumentWorkflow workflow = AgenticServices.sequenceBuilder(DescribedArgumentWorkflow.class)
                .subAgents(new Categorizer())
                .outputKey("category")
                .build();

        assertThat(workflow.process("refund")).isEqualTo("category of refund");
    }

    @Test
    void workflow_listener_receives_the_root_argument_named_by_p() {
        InputKeyRecordingListener listener = new InputKeyRecordingListener();
        NamedArgumentWorkflow workflow = AgenticServices.sequenceBuilder(NamedArgumentWorkflow.class)
                .subAgents(new Categorizer())
                .listener(listener)
                .outputKey("category")
                .build();

        workflow.process("refund");

        assertThat(listener.firstInputs()).containsExactly(Map.entry("request", "refund"));
    }
}
