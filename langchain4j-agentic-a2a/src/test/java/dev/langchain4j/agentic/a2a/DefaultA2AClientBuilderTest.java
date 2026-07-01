package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AClientTransportSupplier;
import dev.langchain4j.service.V;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.a2aproject.sdk.A2A;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientBuilder;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransportConfigBuilder;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Artifact;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.Part;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskState;
import org.a2aproject.sdk.spec.TaskStatus;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DefaultA2AClientBuilderTest {

    @Test
    void completeFromTask_failedTaskWithReason_completesExceptionally() {
        Message failureMessage = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart("upstream model unavailable")))
                .build();
        Task failedTask = Task.builder()
                .id("task-123")
                .contextId("ctx-1")
                .status(new TaskStatus(TaskState.TASK_STATE_FAILED, failureMessage, null))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(failedTask, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .hasCauseInstanceOf(RuntimeException.class)
                .hasMessageContaining("task-123")
                .hasMessageContaining("TASK_STATE_FAILED")
                .hasMessageContaining("upstream model unavailable");
    }

    @Test
    void completeFromTask_failedTaskWithoutReason_completesExceptionally() {
        Task failedTask = Task.builder()
                .id("task-456")
                .contextId("ctx-2")
                .status(new TaskStatus(TaskState.TASK_STATE_CANCELED))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(failedTask, future);

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get).hasMessageContaining("task-456").hasMessageContaining("TASK_STATE_CANCELED");
    }

    @Test
    void completeFromTask_completedTaskWithArtifact_completesNormally() throws Exception {
        Artifact artifact = Artifact.builder()
                .artifactId("artifact-1")
                .parts(List.<Part<?>>of(new TextPart("the answer")))
                .build();
        Task completedTask = Task.builder()
                .id("task-789")
                .contextId("ctx-3")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of(artifact))
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(completedTask, future);

        assertThat(future).isCompleted();
        assertThat(future.get()).isEqualTo("the answer");
    }

    @Test
    void completeFromTask_completedTaskWithEmptyArtifacts_completesNormallyWithEmptyString() throws Exception {
        Task completedTask = Task.builder()
                .id("task-000")
                .contextId("ctx-4")
                .status(new TaskStatus(TaskState.TASK_STATE_COMPLETED))
                .artifacts(List.of())
                .build();

        CompletableFuture<String> future = new CompletableFuture<>();
        DefaultA2AClientBuilder.completeFromTask(completedTask, future);

        assertThat(future).isCompleted();
        assertThat(future.get()).isEmpty();
    }

    @Test
    void transportConfigurer_factory_appliesTransportAndConfigBuilderToClientBuilder() {
        ClientBuilder clientBuilder = mock(ClientBuilder.class);
        JSONRPCTransportConfigBuilder transportConfigBuilder = new JSONRPCTransportConfigBuilder();

        A2AClientTransportConfigurer configurer =
                A2AClientTransportConfigurer.transport(JSONRPCTransport.class, transportConfigBuilder);
        configurer.configure(clientBuilder);

        verify(clientBuilder).withTransport(JSONRPCTransport.class, transportConfigBuilder);
    }

    @Test
    void transportConfigurer_factory_rejectsNullArguments() {
        assertThatThrownBy(() -> A2AClientTransportConfigurer.transport(null, new JSONRPCTransportConfigBuilder()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transportClass");

        assertThatThrownBy(() -> A2AClientTransportConfigurer.transport(JSONRPCTransport.class, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transportConfigBuilder");
    }

    @Test
    void buildClient_withoutConfigurer_usesDefaultJsonRpcTransport() {
        try (MockedStatic<Client> clientStatic = mockStatic(Client.class)) {
            ClientBuilder clientBuilder = mock(ClientBuilder.class);
            Client client = mock(Client.class);
            clientStatic.when(() -> Client.builder(any())).thenReturn(clientBuilder);
            when(clientBuilder.build()).thenReturn(client);

            Client result = DefaultA2AClientBuilder.buildClient(null, null);

            assertThat(result).isSameAs(client);
            verify(clientBuilder).withTransport(eq(JSONRPCTransport.class), any(JSONRPCTransportConfigBuilder.class));
            verify(clientBuilder).build();
        }
    }

    @Test
    void buildClient_withConfigurer_appliesConfigurerInsteadOfDefault() {
        try (MockedStatic<Client> clientStatic = mockStatic(Client.class)) {
            ClientBuilder clientBuilder = mock(ClientBuilder.class);
            Client client = mock(Client.class);
            clientStatic.when(() -> Client.builder(any())).thenReturn(clientBuilder);
            when(clientBuilder.build()).thenReturn(client);

            AtomicBoolean configured = new AtomicBoolean(false);
            A2AClientTransportConfigurer configurer = builder -> {
                assertThat(builder).isSameAs(clientBuilder);
                configured.set(true);
            };

            Client result = DefaultA2AClientBuilder.buildClient(null, configurer);

            assertThat(result).isSameAs(client);
            assertThat(configured).isTrue();
            // the default JSON-RPC transport must not be applied when a configurer is supplied
            verify(clientBuilder, never())
                    .withTransport(eq(JSONRPCTransport.class), any(JSONRPCTransportConfigBuilder.class));
            verify(clientBuilder).build();
        }
    }

    private static AgentCard minimalAgentCard(String name) {
        return AgentCard.builder()
                .name(name)
                .description("test agent")
                .version("1.0")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost:1234")))
                .build();
    }

    @Test
    void clientTransport_rejectsUnexpectedTypeAndAcceptsNull() {
        try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class)) {
            AgentCard agentCard = minimalAgentCard("test-agent");
            a2aStatic.when(() -> A2A.getAgentCard(anyString())).thenReturn(agentCard);

            DefaultA2AClientBuilder<UntypedAgent> builder =
                    new DefaultA2AClientBuilder<>("http://localhost:1234", UntypedAgent.class);

            assertThatThrownBy(() -> builder.clientTransport("not a configurer"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("A2AClientTransportConfigurer");

            assertThat(builder.clientTransport(null)).isSameAs(builder);
        }
    }

    static final AtomicReference<ClientBuilder> DECLARATIVE_CONFIGURED_BUILDER = new AtomicReference<>();

    public interface TransportConfiguredAgent {

        @A2AClientAgent(a2aServerUrl = "http://localhost:1234", outputKey = "response")
        String ask(@V("question") String question);

        @A2AClientTransportSupplier
        static A2AClientTransportConfigurer transport() {
            return DECLARATIVE_CONFIGURED_BUILDER::set;
        }
    }

    @Test
    void declarativeAgent_appliesTransportConfigurerFromSupplier() {
        DECLARATIVE_CONFIGURED_BUILDER.set(null);
        try (MockedStatic<A2A> a2aStatic = mockStatic(A2A.class);
                MockedStatic<Client> clientStatic = mockStatic(Client.class)) {
            AgentCard agentCard = minimalAgentCard("declarative-agent");
            a2aStatic.when(() -> A2A.getAgentCard(anyString())).thenReturn(agentCard);
            ClientBuilder clientBuilder = mock(ClientBuilder.class);
            clientStatic.when(() -> Client.builder(any())).thenReturn(clientBuilder);
            when(clientBuilder.build()).thenReturn(mock(Client.class));

            AgenticServices.createAgenticSystem(TransportConfiguredAgent.class);

            // the configurer returned by the @A2AClientTransportSupplier method must be applied to the client builder
            assertThat(DECLARATIVE_CONFIGURED_BUILDER.get()).isSameAs(clientBuilder);
            // and the default JSON-RPC transport must not be applied when a supplier is present
            verify(clientBuilder, never())
                    .withTransport(eq(JSONRPCTransport.class), any(JSONRPCTransportConfigBuilder.class));
        }
    }
}
