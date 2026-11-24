package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.planner.AgentInstance;
import java.util.List;
import java.util.Map;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.junit.jupiter.api.Test;

class A2AActivationConditionTest {

    private static AgentCard agentCard() {
        return AgentCard.builder()
                .name("remote")
                .description("A remote agent")
                .version("1.0.0")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of())
                .build();
    }

    @Test
    void builder_sets_the_condition_and_the_remote_agent_is_not_called_when_it_is_false() {
        Client client = mock(Client.class);
        UntypedAgent remote = new DefaultA2AClientBuilder<>(agentCard(), UntypedAgent.class, client)
                .inputKeys("question")
                .outputKey("answer")
                .activationCondition("the question is for the remote agent", scope -> false)
                .build();

        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(remote)
                .outputKey("answer")
                .build();

        assertThat(((AgentInstance) sequence)
                        .subagents()
                        .get(0)
                        .activationCondition()
                        .description())
                .isEqualTo("the question is for the remote agent");
        assertThat(sequence.invoke(Map.of("question", "anything?"))).isNull();
        verifyNoInteractions(client);
    }
}
