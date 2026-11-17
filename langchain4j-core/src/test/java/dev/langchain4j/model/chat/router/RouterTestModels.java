package dev.langchain4j.model.chat.router;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.internal.AsyncNotSupported;
import dev.langchain4j.test.mock.MockDecisionModel;

/**
 * Models for the router tests of this package.
 */
final class RouterTestModels {

    private RouterTestModels() {}

    /**
     * The given mock as a decision model without asynchronous support.
     */
    static DecisionModel withoutAsyncSupport(MockDecisionModel model) {
        MockDecisionModel withoutAsync = spy(model);
        doAnswer(invocation -> AsyncNotSupported.failedFuture(MockDecisionModel.class, "doDecideAsync"))
                .when(withoutAsync)
                .doDecideAsync(any());
        return withoutAsync;
    }
}
