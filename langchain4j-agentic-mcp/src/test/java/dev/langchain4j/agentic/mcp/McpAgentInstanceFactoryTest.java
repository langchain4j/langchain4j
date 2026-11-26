package dev.langchain4j.agentic.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.AgenticServices.AgentConfigurator;
import dev.langchain4j.agentic.agent.AgentBuilder;
import dev.langchain4j.agentic.declarative.McpClientAgent;
import dev.langchain4j.agentic.declarative.McpClientSupplier;
import dev.langchain4j.agentic.declarative.SequenceAgent;
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
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.V;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * An integration provides the object representing a declarative agent through the {@code agentInstanceFactory} of an
 * {@link AgentConfigurator}, which is how an agent declared as a class gets an instance, since a JDK proxy cannot
 * extend a class. An MCP client agent declared as a class is created through that factory, while one declared as an
 * interface keeps its JDK proxy.
 */
class McpAgentInstanceFactoryTest {

    static McpClient mcpClient;

    @BeforeEach
    void mockMcpClient() {
        mcpClient = mock(McpClient.class);
        when(mcpClient.listTools())
                .thenReturn(List.of(ToolSpecification.builder()
                        .name("greet")
                        .description("Generate a greeting")
                        .parameters(JsonObjectSchema.builder()
                                .addProperty("name", new JsonStringSchema())
                                .build())
                        .build()));
        when(mcpClient.executeTool(any()))
                .thenReturn(ToolExecutionResult.builder()
                        .resultText("Hello, Mario!")
                        .isError(false)
                        .build());
    }

    /** An MCP client agent declared as a class, as the bean of a container: its client supplier is an instance method. */
    public static class Greeter {

        @McpClientAgent(toolName = "greet", outputKey = "greeting")
        public native String greet(@V("name") String name);

        @McpClientSupplier
        Object mcpClient() {
            return mcpClient;
        }
    }

    public static class AsyncGreeter {

        @McpClientAgent(toolName = "greet", outputKey = "greeting", async = true)
        public native String greet(@V("name") String name);

        @McpClientSupplier
        Object mcpClient() {
            return mcpClient;
        }
    }

    public interface GreetingSequence {

        @SequenceAgent(outputKey = "greeting", subAgents = Greeter.class)
        String greet(@V("name") String name);
    }

    public interface AsyncGreetingSequence {

        @SequenceAgent(outputKey = "greeting", subAgents = AsyncGreeter.class)
        String greet(@V("name") String name);
    }

    public interface InterfaceGreeter {

        @McpClientAgent(toolName = "greet", outputKey = "greeting")
        String greet(@V("name") String name);

        @McpClientSupplier
        static Object mcpClient() {
            return mcpClient;
        }
    }

    public interface InterfaceGreetingSequence {

        @SequenceAgent(outputKey = "greeting", subAgents = InterfaceGreeter.class)
        String greet(@V("name") String name);
    }

    /**
     * What an integration generates for an agent class is a subclass of it, forwarding the agent method and the
     * methods of the interfaces of an MCP client agent to the agent that the factory received, which is also an
     * invocation handler. This interface does that forwarding for the subclasses of this test, passing the subclass
     * instance to the handler as the generated classes do. Like them it is also an {@link AgenticScopeOwner}, which
     * for an MCP client agent, having no agentic scope of its own, is the object itself.
     */
    interface ForwardingMcpClientAgent extends McpClientInstance, AgenticScopeOwner {

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
            return forward(McpClientInstance.class, methodName, args);
        }

        @Override
        default String[] inputKeys() {
            return forward("inputKeys");
        }

        @Override
        default Map<String, String> inputDescriptions() {
            return forward("inputDescriptions");
        }

        @Override
        default String toolName() {
            return forward("toolName");
        }

        @Override
        default String toolDescription() {
            return forward("toolDescription");
        }

