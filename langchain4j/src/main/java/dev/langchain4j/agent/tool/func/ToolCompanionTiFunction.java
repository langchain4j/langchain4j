package dev.langchain4j.agent.tool.func;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.lambda.LambdaMeta;
import dev.langchain4j.agent.tool.lambda.ToolSerializedCompanionFunction;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

@FunctionalInterface
public interface ToolCompanionTiFunction<T, U1, U2, R> extends ToolSerializedCompanionFunction {

    /**
     * Applies this function to the given arguments.
     *
     * @param t  the first function argument
     * @param u1 the second function argument
     * @param u2 the third function argument
     * @return the function result
     */
    R apply(T t, U1 u1, U2 u2);

    default ToolExecutor delegate() {
        final @NotNull LambdaMeta meta = LambdaMeta.extract(this);
        final Class<?> implClass = meta.getImplClass();
        final @NotNull Method implMethod = meta.getImplMethod();

        return new ToolExecutor(implClass, implMethod) {
            @Override
            public Object execute(Object[] args) {
                return apply(sneakyCast(args[0]), sneakyCast(args[1]), sneakyCast(args[2]));
            }
        };
    }
}

