package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.langchain4j.service.V;
import java.util.List;
import java.util.function.BiConsumer;
import org.a2aproject.sdk.client.Client;
import org.a2aproject.sdk.client.ClientEvent;
import org.a2aproject.sdk.client.MessageEvent;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests for the static {@code tenant} attribute on {@code @A2AClientAgent} and
 * tenant auto-detection from the agent card's {@code supportedInterfaces}.
 */
class A2AAnnotationTenantTest {

    // Interface without @A2ATenantId — tenant must come from static builder configuration.
    interface StaticTenantAgent {
        String chat(@V("question") String question);
    }

    // Interface with both static builder tenant and @A2ATenantId — the latter overrides when non-empty.
    interface OverrideTenantAgent {
        String chat(@V("question") String question, @A2ATenantId String tenant);
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

    // ── extractTenantFromAgentCard ──────────────────────────────────────────

    @Test
    void extractTenantFromAgentCard_nullAgentCard_returnsNull() {
        assertThat(DefaultA2AClientBuilder.extractTenantFromAgentCard(null)).isNull();
    }

    @Test
    void extractTenantFromAgentCard_emptySupportedInterfaces_returnsNull() {
        assertThat(DefaultA2AClientBuilder.extractTenantFromAgentCard(agentCard)).isNull();
    }

    @Test
    void extractTenantFromAgentCard_interfaceWithTenant_returnsTenant() {
        AgentCard card = AgentCard.builder()
                .name("a")
                .description("d")
                .version("1")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost", "my-tenant")))
                .build();
        assertThat(DefaultA2AClientBuilder.extractTenantFromAgentCard(card)).isEqualTo("my-tenant");
    }

    @Test
    void extractTenantFromAgentCard_firstNonEmptyTenantWins() {
        AgentCard card = AgentCard.builder()
                .name("a")
                .description("d")
                .version("1")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(
                        new AgentInterface("JSONRPC", "http://localhost"),
                        new AgentInterface("JSONRPC", "http://localhost", "first-tenant"),
                        new AgentInterface("JSONRPC", "http://localhost", "second-tenant")))
                .build();
        assertThat(DefaultA2AClientBuilder.extractTenantFromAgentCard(card)).isEqualTo("first-tenant");
    }

