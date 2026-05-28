package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AContextId;
import dev.langchain4j.agentic.declarative.A2ATaskId;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.AgentExecutor;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import io.a2a.client.Client;
import io.a2a.client.MessageEvent;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentSkill;
import io.a2a.spec.Message;
import io.a2a.spec.TextPart;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class A2AClientAgentMessageContextTest {

    private static final AgentCard AGENT_CARD = new AgentCard.Builder()
            .name("test-agent")
            .description("Test agent")
            .url("http://localhost:8080")
            .version("1.0")
            .capabilities(new AgentCapabilities.Builder().build())
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of(new AgentSkill.Builder()
                    .id("test")
                    .name("test")
                    .description("Test skill")
                    .tags(List.of("test"))
                    .build()))
            .build();

    interface TypedA2AAgent {

        String chat(
                @V("question") String question,
                @A2AContextId @V("contextId") String contextId,
                @A2ATaskId @V("taskId") String taskId);
    }

    public interface DeclarativeA2AAgent {

        @A2AClientAgent(
                a2aServerUrl = "http://test-a2a",
                outputKey = "answer",
                contextIdKey = "contextId",
                taskIdKey = "taskId")
        String chat(@V("question") String question);
    }

    public interface DeclarativeA2AAgentWithContextParameters {

        @A2AClientAgent(
                a2aServerUrl = "http://test-a2a",
                outputKey = "answer",
                contextIdKey = "contextId",
                taskIdKey = "taskId")
        String chat(@V("question") String question, @V("contextId") String contextId, @V("taskId") String taskId);
    }

    public interface DeclarativeA2AWorkflow {

        @SequenceAgent(outputKey = "answer", subAgents = DeclarativeA2AAgent.class)
        String chat(@V("question") String question, @V("contextId") String contextId, @V("taskId") String taskId);
    }

    public interface DeclarativeA2AWorkflowWithContextParameters {

        @SequenceAgent(outputKey = "answer", subAgents = DeclarativeA2AAgentWithContextParameters.class)
        String chat(@V("question") String question, @V("contextId") String contextId, @V("taskId") String taskId);
    }

    @Test
    void typed_agent_applies_marked_context_and_task_ids_to_message_envelope() throws Exception {
        Client client = mockClientReturning("ok");

        TypedA2AAgent agent = new DefaultA2AClientBuilder<>(AGENT_CARD, client, TypedA2AAgent.class).build();

        String result = agent.chat("What changed?", "context-1", "task-1");

        assertThat(result).isEqualTo("ok");

        Message message = captureSentMessage(client);
        assertThat(message.getContextId()).isEqualTo("context-1");
        assertThat(message.getTaskId()).isEqualTo("task-1");
        assertThat(message.getParts())
                .singleElement()
                .isInstanceOfSatisfying(
                        TextPart.class, part -> assertThat(part.getText()).isEqualTo("What changed?"));
    }

    @Test
    void typed_agent_allows_null_context_and_task_ids() throws Exception {
        Client client = mockClientReturning("ok");

        TypedA2AAgent agent = new DefaultA2AClientBuilder<>(AGENT_CARD, client, TypedA2AAgent.class).build();

        String result = agent.chat("Start a new task", null, null);

        assertThat(result).isEqualTo("ok");

        Message message = captureSentMessage(client);
        assertThat(message.getContextId()).isNull();
        assertThat(message.getTaskId()).isNull();
        assertThat(message.getParts())
                .singleElement()
                .isInstanceOfSatisfying(
                        TextPart.class, part -> assertThat(part.getText()).isEqualTo("Start a new task"));
    }

    @Test
    void untyped_agent_applies_configured_context_and_task_keys_to_message_envelope() throws Exception {
        Client client = mockClientReturning("ok");

        UntypedAgent agent = new DefaultA2AClientBuilder<>(AGENT_CARD, client, UntypedAgent.class)
                .inputKeys("question", "contextId", "taskId")
                .contextIdKey("contextId")
                .taskIdKey("taskId")
                .build();

        Object result = agent.invoke(Map.of(
                "question", "Continue the task",
                "contextId", "context-1",
                "taskId", "task-1"));

        assertThat(result).isEqualTo("ok");

        Message message = captureSentMessage(client);
        assertThat(message.getContextId()).isEqualTo("context-1");
        assertThat(message.getTaskId()).isEqualTo("task-1");
        assertThat(message.getParts())
                .singleElement()
                .isInstanceOfSatisfying(
                        TextPart.class, part -> assertThat(part.getText()).isEqualTo("Continue the task"));
    }

    @Test
    void untyped_agent_keeps_context_and_task_keys_as_message_parts_when_not_configured() throws Exception {
        Client client = mockClientReturning("ok");

        UntypedAgent agent = new DefaultA2AClientBuilder<>(AGENT_CARD, client, UntypedAgent.class)
                .inputKeys("question", "contextId", "taskId")
                .build();

        agent.invoke(Map.of(
                "question", "Continue the task",
                "contextId", "context-1",
                "taskId", "task-1"));

        Message message = captureSentMessage(client);
        assertThat(message.getContextId()).isNull();
        assertThat(message.getTaskId()).isNull();
        assertThat(message.getParts())
                .filteredOn(TextPart.class::isInstance)
                .map(part -> ((TextPart) part).getText())
                .containsExactly("Continue the task", "context-1", "task-1");
    }

    @Test
    void declarative_agent_applies_configured_context_and_task_keys_from_agentic_scope() throws Exception {
        Client client = mockClientReturning("ok");
        A2AService previousA2AService = replaceA2AService(new TestA2AService(client));
        try {
            DeclarativeA2AWorkflow workflow =
                    AgenticServices.createAgenticSystem(DeclarativeA2AWorkflow.class, (ChatModel) null);

            String result = workflow.chat("Continue the task", "context-1", "task-1");

            assertThat(result).isEqualTo("ok");

            Message message = captureSentMessage(client);
            assertThat(message.getContextId()).isEqualTo("context-1");
            assertThat(message.getTaskId()).isEqualTo("task-1");
            assertThat(message.getParts())
                    .singleElement()
                    .isInstanceOfSatisfying(
                            TextPart.class, part -> assertThat(part.getText()).isEqualTo("Continue the task"));
        } finally {
            replaceA2AService(previousA2AService);
        }
    }

    @Test
    void declarative_agent_keeps_configured_context_and_task_key_parameters_out_of_message_parts() throws Exception {
        Client client = mockClientReturning("ok");
        A2AService previousA2AService = replaceA2AService(new TestA2AService(client));
        try {
            DeclarativeA2AWorkflowWithContextParameters workflow = AgenticServices.createAgenticSystem(
                    DeclarativeA2AWorkflowWithContextParameters.class, (ChatModel) null);

            String result = workflow.chat("Continue the task", "context-1", "task-1");

            assertThat(result).isEqualTo("ok");

            Message message = captureSentMessage(client);
            assertThat(message.getContextId()).isEqualTo("context-1");
            assertThat(message.getTaskId()).isEqualTo("task-1");
            assertThat(message.getParts())
                    .singleElement()
                    .isInstanceOfSatisfying(
                            TextPart.class, part -> assertThat(part.getText()).isEqualTo("Continue the task"));
        } finally {
            replaceA2AService(previousA2AService);
        }
    }

    @Test
    void invoker_keeps_marked_context_and_task_ids_out_of_business_arguments() throws Exception {
        Client client = mockClientReturning("ok");
        TypedA2AAgent agent = new DefaultA2AClientBuilder<>(AGENT_CARD, client, TypedA2AAgent.class).build();
        Method method = TypedA2AAgent.class.getMethod("chat", String.class, String.class, String.class);
        A2AClientAgentInvoker invoker = new A2AClientAgentInvoker((A2AClientInstance) agent, method);
        DefaultAgenticScope agenticScope = DefaultAgenticScope.ephemeralAgenticScope();
        agenticScope.writeState("question", "Continue the task");
        agenticScope.writeState("contextId", "context-1");
        agenticScope.writeState("taskId", "task-1");

        var invocationArguments = invoker.toInvocationArguments(agenticScope);

        assertThat(invoker.arguments()).map(AgentArgument::name).containsExactly("question");
        assertThat(invocationArguments.namedArgs()).containsExactly(Map.entry("question", "Continue the task"));
        assertThat(invocationArguments.positionalArgs()).containsExactly("Continue the task", "context-1", "task-1");
    }

    @SuppressWarnings("unchecked")
    private static Client mockClientReturning(String responseText) throws Exception {
        Client client = mock(Client.class);
        doAnswer(invocation -> {
                    List<BiConsumer<io.a2a.client.ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                    Message response = new Message.Builder()
                            .role(Message.Role.AGENT)
                            .parts(new TextPart(responseText))
                            .build();
                    consumers.forEach(consumer -> consumer.accept(new MessageEvent(response), AGENT_CARD));
                    return null;
                })
                .when(client)
                .sendMessage(any(Message.class), anyList(), any(Consumer.class));
        return client;
    }

    private static A2AService replaceA2AService(A2AService a2aService) throws Exception {
        Field field = Class.forName("dev.langchain4j.agentic.internal.A2AService$Provider")
                .getDeclaredField("a2aService");
        field.setAccessible(true);
        A2AService previousA2AService = (A2AService) field.get(null);
        field.set(null, a2aService);
        return previousA2AService;
    }

    private static Message captureSentMessage(Client client) throws Exception {
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(client).sendMessage(messageCaptor.capture(), anyList(), any(Consumer.class));
        return messageCaptor.getValue();
    }

    private static class TestA2AService implements A2AService {

        private final Client client;

        TestA2AService(Client client) {
            this.client = client;
        }

        @Override
        public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, Class<T> agentServiceClass) {
            assertThat(a2aServerUrl).isEqualTo("http://test-a2a");
            return new DefaultA2AClientBuilder<>(AGENT_CARD, client, agentServiceClass);
        }

        @Override
        public Optional<AgentExecutor> methodToAgentExecutor(InternalAgent a2aClient, Method method) {
            return new DefaultA2AService().methodToAgentExecutor(a2aClient, method);
        }
    }
}
