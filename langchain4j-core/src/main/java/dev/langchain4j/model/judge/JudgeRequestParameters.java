package dev.langchain4j.model.judge;

import dev.langchain4j.Experimental;

/** Per-call parameters for a {@link JudgeRequest}. */
@Experimental
public interface JudgeRequestParameters {

    JudgeRequestParameters EMPTY = DefaultJudgeRequestParameters.builder().build();

    String modelName();

    JudgeRequestParameters overrideWith(JudgeRequestParameters that);

    static DefaultJudgeRequestParameters.Builder<?> builder() {
        return new DefaultJudgeRequestParameters.Builder<>();
    }
}