        @Override
        default ToolSpecification toolSpecification() {
            return forward("toolSpecification");
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

    static class GeneratedGreeter extends Greeter implements ForwardingMcpClientAgent {

        private final InternalAgent agent;

        GeneratedGreeter(InternalAgent agent) {
            this.agent = agent;
        }

        @Override
        public InternalAgent agent() {
            return agent;
        }

        @Override
        public String greet(String name) {
            return forward(Greeter.class, "greet", name);
        }
    }

    static class GeneratedAsyncGreeter extends AsyncGreeter implements ForwardingMcpClientAgent {

        private final InternalAgent agent;

        GeneratedAsyncGreeter(InternalAgent agent) {
            this.agent = agent;
        }

        @Override
        public InternalAgent agent() {
            return agent;
        }

        @Override
        public String greet(String name) {
            return forward(AsyncGreeter.class, "greet", name);
        }
    }

    /** What a factory set on the builder of an agent interface creates: an implementation forwarding in the same way. */
    static class GeneratedInterfaceGreeter implements InterfaceGreeter, ForwardingMcpClientAgent {

        private final InternalAgent agent;

        GeneratedInterfaceGreeter(InternalAgent agent) {
            this.agent = agent;
        }

        @Override
        public InternalAgent agent() {
            return agent;
        }

        @Override
        public String greet(String name) {
            return forward(InterfaceGreeter.class, "greet", name);
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
                    if (agent.type() == Greeter.class) {
                        return new GeneratedGreeter(agent);
                    }
                    if (agent.type() == AsyncGreeter.class) {
                        return new GeneratedAsyncGreeter(agent);
                    }
                    return Proxy.newProxyInstance(
                            agent.type().getClassLoader(),
                            AgentBuilder.interfacesToImplement(agent.type()),
                            (InvocationHandler) agent);
                })
                .helperObjectsResolver(type -> {
                    if (type == Greeter.class) {
                        return List.of(new Greeter());
                    }
                    if (type == AsyncGreeter.class) {
                        return List.of(new AsyncGreeter());
                    }
                    return List.of();
                })
                .build();
    }

    @Test
    void class_based_mcp_agent_is_created_by_the_agent_instance_factory() {
        GreetingSequence sequence =
                AgenticServices.createAgenticSystem(GreetingSequence.class, (ChatModel) null, configurator());

        assertThat(factoryCalls).contains(Greeter.class);
        AgentInstance greeter = ((AgentInstance) sequence).subagents().get(0);
        assertThat(greeter.name()).isEqualTo("greet");
        assertThat(greeter.as(McpClientInstance.class)).isInstanceOf(GeneratedGreeter.class);

        assertThat(sequence.greet("Mario")).isEqualTo("Hello, Mario!");

        ArgumentCaptor<ToolExecutionRequest> request = ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(mcpClient).executeTool(request.capture());
        assertThat(request.getValue().name()).isEqualTo("greet");
        assertThat(request.getValue().arguments()).contains("\"name\"").contains("\"Mario\"");
    }

    @Test
    void class_based_mcp_agent_as_the_root_of_the_system_is_created_by_the_agent_instance_factory() {
        Greeter greeter = AgenticServices.createAgenticSystem(Greeter.class, (ChatModel) null, configurator());

        assertThat(factoryCalls).containsExactly(Greeter.class);
        assertThat(greeter).isInstanceOf(GeneratedGreeter.class);

        assertThat(greeter.greet("Mario")).isEqualTo("Hello, Mario!");

        ArgumentCaptor<ToolExecutionRequest> request = ArgumentCaptor.forClass(ToolExecutionRequest.class);
        verify(mcpClient).executeTool(request.capture());
        assertThat(request.getValue().name()).isEqualTo("greet");
        assertThat(request.getValue().arguments()).contains("\"name\"").contains("\"Mario\"");
    }

    @Test
    void async_class_based_mcp_agent_is_created_by_the_agent_instance_factory() {
        AsyncGreetingSequence sequence =
                AgenticServices.createAgenticSystem(AsyncGreetingSequence.class, (ChatModel) null, configurator());

        assertThat(factoryCalls).contains(AsyncGreeter.class);
        assertThat(((AgentInstance) sequence).subagents().get(0).async()).isTrue();

        assertThat(sequence.greet("Mario")).isEqualTo("Hello, Mario!");
        verify(mcpClient).executeTool(any());
    }

