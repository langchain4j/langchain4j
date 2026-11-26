package dev.langchain4j.agentic.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.a2a.A2ALazyAgentCardTest.ReplyingClient;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.declarative.A2AClientAgent;
import dev.langchain4j.agentic.declarative.A2AServerUrlSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.agentic.internal.A2AClientBuilder;
import dev.langchain4j.agentic.internal.A2AService;
import dev.langchain4j.agentic.internal.AgenticScopeOwner;
import dev.langchain4j.agentic.internal.InternalAgent;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.planner.AgentArgument;
import dev.langchain4j.agentic.planner.AgentCondition;
import dev.langchain4j.agentic.planner.AgentInstance;
import dev.langchain4j.agentic.planner.AgenticSystemConfigurationException;
import dev.langchain4j.agentic.planner.AgenticSystemTopology;
import dev.langchain4j.agentic.planner.Planner;
import dev.langchain4j.agentic.scope.AgenticScopeRegistry;
import dev.langchain4j.agentic.scope.DefaultAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.V;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * An integration provides the object representing a declarative agent through the {@code agentInstanceFactory} of an
 * {@link AgentConfigurator}, which is how an agent declared as a class gets an instance, since a JDK proxy cannot
 * extend a class. An A2A client agent declared as a class is created through that factory, while one declared as an
 * interface keeps its JDK proxy.
 */
class A2AAgentInstanceFactoryTest {

    private static final AgentCard REMOTE_FIXER_CARD = AgentCard.builder()
            .name("remote-fixer")
            .description("Fixes the analyzed problem")
            .version("1.0.0")
            .url("http://localhost")
            .capabilities(new AgentCapabilities(false, false, false, List.of()))
            .defaultInputModes(List.of("text"))
            .defaultOutputModes(List.of("text"))
            .skills(List.of())
            .supportedInterfaces(List.of())
            .build();

    private ReplyingClient remoteFixer;

    /** The declarative agents of this test get the card above and a mocked client replying "fixed". */
    @BeforeEach
    void useMockedRemoteAgent() {
        remoteFixer = new ReplyingClient("fixed", REMOTE_FIXER_CARD);
        A2AService.setA2AService(new DefaultA2AService() {
            @Override
            public <T> A2AClientBuilder<T> a2aBuilder(String a2aServerUrl, String tenant, Class<T> agentServiceClass) {
                return new DefaultA2AClientBuilder<>(
                                a2aServerUrl, () -> REMOTE_FIXER_CARD, agentServiceClass, card -> remoteFixer.client)
                        .tenant(tenant);
            }
        });
    }

    @AfterEach
    void restoreDefaultA2AService() {
        A2AService.setA2AService(new DefaultA2AService());
    }

    /** An A2A client agent declared as a class, as the bean of a container: its URL supplier is an instance method. */
    public static class Fixer {

        @A2AClientAgent(outputKey = "fix")
        public native String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        String a2aServerUrl() {
            return A2ALazyAgentCardTest.UNREACHABLE_A2A_SERVER_URL;
        }
    }

    public static class AsyncFixer {

        @A2AClientAgent(outputKey = "fix", async = true)
        public native String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        String a2aServerUrl() {
            return A2ALazyAgentCardTest.UNREACHABLE_A2A_SERVER_URL;
        }
    }

    public interface FixingSequence {

        @SequenceAgent(outputKey = "fix", subAgents = Fixer.class)
        String fix(@V("analysis") String analysis);
    }

    public interface AsyncFixingSequence {

        @SequenceAgent(outputKey = "fix", subAgents = AsyncFixer.class)
        String fix(@V("analysis") String analysis);
    }

    public interface InterfaceFixer {

        @A2AClientAgent(outputKey = "fix")
        String applyFix(@V("analysis") String analysis);

        @A2AServerUrlSupplier
        static String a2aServerUrl() {
            return A2ALazyAgentCardTest.UNREACHABLE_A2A_SERVER_URL;
        }
    }

    public interface InterfaceFixingSequence {

        @SequenceAgent(outputKey = "fix", subAgents = InterfaceFixer.class)
        String fix(@V("analysis") String analysis);
    }

    /**
     * What an integration generates for an agent class is a subclass of it, forwarding the agent method and the
     * methods of the interfaces of an A2A client agent to the agent that the factory received, which is also an
     * invocation handler. This interface does that forwarding for the subclasses of this test, passing the subclass
     * instance to the handler as the generated classes do. Like them it is also an {@link AgenticScopeOwner}, which
     * for an A2A client agent, having no agentic scope of its own, is the object itself.
     */
    interface ForwardingA2AClientAgent extends A2AClientInstance, AgenticScopeOwner {

        InternalAgent agent();

        @Override
        default AgenticScopeOwner withAgenticScope(DefaultAgenticScope agenticScope) {
            return this;
        }

