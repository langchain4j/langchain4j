package dev.langchain4j.agentic.declarative;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * SPI for resolving parameters of static supplier methods from external sources
 * such as a dependency injection container.
 * <p>
 * Applies to all supplier annotations: {@link ChatModelSupplier},
 * {@link StreamingChatModelSupplier}, {@link ContentRetrieverSupplier},
 * {@link RetrievalAugmentorSupplier}, {@link ChatMemorySupplier},
 * {@link ChatMemoryProviderSupplier}, {@link ToolProviderSupplier},
 * {@link ToolsSupplier}, {@link AgentListenerSupplier}, and
 * {@link ParallelExecutor}.
 * <p>
 * Implementations are registered via {@link DeclarativeUtil#addSupplierParameterResolver}.
 */
public interface SupplierParameterResolver {

    interface Context {

        Class<?> declaringAgentClass();

        Method supplierMethod();

        Parameter parameter();
    }

    /**
     * Called once per parameter at agent creation time to pre-determine which parameters this resolver handles.
     */
    boolean supports(Context context);

    /**
     * Called at invocation time to obtain the actual value.
     */
    Object resolve(Context context);
}
