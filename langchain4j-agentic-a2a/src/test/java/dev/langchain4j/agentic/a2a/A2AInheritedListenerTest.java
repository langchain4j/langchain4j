package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentInvocation;
import dev.langchain4j.agentic.observability.AgentMonitor;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class A2AInheritedListenerTest {

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
    void monitor_of_the_root_records_the_remote_subagent() {
        UntypedAgent remote = new DefaultA2AClientBuilder<>(agentCard(), UntypedAgent.class, clientAnswering("42"))
                .inputKeys("question")
                .outputKey("answer")
                .build();
        AgentMonitor monitor = new AgentMonitor();
        UntypedAgent sequence = AgenticServices.sequenceBuilder()
                .subAgents(remote)
                .outputKey("answer")
                .listener(monitor)
                .build();

        assertThat(sequence.invoke(Map.of("question", "meaning of life?"))).isEqualTo("42");

        AgentInvocation root = monitor.successfulExecutions().get(0).topLevelInvocations();
        assertThat(root.nestedInvocations()).hasSize(1);
        AgentInvocation invocation = root.nestedInvocations().get(0);
        assertThat(invocation.agent().name()).isEqualTo("remote");
        assertThat(invocation.output()).isEqualTo("42");
    }

    private static Client clientAnswering(String answer) {
        Client client = mock(Client.class);
        doAnswer(new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) {
                        List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                        consumers.get(0).accept(completedTask(answer), agentCard());
                        return null;
                    }
                })
                .when(client)
                .sendMessage(any(Message.class), anyList(), any(), any());
        return client;
    }

    private static ClientEvent completedTask(String answer) {
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-1")
                .parts(List.<Part<?>>of(new TextPart(answer)))
                .build();
        return new TaskEvent(Task.builder()
                .id("task-1")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .build());
    }
}
