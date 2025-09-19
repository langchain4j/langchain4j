package dev.langchain4j.agentic.workflow;

import dev.langchain4j.agentic.scope.AgenticScope;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

public interface LoopAgentService<T> extends WorkflowService<LoopAgentService<T>, T> {

    LoopAgentService<T> maxIterations(int maxIterations);

    LoopAgentService<T> exitCondition(Predicate<AgenticScope> exitCondition);
    LoopAgentService<T> exitCondition(BiPredicate<AgenticScope, Integer> exitCondition);
    LoopAgentService<T> testExitAtLoopEnd(boolean checkExitConditionAtLoopEnd);
}
