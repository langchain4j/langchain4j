package dev.langchain4j.model.decision;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.message.Content;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** State and named typed questions to evaluate in one judgment call. */
@Experimental
public final class DecisionRequest {

    private final Object state;
    private final List<Content> contents;
    private final Map<String, Question> questions;
    private final DecisionRequestParameters parameters;

    private DecisionRequest(Builder builder) {
        this.state = validateAndCopyState(builder.state);
        this.contents = List.copyOf(builder.contents);
        this.questions = copy(ensureNotEmpty(builder.questions, "questions"));
        this.parameters = getOrDefault(builder.parameters, DecisionRequestParameters.EMPTY);
    }

    public Object state() {
        return state;
    }

    public List<Content> contents() {
        return contents;
    }

    private static Object validateAndCopyState(Object state) {
        ensureNotNull(state, "state");
        if (state instanceof Map<?, ?> map) {
            return copy(ensureNotEmpty(map, "state"));
        }
        if (state instanceof List<?> list) {
            return List.copyOf(ensureNotEmpty(list, "state"));
        }
        if (state instanceof String text) {
            return ensureNotBlank(text, "state");
        }
        throw new IllegalArgumentException("state must be text, a map, or a list");
    }

    public Map<String, Question> questions() {
        return questions;
    }

    public DecisionRequestParameters parameters() {
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
        if (!(o instanceof DecisionRequest that)) return false;
        return Objects.equals(state, that.state)
                && Objects.equals(contents, that.contents)
                && Objects.equals(questions, that.questions)
                && Objects.equals(parameters, that.parameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(state, contents, questions, parameters);
    }

    @Override
    public String toString() {
        return "DecisionRequest{state=" + state + ", contents=" + contents + ", questions=" + questions
                + ", parameters=" + parameters + '}';
    }

    public static final class Builder {
        private Object state;
        private final List<Content> contents = new ArrayList<>();
        private final Map<String, Question> questions = new LinkedHashMap<>();
        private DecisionRequestParameters parameters;

        public Builder state(Object state) {
            this.state = state;
            return this;
        }

        public Builder content(Content... contents) {
            for (Content content : ensureNotNull(contents, "contents")) {
                this.contents.add(ensureNotNull(content, "content"));
            }
            return this;
        }

        public Builder contents(List<Content> contents) {
            this.contents.clear();
            ensureNotNull(contents, "contents")
                    .forEach(content -> this.contents.add(ensureNotNull(content, "content")));
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

        public Builder parameters(DecisionRequestParameters parameters) {
            this.parameters = parameters;
            return this;
        }

        public DecisionRequest build() {
            return new DecisionRequest(this);
        }
    }
}
