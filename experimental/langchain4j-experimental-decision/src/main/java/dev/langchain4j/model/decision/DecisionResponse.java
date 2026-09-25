package dev.langchain4j.model.decision;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Answers keyed by the names supplied in a {@link DecisionRequest}. */
@Experimental
public final class DecisionResponse {

    private final Map<String, DecisionAnswer> answers;
    private final Map<String, Object> metadata;

    private DecisionResponse(Builder builder) {
        this.answers = copy(ensureNotEmpty(builder.answers, "answers"));
        this.metadata = DecisionSnapshots.map(builder.metadata);
    }

    public Map<String, DecisionAnswer> answers() {
        return answers;
    }

    /** Additional response fields supplied by the provider. */
    public Map<String, Object> metadata() {
        return metadata;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DecisionResponse that)) return false;
        return Objects.equals(answers, that.answers) && Objects.equals(metadata, that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(answers, metadata);
    }

    @Override
    public String toString() {
        return "DecisionResponse{answers=" + answers + ", metadata=" + metadata + '}';
    }

    public static final class Builder {
        private final Map<String, DecisionAnswer> answers = new LinkedHashMap<>();
        private final Map<String, Object> metadata = new LinkedHashMap<>();

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.clear();
            if (metadata != null) {
                metadata.forEach(this::metadata);
            }
            return this;
        }

        public Builder metadata(String name, Object value) {
            metadata.put(ensureNotBlank(name, "metadata name"), value);
            return this;
        }

        public Builder answers(Map<String, DecisionAnswer> answers) {
            this.answers.clear();
            if (answers != null) {
                answers.forEach(this::answer);
            }
            return this;
        }

        public Builder answer(String name, DecisionAnswer answer) {
            answers.put(ensureNotBlank(name, "answer name"), ensureNotNull(answer, "answer"));
            return this;
        }

        public DecisionResponse build() {
            return new DecisionResponse(this);
        }
    }
}
