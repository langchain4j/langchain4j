package dev.langchain4j.agentic.declarative;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * SPI for resolving parameters of the static methods of declarative agents from external sources
 * such as a dependency injection container.
 * <p>
 * Applies to the methods annotated with any supplier annotation: {@link ChatModelSupplier},
 * {@link StreamingChatModelSupplier}, {@link ContentRetrieverSupplier},
 * {@link RetrievalAugmentorSupplier}, {@link ChatMemorySupplier},
 * {@link ChatMemoryProviderSupplier}, {@link ToolProviderSupplier},
 * {@link ToolsSupplier}, {@link AgentListenerSupplier}, {@link SystemMessageProviderSupplier},
 * {@link UserMessageProviderSupplier}, {@link McpClientSupplier}, {@link PlannerSupplier},
 * {@link A2AServerUrlSupplier} and {@link ParallelExecutor}; to the methods annotated with
 * {@link ExitCondition}, {@link ActivationCondition}, {@link Output}, {@link BeforeCall},
 * {@link ErrorHandler}, {@link SupervisorRequest} and {@link A2AClientCustomizer}; to the static
 * {@link dev.langchain4j.agentic.Agent} methods of non-AI agents and to the {@link HumanInTheLoop} methods.
 * <p>
 * A parameter supported by a resolver is never read from the {@link dev.langchain4j.agentic.scope.AgenticScope}
 * and is not an argument of the agent: it is not reported to listeners and planners.
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
     * Called once per parameter at agent creation time to pre-determine which parameters this resolver handles.
     * {@link Context#supplierMethod()} is the method declaring the parameter, whatever its annotation.
     */
    boolean supports(Context context);

    /**
     * Called at invocation time to obtain the actual value.
     */
    Object resolve(Context context);
}
