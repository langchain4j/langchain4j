package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import dev.langchain4j.agentic.observability.A2AStreamingClientListenerResult;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.service.V;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.client.TaskEvent;
import org.a2aproject.sdk.client.TaskUpdateEvent;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TaskStatusUpdateEvent;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for {@link DefaultA2AClientBuilder#invoke(Object, Method, Object[])}: the proxy
 * call, the outgoing {@code Message}, the event consumption and the scope write-back, all running
 * in-process against a mocked A2A {@link Client}.
 *
 * <p>These tests pin the stateless contract introduced for issue #6371: the response taskId is no
 * longer written back into the {@code AgenticScope}, so repeating an invocation of the same agent
 * starts a fresh task instead of sending a message to a task that has already reached a terminal
 * state. The contextId write-back is intentionally kept (it identifies the conversation, and
 * reusing it across tasks is legitimate multi-turn behaviour), so it is pinned here as well to
 * protect it from being dropped later.
 */
class DefaultA2AClientBuilderInvokeTest {

    interface EchoAgent {
        ResultWithAgenticScope<String> echo(
                @V("question") String question,
                @A2AContextId @V("contextId") String contextId,
                @A2ATaskId @V("taskId") String taskId);
    }

    private record MockedClient(Client client, List<Message> sentMessages) {}

    private static AgentCard agentCard() {
        return AgentCard.builder()
                .name("echo")
                .description("Echo agent for stateless contract tests")
                .version("1.0.0")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of())
                .build();
    }

    /**
     * A mocked {@link Client} that captures every outgoing message and, on each invocation, replays
     * the next queued event through the consumer list the builder registered, synchronously.
     */
    private static MockedClient clientReplaying(ClientEvent... events) {
        AgentCard card = agentCard();
        List<Message> sentMessages = new ArrayList<>();
        Queue<ClientEvent> eventQueue = new ConcurrentLinkedQueue<>(List.of(events));
        Client client = mock(Client.class);
        doAnswer(invocation -> {
                    sentMessages.add(invocation.getArgument(0, Message.class));
                    List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                    ClientEvent event = eventQueue.poll();
                    if (event != null) {
                        consumers.get(0).accept(event, card);
                    }
                    return null;
                })
                .when(client)
                .sendMessage(any(Message.class), anyList(), any(), any());
        return new MockedClient(client, sentMessages);
    }

    private static DefaultA2AClientBuilder<EchoAgent> builder(Client client) {
        return new DefaultA2AClientBuilder<>(agentCard(), EchoAgent.class, client).outputKey("response");
    }

    private static ClientEvent completedTaskEvent(String taskId, String contextId, String answer) {
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-" + taskId)
                .parts(List.<Part<?>>of(new TextPart(answer)))
                .build();
        return new TaskEvent(
                task(taskId, contextId, new TaskStatus(TaskState.TASK_STATE_COMPLETED), List.of(artifact)));
    }

    private static ClientEvent taskEvent(String taskId, String contextId, TaskState state, String reason) {
        Message statusMessage = reason == null
                ? null
                : Message.builder()
                        .role(Message.Role.ROLE_AGENT)
                        .parts(List.of(new TextPart(reason)))
                        .build();
        return new TaskEvent(task(taskId, contextId, new TaskStatus(state, statusMessage, null), List.of()));
    }

    private static Task task(String id, String contextId, TaskStatus status, List<Artifact> artifacts) {
        return Task.builder()
                .id(id)
                .contextId(contextId)
                .status(status)
                .artifacts(artifacts)
                .build();
    }

    @Test
    void repeated_invocations_do_not_reuse_the_task_id_of_completed_tasks() {
        MockedClient mocked = clientReplaying(
                completedTaskEvent("task-1", "ctx-1", "answer-1"), completedTaskEvent("task-2", "ctx-2", "answer-2"));
        EchoAgent agent = builder(mocked.client()).build();

        ResultWithAgenticScope<String> first = agent.echo("question-1", null, null);
        ResultWithAgenticScope<String> second = agent.echo("question-2", null, null);

        // #6371: the old implementation wrote the response taskId back into the scope under the
        // @V key, so repeating the same agent sent the completed task's id again and the A2A
        // server rejected it. No taskId may land in the scope now, and no outgoing message may
        // carry one unless the caller passed it explicitly.
        assertThat(mocked.sentMessages()).hasSize(2);
        assertThat(mocked.sentMessages().get(0).taskId()).isNull();
        assertThat(mocked.sentMessages().get(1).taskId()).isNull();
        assertThat(first.agenticScope().readState("taskId")).isNull();
        assertThat(second.agenticScope().readState("taskId")).isNull();

        assertThat(first.result()).isEqualTo("answer-1");
        assertThat(second.result()).isEqualTo("answer-2");
    }

    @Test
    void context_id_write_back_is_preserved() {
        MockedClient mocked = clientReplaying(completedTaskEvent("task-1", "ctx-1", "answer-1"));
        EchoAgent agent = builder(mocked.client()).build();

        ResultWithAgenticScope<String> result = agent.echo("question", null, null);

        // The asymmetric contract agreed on #6371: the taskId write-back is gone, the contextId
        // write-back stays (a context identifies a conversation, and reusing it across tasks is
        // legitimate multi-turn behaviour per the A2A spec).
        assertThat(result.agenticScope().readState("contextId")).isEqualTo("ctx-1");
    }

    @Test
    void explicit_a2a_task_id_reaches_the_outgoing_message() {
        MockedClient mocked = clientReplaying(completedTaskEvent("task-2", "ctx-2", "answer-2"));
        EchoAgent agent = builder(mocked.client()).build();

        agent.echo("question", "ctx-manual", "task-manual");

        // Callers that intentionally continue an existing task (e.g. a genuinely interrupted one)
        // pass the ids explicitly; they must reach the outgoing message untouched.
        Message outgoing = mocked.sentMessages().get(0);
        assertThat(outgoing.taskId()).isEqualTo("task-manual");
        assertThat(outgoing.contextId()).isEqualTo("ctx-manual");
        assertThat(outgoing.parts()).containsExactly(new TextPart("question"));
        assertThat(mocked.sentMessages()).hasSize(1);
    }

    @Test
    void input_required_task_interrupts_the_invocation() {
        MockedClient mocked = clientReplaying(
                taskEvent("task-i1", "ctx-i1", TaskState.TASK_STATE_INPUT_REQUIRED, "What is your email address?"));
        EchoAgent agent = builder(mocked.client()).build();

        assertThatThrownBy(() -> agent.echo("question", null, null))
                .isInstanceOf(A2ATaskInterruptedException.class)
                .satisfies(t -> {
                    A2ATaskInterruptedException interruption = (A2ATaskInterruptedException) t;
                    assertThat(interruption.taskId()).isEqualTo("task-i1");
                    assertThat(interruption.contextId()).isEqualTo("ctx-i1");
                    assertThat(interruption.state()).isEqualTo(TaskState.TASK_STATE_INPUT_REQUIRED);
                    assertThat(interruption.reason()).isEqualTo("What is your email address?");
                });
    }

    @Test
    void failed_task_surfaces_the_server_side_failure() {
        MockedClient mocked = clientReplaying(
                taskEvent("task-f1", "ctx-f1", TaskState.TASK_STATE_FAILED, "upstream model unavailable"));
        EchoAgent agent = builder(mocked.client()).build();

        assertThatThrownBy(() -> agent.echo("question", null, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("task-f1")
                .hasMessageContaining("TASK_STATE_FAILED")
                .hasMessageContaining("upstream model unavailable");
    }

    @Test
    void message_only_response_completes_without_a_task() {
        MockedClient mocked = clientReplaying(new MessageEvent(Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("direct answer")))
                .contextId("ctx-m1")
                .build()));
        EchoAgent agent = builder(mocked.client()).build();

        ResultWithAgenticScope<String> result = agent.echo("question", null, null);

        assertThat(result.result()).isEqualTo("direct answer");
        assertThat(result.agenticScope().readState("contextId")).isEqualTo("ctx-m1");
        assertThat(result.agenticScope().readState("taskId")).isNull();
    }

    @Test
    void streaming_client_listener_stops_the_stream_and_returns_its_response() {
        AgentCard card = agentCard();
        Client client = mock(Client.class);
        doAnswer(invocation -> {
                    List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                    Task task = task("task-s1", "ctx-s1", new TaskStatus(TaskState.TASK_STATE_WORKING), List.of());
                    consumers
                            .get(0)
                            .accept(
                                    new TaskUpdateEvent(
                                            task,
                                            new TaskStatusUpdateEvent(
                                                    "task-s1",
                                                    new TaskStatus(TaskState.TASK_STATE_WORKING),
                                                    "ctx-s1",
                                                    Map.of())),
                                    card);
                    return null;
                })
                .when(client)
                .sendMessage(any(Message.class), anyList(), any(), any());

        EchoAgent agent = new DefaultA2AClientBuilder<>(agentCard(), EchoAgent.class, client)
                .outputKey("response")
                .streamingClientListener(
                        (TaskUpdateEvent event) -> event.getTask().status().state() == TaskState.TASK_STATE_WORKING
                                ? A2AStreamingClientListenerResult.stopWithResponse("stopped early")
                                : A2AStreamingClientListenerResult.continueStreaming())
                .build();

        ResultWithAgenticScope<String> result = agent.echo("question", null, null);

        assertThat(result.result()).isEqualTo("stopped early");
    }
}