        @Override
        default AgenticScopeRegistry registry() {
            return null;
        }

        @SuppressWarnings("unchecked")
        default <R> R forward(Class<?> declaringType, String methodName, Object... args) {
            Method method = Stream.of(declaringType.getMethods())
                    .filter(m -> m.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            try {
                return (R) ((InvocationHandler) agent()).invoke(this, method, args);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException(e);
            }
        }

        private <R> R forward(String methodName, Object... args) {
            return forward(A2AClientInstance.class, methodName, args);
        }

        @Override
        default String[] inputKeys() {
            return forward("inputKeys");
        }

        @Override
        default AgentCard agentCard() {
            return forward("agentCard");
        }

        @Override
        default String tenant() {
            return forward("tenant");
        }

        @Override
        default void setParent(InternalAgent parent) {
            forward("setParent", parent);
        }

        @Override
        default void registerInheritedParentListener(AgentListener parentListener) {
            forward("registerInheritedParentListener", parentListener);
        }

        @Override
        default void appendId(String idSuffix) {
            forward("appendId", idSuffix);
        }

        @Override
        default AgentListener listener() {
            return forward("listener");
        }

        @Override
        default Class<?> type() {
            return forward("type");
        }

        @Override
        default Class<? extends Planner> plannerType() {
            return forward("plannerType");
        }

        @Override
        default String name() {
            return forward("name");
        }

        @Override
        default String agentId() {
            return forward("agentId");
        }

        @Override
        default String description() {
            return forward("description");
        }

        @Override
        default Type outputType() {
            return forward("outputType");
        }

        @Override
        default String outputKey() {
            return forward("outputKey");
        }

        @Override
        default boolean async() {
            return forward("async");
        }

        @Override
        default AgentCondition activationCondition() {
            return forward("activationCondition");
        }

        @Override
        default List<AgentArgument> arguments() {
            return forward("arguments");
        }

        @Override
        default AgentInstance parent() {
            return forward("parent");
        }

        @Override
        default List<AgentInstance> subagents() {
            return forward("subagents");
        }

        @Override
        default AgenticSystemTopology topology() {
            return forward("topology");
        }
    }

    static class GeneratedFixer extends Fixer implements ForwardingA2AClientAgent {

        private final InternalAgent agent;

        GeneratedFixer(InternalAgent agent) {
            this.agent = agent;
        }

        @Override
        public InternalAgent agent() {
            return agent;
        }

        @Override
        public String applyFix(String analysis) {
            return forward(Fixer.class, "applyFix", analysis);
        }
    }

    static class GeneratedAsyncFixer extends AsyncFixer implements ForwardingA2AClientAgent {

        private final InternalAgent agent;

        GeneratedAsyncFixer(InternalAgent agent) {
            this.agent = agent;
        }

        @Override
        public InternalAgent agent() {
            return agent;
        }

        @Override
        public String applyFix(String analysis) {
            return forward(AsyncFixer.class, "applyFix", analysis);
        }
    }

    /** The types of the agents that the factory was asked to create. */
    private final List<Class<?>> factoryCalls = new ArrayList<>();

    /**
     * The hooks of an integration with class-based agents: the factory creates the generated subclass of an agent
     * class and, as it is called for every agent of the system, a JDK proxy for the sequence containing it, while the
     * helper object of an agent class is its bean.
     */
    private AgentConfigurator configurator() {
        return AgentConfigurator.builder()
                .agentInstanceFactory(agent -> {
                    factoryCalls.add(agent.type());
                    if (agent.type() == Fixer.class) {
                        return new GeneratedFixer(agent);
                    }
                    if (agent.type() == AsyncFixer.class) {
                        return new GeneratedAsyncFixer(agent);
                    }
                    return Proxy.newProxyInstance(
                            agent.type().getClassLoader(),
                            AgentBuilder.interfacesToImplement(agent.type()),
                            (InvocationHandler) agent);
                })
                .helperObjectsResolver(type -> {
                    if (type == Fixer.class) {
                        return List.of(new Fixer());
                    }
                    if (type == AsyncFixer.class) {
                        return List.of(new AsyncFixer());
                    }
                    return List.of();
                })
                .build();
    }

    @Test
    void class_based_a2a_agent_is_created_by_the_agent_instance_factory() {
        FixingSequence sequence =
                AgenticServices.createAgenticSystem(FixingSequence.class, (ChatModel) null, configurator());

        assertThat(factoryCalls).contains(Fixer.class);
        AgentInstance fixer = ((AgentInstance) sequence).subagents().get(0);
        assertThat(fixer.name()).isEqualTo("applyFix");
        assertThat(fixer.as(A2AClientInstance.class)).isInstanceOf(GeneratedFixer.class);

        assertThat(sequence.fix("a broken build")).isEqualTo("fixed");

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(remoteFixer.client).sendMessage(message.capture(), anyList(), any(), any());
        assertThat(message.getValue().parts()).containsExactly(new TextPart("a broken build"));
    }

