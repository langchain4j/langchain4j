package dev.langchain4j.model.judge;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** State and named typed questions to evaluate in one judgment call. */
@Experimental
public final class JudgeRequest {

    private final Map<String, Object> state;
    private final Map<String, Question> questions;
    private final JudgeRequestParameters parameters;

    private JudgeRequest(Builder builder) {
        this.state = copy(ensureNotEmpty(builder.state, "state"));
        this.questions = copy(ensureNotEmpty(builder.questions, "questions"));
        this.parameters = getOrDefault(builder.parameters, JudgeRequestParameters.EMPTY);
    }

    public Map<String, Object> state() {
        return state;
    }

    public Map<String, Question> questions() {
        return questions;
    }

    public JudgeRequestParameters parameters() {
        return parameters;
    }

    public String modelName() {
        return parameters.modelName();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JudgeRequest that)) return false;
        return Objects.equals(state, that.state)
                && Objects.equals(questions, that.questions)
                && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, questions, parameters);
    }

    @Override
    public String toString() {
        return "JudgeRequest{state=" + state + ", questions=" + questions + ", parameters=" + parameters + '}';
    }

    public static final class Builder {
        private Map<String, Object> state;
        private final Map<String, Question> questions = new LinkedHashMap<>();
        private JudgeRequestParameters parameters;

        public Builder state(Map<String, Object> state) {
            this.state = state;
            return this;
        }

        public Builder questions(Map<String, Question> questions) {
            this.questions.clear();
            if (questions != null) {
                questions.forEach(this::question);
            }
            return this;
        }

        public Builder question(String name, Question question) {
            questions.put(ensureNotBlank(name, "question name"), ensureNotNull(question, "question"));
            return this;
        }

        public Builder parameters(JudgeRequestParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public JudgeRequest build() {
            return new JudgeRequest(this);
        }
    }
}