    @Test
    void class_based_mcp_agent_without_an_agent_instance_factory_fails_when_it_is_built() {
        assertThatThrownBy(() -> McpAgent.builder(mcpClient, Greeter.class).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An MCP client agent must be defined by an interface, but " + Greeter.class.getName()
                        + " is a class");
    }

    @Test
    void agent_instance_factory_not_returning_an_mcp_client_agent_of_the_agent_type_fails_when_the_agent_is_built() {
        // an instance of the agent type that is not an MCP client agent, an MCP client agent of another type, null
        assertThatThrownBy(() -> McpAgent.builder(mcpClient, Greeter.class)
                        .agentInstanceFactory(agent -> new Greeter())
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("agent instance factory of the MCP client agent 'greet'")
                .hasMessageContaining("must return an instance of " + Greeter.class.getName())
                .hasMessageContaining(McpClientInstance.class.getName())
                .hasMessageContaining("returned an instance of " + Greeter.class.getName());
        assertThatThrownBy(() -> McpAgent.builder(mcpClient, Greeter.class)
                        .agentInstanceFactory(GeneratedAsyncGreeter::new)
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("returned an instance of " + GeneratedAsyncGreeter.class.getName());
        assertThatThrownBy(() -> McpAgent.builder(mcpClient, Greeter.class)
                        .agentInstanceFactory(agent -> null)
                        .build())
                .isInstanceOf(AgenticSystemConfigurationException.class)
                .hasMessageContaining("returned null");
    }

    @Test
    void a_method_of_the_generated_subclass_passed_in_place_of_the_one_of_its_interface_does_not_invoke_the_tool()
            throws Exception {
        AtomicReference<InternalAgent> agent = new AtomicReference<>();
        Greeter greeter = McpAgent.builder(mcpClient, Greeter.class)
                .agentInstanceFactory(internalAgent -> {
                    agent.set(internalAgent);
                    return new GeneratedGreeter(internalAgent);
                })
                .build();
        // name() as the subclass has it, which is not the method that AgentInstance declares
        Method nameOfSubclass = GeneratedGreeter.class.getMethod("name");
        assertThat(nameOfSubclass.getDeclaringClass()).isNotEqualTo(AgentInstance.class);

        assertThatThrownBy(() -> ((InvocationHandler) agent.get()).invoke(greeter, nameOfSubclass, null))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("'name'")
                .hasMessageContaining(nameOfSubclass.getDeclaringClass().getName())
                .hasMessageContaining("is not the agent method of the MCP client agent 'greet'");
        verifyNoInteractions(mcpClient);
    }

    @Test
    void interface_based_mcp_agent_keeps_its_jdk_proxy_when_an_agent_instance_factory_is_set() {
        InterfaceGreetingSequence sequence =
                AgenticServices.createAgenticSystem(InterfaceGreetingSequence.class, (ChatModel) null, configurator());

        // the factory is called for the sequence, as before, but not for the MCP client agent
        assertThat(factoryCalls).contains(InterfaceGreetingSequence.class).doesNotContain(InterfaceGreeter.class);
        McpClientInstance greeter =
                ((AgentInstance) sequence).subagents().get(0).as(McpClientInstance.class);
        assertThat(Proxy.isProxyClass(greeter.getClass())).isTrue();
        assertThat(greeter).isInstanceOf(InterfaceGreeter.class);

        assertThat(sequence.greet("Mario")).isEqualTo("Hello, Mario!");
    }

    @Test
    void interface_based_mcp_agent_is_created_by_an_agent_instance_factory_set_on_its_builder() {
        // unlike the factory of a configurator, which all the agents of an integration share, this one is set for
        // this agent, so the builder uses it as the builders of the other agents do
        InterfaceGreeter greeter = McpAgent.builder(mcpClient, InterfaceGreeter.class)
                .agentInstanceFactory(GeneratedInterfaceGreeter::new)
                .build();

        assertThat(greeter).isInstanceOf(GeneratedInterfaceGreeter.class);
        assertThat(greeter.greet("Mario")).isEqualTo("Hello, Mario!");
        verify(mcpClient).executeTool(any());
    }
}
