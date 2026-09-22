package dev.langchain4j.model.judge;

import dev.langchain4j.Experimental;

/** Per-call parameters for a {@link JudgeRequest}. */
@Experimental
public interface JudgeRequestParameters {

    /** Empty immutable parameters used when a request does not specify overrides. */
    JudgeRequestParameters EMPTY = DefaultJudgeRequestParameters.builder().build();

    String modelName();

    /**
     * Returns new parameters combining this instance with {@code that}. Values from {@code that} take precedence;
     * neither instance is modified.
     */
    JudgeRequestParameters overrideWith(JudgeRequestParameters that);

    static DefaultJudgeRequestParameters.Builder<?> builder() {
        return new DefaultJudgeRequestParameters.Builder<>();
    }
}
