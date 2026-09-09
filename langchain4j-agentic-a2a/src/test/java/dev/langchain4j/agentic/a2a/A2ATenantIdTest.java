package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.service.V;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.BiConsumer;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class A2ATenantIdTest {

    // @A2ATenantId uses @V("tenant") so it can flow from the agentic scope (like @A2AContextId)
    interface TenantAwareAgent {
        String chat(@V("question") String question, @A2ATenantId @V("tenant") String tenant);
    }

    interface ContextAndTenantAgent {
        String chat(
                @V("question") String question,
                @A2AContextId @V("contextId") String contextId,
                @A2ATenantId @V("tenant") String tenant);
    }

    private AgentCard agentCard;

    @BeforeEach
    void setUp() {
        agentCard = AgentCard.builder()
                .name("test-agent")
                .description("Test agent")
                .version("1.0.0")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of())
                .build();
    }

    // --- A2AClientAgentInvoker argument tests ---

    @Test
    void tenantId_parameter_is_optional_in_invoker_arguments() throws NoSuchMethodException {
        A2AClientInstance clientInstance = mock(A2AClientInstance.class);
        when(clientInstance.agentCard()).thenReturn(agentCard);

        Method chatMethod = TenantAwareAgent.class.getMethod("chat", String.class, String.class);
        A2AClientAgentInvoker invoker = new A2AClientAgentInvoker(clientInstance, chatMethod);

        List<AgentArgument> args = invoker.arguments();

        // tenant is optional — present in args so the planner can pass it from scope, but not required
        assertThat(args).hasSize(2);
        assertThat(args.get(0).name()).isEqualTo("question");
        assertThat(args.get(0).isOptional()).isFalse();
        assertThat(args.get(1).name()).isEqualTo("tenant");
        assertThat(args.get(1).isOptional()).isTrue();
    }

    @Test
    void contextId_and_tenantId_parameters_are_both_optional_in_invoker_arguments() throws NoSuchMethodException {
        A2AClientInstance clientInstance = mock(A2AClientInstance.class);
        when(clientInstance.agentCard()).thenReturn(agentCard);

        Method chatMethod = ContextAndTenantAgent.class.getMethod("chat", String.class, String.class, String.class);
        A2AClientAgentInvoker invoker = new A2AClientAgentInvoker(clientInstance, chatMethod);

        List<AgentArgument> args = invoker.arguments();

        assertThat(args).hasSize(3);
        assertThat(args.get(0).name()).isEqualTo("question");
        assertThat(args.get(0).isOptional()).isFalse();
        assertThat(args.get(1).name()).isEqualTo("contextId");
        assertThat(args.get(1).isOptional()).isTrue();
        assertThat(args.get(2).name()).isEqualTo("tenant");
        assertThat(args.get(2).isOptional()).isTrue();
    }

    // --- DefaultA2AClientBuilder invocation tests ---

    @Test
    void nonNull_tenant_is_passed_via_MessageSendParams() throws Exception {
        Client mockClient = mock(Client.class);
        DefaultA2AClientBuilder<TenantAwareAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, TenantAwareAgent.class, mockClient);

        ArgumentCaptor<MessageSendParams> paramsCaptor = ArgumentCaptor.forClass(MessageSendParams.class);
        doAnswer(invocation -> {
                    List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                    Message response = Message.builder()
                            .role(Message.Role.ROLE_AGENT)
                            .parts(List.of(new TextPart("ok")))
                            .build();
                    consumers.get(0).accept(new MessageEvent(response), agentCard);
                    return null;
                })
                .when(mockClient)
                .sendMessage(any(MessageSendParams.class), anyList(), any(), isNull());

        TenantAwareAgent agent = builder.build();
        String result = agent.chat("hello", "acme-corp");

        verify(mockClient).sendMessage(paramsCaptor.capture(), anyList(), any(), isNull());
        verify(mockClient, never()).sendMessage(any(Message.class), anyList(), any(), isNull());

        MessageSendParams captured = paramsCaptor.getValue();
        assertThat(captured.tenant()).isEqualTo("acme-corp");
        assertThat(captured.message().parts()).hasSize(1);
        assertThat(((TextPart) captured.message().parts().get(0)).text()).isEqualTo("hello");
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void null_tenant_sends_plain_Message_without_MessageSendParams() throws Exception {
        Client mockClient = mock(Client.class);
        DefaultA2AClientBuilder<TenantAwareAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, TenantAwareAgent.class, mockClient);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        doAnswer(invocation -> {
                    List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                    Message response = Message.builder()
                            .role(Message.Role.ROLE_AGENT)
                            .parts(List.of(new TextPart("ok")))
                            .build();
                    consumers.get(0).accept(new MessageEvent(response), agentCard);
                    return null;
                })
                .when(mockClient)
                .sendMessage(any(Message.class), anyList(), any(), isNull());

        TenantAwareAgent agent = builder.build();
        agent.chat("hello", null);

        verify(mockClient).sendMessage(messageCaptor.capture(), anyList(), any(), isNull());
        verify(mockClient, never()).sendMessage(any(MessageSendParams.class), anyList(), any(), isNull());

        Message captured = messageCaptor.getValue();
        assertThat(captured.parts()).hasSize(1);
        assertThat(((TextPart) captured.parts().get(0)).text()).isEqualTo("hello");
    }

    @Test
    void empty_string_tenant_sends_plain_Message_like_null() throws Exception {
        Client mockClient = mock(Client.class);
        DefaultA2AClientBuilder<TenantAwareAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, TenantAwareAgent.class, mockClient);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        doAnswer(invocation -> {
                    List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                    Message response = Message.builder()
                            .role(Message.Role.ROLE_AGENT)
                            .parts(List.of(new TextPart("ok")))
                            .build();
                    consumers.get(0).accept(new MessageEvent(response), agentCard);
                    return null;
                })
                .when(mockClient)
                .sendMessage(any(Message.class), anyList(), any(), isNull());

        TenantAwareAgent agent = builder.build();
        agent.chat("hello", "");

        verify(mockClient).sendMessage(messageCaptor.capture(), anyList(), any(), isNull());
        verify(mockClient, never()).sendMessage(any(MessageSendParams.class), anyList(), any(), isNull());

        Message captured = messageCaptor.getValue();
        assertThat(captured.parts()).hasSize(1);
        assertThat(((TextPart) captured.parts().get(0)).text()).isEqualTo("hello");
    }

    @Test
    void tenant_is_not_included_as_a_message_part() throws Exception {
        Client mockClient = mock(Client.class);
        DefaultA2AClientBuilder<TenantAwareAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, TenantAwareAgent.class, mockClient);

        ArgumentCaptor<MessageSendParams> paramsCaptor = ArgumentCaptor.forClass(MessageSendParams.class);
        doAnswer(invocation -> {
                    List<BiConsumer<ClientEvent, AgentCard>> consumers = invocation.getArgument(1);
                    Message response = Message.builder()
                            .role(Message.Role.ROLE_AGENT)
                            .parts(List.of(new TextPart("ok")))
                            .build();
                    consumers.get(0).accept(new MessageEvent(response), agentCard);
                    return null;
                })
                .when(mockClient)
                .sendMessage(any(MessageSendParams.class), anyList(), any(), isNull());

        TenantAwareAgent agent = builder.build();
        agent.chat("hello", "tenant-xyz");

        verify(mockClient).sendMessage(paramsCaptor.capture(), anyList(), any(), isNull());

        List<?> parts = paramsCaptor.getValue().message().parts();
        assertThat(parts).hasSize(1);
        assertThat(((TextPart) parts.get(0)).text())
                .doesNotContain("tenant-xyz")
                .isEqualTo("hello");
    }
}