    @Test
    void extractTenantFromAgentCard_onlyEmptyTenants_returnsNull() {
        AgentCard card = AgentCard.builder()
                .name("a")
                .description("d")
                .version("1")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(
                        new AgentInterface("JSONRPC", "http://localhost"),
                        new AgentInterface("JSONRPC", "http://localhost", "")))
                .build();
        assertThat(DefaultA2AClientBuilder.extractTenantFromAgentCard(card)).isNull();
    }

    @Test
    void staticTenant_isAppliedToEveryCallViaMessageSendParams() throws Exception {
        Client mockClient = mockClientReturning("ok");
        DefaultA2AClientBuilder<StaticTenantAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, StaticTenantAgent.class, mockClient);
        builder.tenant("acme");

        StaticTenantAgent agent = builder.build();
        agent.chat("hello");

        ArgumentCaptor<MessageSendParams> captor = ArgumentCaptor.forClass(MessageSendParams.class);
        verify(mockClient).sendMessage(captor.capture(), anyList(), any(), isNull());
        assertThat(captor.getValue().tenant()).isEqualTo("acme");
        assertThat(((TextPart) captor.getValue().message().parts().get(0)).text()).isEqualTo("hello");
    }

    @Test
    void staticTenant_isNotIncludedAsMessagePart() throws Exception {
        Client mockClient = mockClientReturning("ok");
        DefaultA2AClientBuilder<StaticTenantAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, StaticTenantAgent.class, mockClient);
        builder.tenant("acme");

        builder.build().chat("hello");

        ArgumentCaptor<MessageSendParams> captor = ArgumentCaptor.forClass(MessageSendParams.class);
        verify(mockClient).sendMessage(captor.capture(), anyList(), any(), isNull());
        List<?> parts = captor.getValue().message().parts();
        assertThat(parts).hasSize(1);
        assertThat(((TextPart) parts.get(0)).text()).doesNotContain("acme").isEqualTo("hello");
    }

    @Test
    void staticTenant_blankValue_isIgnoredAndNoTenantSent() throws Exception {
        Client mockClient = mockClientReturning("ok");
        DefaultA2AClientBuilder<StaticTenantAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, StaticTenantAgent.class, mockClient);
        builder.tenant("");

        builder.build().chat("hello");

        verify(mockClient).sendMessage(any(Message.class), anyList(), any(), isNull());
        verify(mockClient, never()).sendMessage(any(MessageSendParams.class), anyList(), any(), isNull());
    }

    @Test
    void staticTenant_overriddenByNonEmptyDynamicTenantId() throws Exception {
        Client mockClient = mockClientReturning("ok");
        DefaultA2AClientBuilder<OverrideTenantAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, OverrideTenantAgent.class, mockClient);
        builder.tenant("static-tenant");

        builder.build().chat("hello", "dynamic-tenant");

        ArgumentCaptor<MessageSendParams> captor = ArgumentCaptor.forClass(MessageSendParams.class);
        verify(mockClient).sendMessage(captor.capture(), anyList(), any(), isNull());
        assertThat(captor.getValue().tenant()).isEqualTo("dynamic-tenant");
    }

    @Test
    void staticTenant_usedWhenDynamicTenantIdIsNull() throws Exception {
        Client mockClient = mockClientReturning("ok");
        DefaultA2AClientBuilder<OverrideTenantAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, OverrideTenantAgent.class, mockClient);
        builder.tenant("static-tenant");

        builder.build().chat("hello", null);

        ArgumentCaptor<MessageSendParams> captor = ArgumentCaptor.forClass(MessageSendParams.class);
        verify(mockClient).sendMessage(captor.capture(), anyList(), any(), isNull());
        assertThat(captor.getValue().tenant()).isEqualTo("static-tenant");
    }

    @Test
    void staticTenant_usedWhenDynamicTenantIdIsEmpty() throws Exception {
        Client mockClient = mockClientReturning("ok");
        DefaultA2AClientBuilder<OverrideTenantAgent> builder =
                new DefaultA2AClientBuilder<>(agentCard, OverrideTenantAgent.class, mockClient);
        builder.tenant("static-tenant");

        builder.build().chat("hello", "");

        ArgumentCaptor<MessageSendParams> captor = ArgumentCaptor.forClass(MessageSendParams.class);
        verify(mockClient).sendMessage(captor.capture(), anyList(), any(), isNull());
        assertThat(captor.getValue().tenant()).isEqualTo("static-tenant");
    }

    @Test
    void autoDetectedTenant_fromAgentCard_isAppliedToEveryCall() throws Exception {
        AgentCard cardWithTenant = AgentCard.builder()
                .name("a")
                .description("d")
                .version("1")
                .url("http://localhost")
                .capabilities(new AgentCapabilities(false, false, false, List.of()))
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(new AgentInterface("JSONRPC", "http://localhost", "auto-tenant")))
                .build();

        Client mockClient = mockClientReturning("ok", cardWithTenant);
        // Use the testing constructor — tenant comes from auto-detection inside extractTenantFromAgentCard
        DefaultA2AClientBuilder<StaticTenantAgent> builder =
                new DefaultA2AClientBuilder<>(cardWithTenant, StaticTenantAgent.class, mockClient);
        // Simulate what the constructor with URL would do: set tenant from card
        builder.tenant(DefaultA2AClientBuilder.extractTenantFromAgentCard(cardWithTenant));

        builder.build().chat("hello");

        ArgumentCaptor<MessageSendParams> captor = ArgumentCaptor.forClass(MessageSendParams.class);
        verify(mockClient).sendMessage(captor.capture(), anyList(), any(), isNull());
        assertThat(captor.getValue().tenant()).isEqualTo("auto-tenant");
    }

    private Client mockClientReturning(String response) {
        return mockClientReturning(response, agentCard);
    }

    private Client mockClientReturning(String response, AgentCard card) {
        Client mockClient = mock(Client.class);
        Message agentResponse = Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .parts(List.of(new TextPart(response)))
                .build();
        doAnswer(inv -> {
                    List<BiConsumer<ClientEvent, AgentCard>> consumers = inv.getArgument(1);
                    consumers.get(0).accept(new MessageEvent(agentResponse), card);
                    return null;
                })
                .when(mockClient)
                .sendMessage(any(MessageSendParams.class), anyList(), any(), isNull());
        doAnswer(inv -> {
                    List<BiConsumer<ClientEvent, AgentCard>> consumers = inv.getArgument(1);
                    consumers.get(0).accept(new MessageEvent(agentResponse), card);
                    return null;
                })
                .when(mockClient)
                .sendMessage(any(Message.class), anyList(), any(), isNull());
        return mockClient;
    }
}
