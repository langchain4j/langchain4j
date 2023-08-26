package dev.langchain4j.agent.tool.func;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.lambda.LambdaMeta;
import dev.langchain4j.agent.tool.lambda.ToolSerializedFunction;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

@FunctionalInterface
public interface ToolBiFunction<TOOL, U1, U2, R> extends ToolSerializedFunction {
    /**
     * Applies this function to the given arguments.
     *
     * @param t  the tool instance
     * @param u1  the first function argument
     * @param u2 the second function argument
     * @return the function result
     */
    R apply(TOOL t, U1 u1, U2 u2);

    default ToolExecutor wrap(TOOL tool) {
        final @NotNull LambdaMeta meta = LambdaMeta.extract(this);
        final @NotNull Method implMethod = meta.getImplMethod();

        return new ToolExecutor(tool, implMethod) {
            @Override
            public Object execute(Object[] args) {
                return apply(tool, sneakyCast(args[0]), sneakyCast(args[1]));
            }
        };
    }
}

