package dev.langchain4j.agentic.declarative;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * SPI for resolving parameters of the static methods of declarative agents from external sources
 * such as a dependency injection container.
 * <p>
 * Applies to the methods annotated with any supplier annotation: {@link ChatModelSupplier},
 * {@link StreamingChatModelSupplier}, {@link DecisionModelSupplier}, {@link DecisionThresholdSupplier},
 * {@link ContentRetrieverSupplier},
 * {@link RetrievalAugmentorSupplier}, {@link ChatMemorySupplier},
 * {@link ChatMemoryProviderSupplier}, {@link ToolProviderSupplier},
 * {@link ToolsSupplier}, {@link ToolSearchStrategySupplier}, {@link AgentListenerSupplier},
 * {@link SystemMessageProviderSupplier},
 * {@link UserMessageProviderSupplier}, {@link McpClientSupplier}, {@link PlannerSupplier},
 * {@link A2AServerUrlSupplier} and {@link ParallelExecutor}; to the methods annotated with
 * {@link ExitCondition}, {@link ActivationCondition}, {@link Output}, {@link BeforeCall},
 * {@link ErrorHandler}, {@link SupervisorRequest} and {@link A2AClientCustomizer}; to the static
 * {@link dev.langchain4j.agentic.Agent} methods of non-AI agents and to the {@link HumanInTheLoop} methods.
 * <p>
 * The methods invoked without any value from the framework, like the {@link ToolsSupplier}, {@link PlannerSupplier}
 * or {@link ParallelExecutor} ones, can only declare resolved parameters: when one of their parameters is not
 * supported by any resolver, the creation of the agent fails with an
 * {@link dev.langchain4j.agentic.planner.AgenticSystemConfigurationException}.
 * <p>
 * A parameter supported by a resolver is never read from the {@link dev.langchain4j.agentic.scope.AgenticScope}
 * and is not an argument of the agent: it is not reported to listeners and planners. For this reason a resolver
 * should only support the parameters explicitly marked for it, for example with a dedicated annotation or qualifier.
 * When several resolvers support the same parameter, the first registered one is used.
 * <p>
 * Implementations are registered via {@link DeclarativeUtil#addSupplierParameterResolver}, and must be registered
 * before the agents using them are created.
 */
public interface SupplierParameterResolver {

    interface Context {

        Class<?> declaringAgentClass();

        Method supplierMethod();

        Parameter parameter();
    }

    /**
     * Called at agent creation time to pre-determine which parameters this resolver handles. It can be called more
     * than once for the same parameter, so it must not have side effects.
     * {@link Context#supplierMethod()} is the method declaring the parameter, whatever its annotation.
     */
    boolean supports(Context context);

    /**
     * Called each time the method declaring the parameter is invoked, to obtain the actual value: once, when the agent
     * is created, for the suppliers of fixed components like tools or chat memory, and at each invocation for the
     * methods depending on the agent invocation, like conditions or non-AI agents. In the latter case it can be
     * called on the thread executing the agent.
     */
    Object resolve(Context context);
}
