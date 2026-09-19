package dev.langchain4j.model.judge;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Answers keyed by the names supplied in a {@link JudgeRequest}. */
@Experimental
public final class JudgeResponse {

    private final Map<String, JudgeAnswer> answers;

    private JudgeResponse(Builder builder) {
        this.answers = copy(ensureNotEmpty(builder.answers, "answers"));
    }

    public Map<String, JudgeAnswer> answers() {
        return answers;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JudgeResponse that)) return false;
        return Objects.equals(answers, that.answers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(answers);
    }

    @Override
    public String toString() {
        return "JudgeResponse{answers=" + answers + '}';
    }

    public static final class Builder {
        private final Map<String, JudgeAnswer> answers = new LinkedHashMap<>();

        public Builder answers(Map<String, JudgeAnswer> answers) {
            this.answers.clear();
            if (answers != null) {
                answers.forEach(this::answer);
            }
            return this;
        }

        public Builder answer(String name, JudgeAnswer answer) {
            answers.put(ensureNotBlank(name, "answer name"), ensureNotNull(answer, "answer"));
            return this;
        }

        public JudgeResponse build() {
            return new JudgeResponse(this);
        }
    }
}
