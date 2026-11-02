package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

class A2ADetachedAgentTest {

    private static AgentCard agentCard() {
        return AgentCard.builder()
                .name("remediate")
                .description("A remote coding agent")
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
    void root_returns_without_waiting_for_a_detached_remote_agent() throws InterruptedException {
        CountDownLatch taskCompleted = new CountDownLatch(1);
        RemediationListener listener = new RemediationListener();
        UntypedAgent remediator = new DefaultA2AClientBuilder<>(
                        agentCard(), UntypedAgent.class, clientCompletingAfter(taskCompleted))
                .inputKeys("metrics")
                .outputKey("remediation")
                .detached(true)
                .listener(listener)
                .build();
        UntypedAgent rollout = AgenticServices.sequenceBuilder()
                .subAgents(new Analyst(), remediator)
                .outputKey("decision")
                .build();

        assertThat(((AgentInstance) rollout).subagents().get(1).detached()).isTrue();

        assertThat(rollout.invoke(Map.of("metrics", "error rate 12%"))).isEqualTo("rollback");
        assertThat(listener.remediations).isEmpty();

        taskCompleted.countDown();

        assertThat(listener.remediations.poll(10, TimeUnit.SECONDS)).isEqualTo("PR #42 opened");
    }

    public static class Analyst {

        @Agent(outputKey = "decision")
        public String analyze(@V("metrics") String metrics) {
            return "rollback";
        }
    }

    static class RemediationListener implements AgentListener {

        final LinkedBlockingQueue<Object> remediations = new LinkedBlockingQueue<>();

        @Override
        public void afterAgentInvocation(AgentResponse agentResponse) {
            if ("remediate".equals(agentResponse.agentName())) {
                remediations.add(agentResponse.output());
            }
        }
    }

    /**
     * A remote agent that completes its task only once the latch is released.
     */
    private static Client clientCompletingAfter(CountDownLatch taskCompleted) {
        Client client = mock(Client.class);
        doAnswer(new Answer<Void>() {
                    @Override
                    public Void answer(InvocationOnMock invocation) throws InterruptedException {
                        assertThat(taskCompleted.await(10, TimeUnit.SECONDS)).isTrue();
                        List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                        consumers.get(0).accept(completedTask("PR #42 opened"), agentCard());
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
