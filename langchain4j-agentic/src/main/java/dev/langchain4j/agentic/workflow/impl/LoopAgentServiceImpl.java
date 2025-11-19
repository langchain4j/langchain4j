package dev.langchain4j.agentic.workflow.impl;

import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.internal.AbstractServiceBuilder;
import dev.langchain4j.agentic.workflow.LoopAgentService;
import java.lang.reflect.Method;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static dev.langchain4j.agentic.internal.AgentUtil.validateAgentClass;

public class LoopAgentServiceImpl<T> extends AbstractServiceBuilder<T, LoopAgentService<T>> implements LoopAgentService<T> {

    private int maxIterations = Integer.MAX_VALUE;
    private BiPredicate<AgenticScope, Integer> exitCondition = (scope, loopCounter) -> false;
    private boolean testExitAtLoopEnd = false;

    public LoopAgentServiceImpl(Class<T> agentServiceClass, Method agenticMethod) {
        super(agentServiceClass, agenticMethod);
    }

    @Override
    public T build() {
        return build(() -> new LoopPlanner(maxIterations, testExitAtLoopEnd, exitCondition));
    }

    public static LoopAgentServiceImpl<UntypedAgent> builder() {
        return new LoopAgentServiceImpl<>(UntypedAgent.class, null);
    }

    public static <T> LoopAgentServiceImpl<T> builder(Class<T> agentServiceClass) {
        return new LoopAgentServiceImpl<>(agentServiceClass, validateAgentClass(agentServiceClass, false));
    }

    @Override
    public LoopAgentServiceImpl<T> maxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
        return this;
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(Predicate<AgenticScope> exitCondition) {
        this.exitCondition = (scope, loopCounter) -> exitCondition.test(scope);
        return this;
    }

    @Override
    public LoopAgentServiceImpl<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition) {
        this.exitCondition = exitCondition;
        return this;
    }

    @Override
    public LoopAgentServiceImpl<T> testExitAtLoopEnd(boolean testExitAtLoopEnd) {
        this.testExitAtLoopEnd = testExitAtLoopEnd;
        return this;
    }

    @Override
    public String serviceType() {
        return "Loop";
    }
}
