package dev.langchain4j.model.judge;

import dev.langchain4j.Experimental;
import dev.langchain4j.internal.AsyncNotSupported;
import java.util.concurrent.CompletableFuture;

/** A model that evaluates a state against a batch of typed, named questions. */
@Experimental
public interface JudgeModel {

    JudgeResponse judge(JudgeRequest request);

    default CompletableFuture<JudgeResponse> judgeAsync(JudgeRequest request) {
        return AsyncNotSupported.failedFuture(getClass(), "judgeAsync");
    }

    default JudgeRequestParameters defaultRequestParameters() {
        return JudgeRequestParameters.EMPTY;
    }
}