    @Test
    void class_based_a2a_agent_as_the_root_of_the_system_is_created_by_the_agent_instance_factory() {
        Fixer fixer = AgenticServices.createAgenticSystem(Fixer.class, (ChatModel) null, configurator());

        assertThat(factoryCalls).containsExactly(Fixer.class);
        assertThat(fixer).isInstanceOf(GeneratedFixer.class);

        assertThat(fixer.applyFix("a broken build")).isEqualTo("fixed");

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(remoteFixer.client).sendMessage(message.capture(), anyList(), any(), any());
        assertThat(message.getValue().parts()).containsExactly(new TextPart("a broken build"));
    }

    @Test
    void async_class_based_a2a_agent_is_created_by_the_agent_instance_factory() {
        AsyncFixingSequence sequence =
                AgenticServices.createAgenticSystem(AsyncFixingSequence.class, (ChatModel) null, configurator());

        assertThat(factoryCalls).contains(AsyncFixer.class);
        assertThat(((AgentInstance) sequence).subagents().get(0).async()).isTrue();

        assertThat(sequence.fix("a broken build")).isEqualTo("fixed");
        verify(remoteFixer.client).sendMessage(any(Message.class), anyList(), any(), any());
    }

    private static A2AClientBuilder<Fixer> fixerBuilder() {
        return AgenticServices.a2aBuilder(A2ALazyAgentCardTest.UNREACHABLE_A2A_SERVER_URL, Fixer.class);
    }

    @Test
    void class_based_a2a_agent_without_an_agent_instance_factory_fails_when_it_is_built() {
        assertThatThrownBy(() -> fixerBuilder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An A2A client agent must be defined by an interface, but " + Fixer.class.getName()
                        + " is a class");
    }

    @Test
    void agent_instance_factory_not_returning_an_a2a_client_agent_of_the_agent_type_fails_when_the_agent_is_built() {
        // an instance of the agent type that is not an A2A client agent, an A2A client agent of another type, null
        assertThatThrownBy(() ->
                        fixerBuilder().agentInstanceFactory(agent -> new Fixer()).build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("agent instance factory of the A2A client agent 'applyFix'")
                .hasMessageContaining("must return an instance of " + Fixer.class.getName())
                .hasMessageContaining(A2AClientInstance.class.getName())
                .hasMessageContaining("returned an instance of " + Fixer.class.getName());
        assertThatThrownBy(() -> fixerBuilder()
                        .agentInstanceFactory(GeneratedAsyncFixer::new)
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("returned an instance of " + GeneratedAsyncFixer.class.getName());
        assertThatThrownBy(() -> fixerBuilder().agentInstanceFactory(agent -> null).build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("returned null");
    }

    @Test
    void a_method_of_the_generated_subclass_passed_in_place_of_the_one_of_its_interface_does_not_invoke_the_remote_agent()
            throws Exception {
        AtomicReference<InternalAgent> agent = new AtomicReference<>();
        Fixer fixer = fixerBuilder()
                .agentInstanceFactory(internalAgent -> {
                    agent.set(internalAgent);
                    return new GeneratedFixer(internalAgent);
                })
                .build();
        // name() as the subclass has it, which is not the method that AgentInstance declares
        Method nameOfSubclass = GeneratedFixer.class.getMethod("name");
        assertThat(nameOfSubclass.getDeclaringClass()).isNotEqualTo(AgentInstance.class);

        assertThatThrownBy(() -> ((InvocationHandler) agent.get()).invoke(fixer, nameOfSubclass, null))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("'name'")
                .hasMessageContaining(nameOfSubclass.getDeclaringClass().getName())
                .hasMessageContaining("is not the agent method of the A2A client agent 'applyFix'");
        verifyNoInteractions(remoteFixer.client);
    }

    @Test
    void interface_based_a2a_agent_keeps_its_jdk_proxy_when_an_agent_instance_factory_is_set() {
        InterfaceFixingSequence sequence =
                AgenticServices.createAgenticSystem(InterfaceFixingSequence.class, (ChatModel) null, configurator());

        // the factory is called for the sequence, as before, but not for the A2A client agent
        assertThat(factoryCalls).contains(InterfaceFixingSequence.class).doesNotContain(InterfaceFixer.class);
        A2AClientInstance fixer = ((AgentInstance) sequence).subagents().get(0).as(A2AClientInstance.class);
        assertThat(Proxy.isProxyClass(fixer.getClass())).isTrue();
        assertThat(fixer).isInstanceOf(InterfaceFixer.class);

        assertThat(sequence.fix("a broken build")).isEqualTo("fixed");
    }
}
