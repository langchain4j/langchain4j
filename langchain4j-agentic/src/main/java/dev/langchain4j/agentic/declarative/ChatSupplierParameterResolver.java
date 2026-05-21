package dev.langchain4j.agentic.declarative;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * SPI for resolving parameters of supplier methods of {@link ChatModelSupplier} and {@link StreamingChatModelSupplier}
 * from external sources such as a dependency injection container.
 */
public interface ChatSupplierParameterResolver {

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
     * Called at invocation time to obtain the actual value
     */
    Object resolve(Context context);
}
