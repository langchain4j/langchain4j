package dev.langchain4j.agent.tool.func;

import dev.langchain4j.agent.tool.ToolExecutor;
import dev.langchain4j.agent.tool.lambda.LambdaMeta;
import dev.langchain4j.agent.tool.lambda.ToolSerializedFunction;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.function.BiFunction;

@FunctionalInterface
public interface ToolFunction<TOOL, U, R> extends BiFunction<TOOL, U, R>, ToolSerializedFunction {

    default ToolExecutor wrap(TOOL tool) {
        final @NotNull LambdaMeta meta = LambdaMeta.extract(this);
        final @NotNull Method implMethod = meta.getImplMethod();

        return new ToolExecutor(tool, implMethod) {
            @Override
            public Object execute(Object[] args) {
                return apply(tool, sneakyCast(args[0]));
            }
        };
    }
}

