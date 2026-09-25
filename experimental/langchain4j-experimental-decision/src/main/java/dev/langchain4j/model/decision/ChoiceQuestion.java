package dev.langchain4j.model.decision;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A question that selects one named option. */
@Experimental
public final class ChoiceQuestion implements Question {

    private final String instructions;
    private final Map<String, OptionCriteria> options;

    private ChoiceQuestion(Builder builder) {
        this.instructions = ensureNotBlank(builder.instructions, "instructions");
        this.options = copy(ensureNotEmpty(builder.options, "options"));
    }

    @Override
    public String instructions() {
        return instructions;
    }

    public Map<String, OptionCriteria> options() {
        return options;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChoiceQuestion that)) return false;
        return Objects.equals(instructions, that.instructions) && Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instructions, options);
    }

    @Override
    public String toString() {
        return "ChoiceQuestion{instructions=" + instructions + ", options=" + options + '}';
    }

    public static final class Builder {
        private String instructions;
        private final Map<String, OptionCriteria> options = new LinkedHashMap<>();

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder options(Map<String, OptionCriteria> options) {
            this.options.clear();
            if (options != null) {
                options.forEach(this::option);
            }
            return this;
        }

        public Builder option(String name, OptionCriteria criteria) {
            options.put(ensureNotBlank(name, "option name"), ensureNotNull(criteria, "criteria"));
            return this;
        }

        public ChoiceQuestion build() {
            return new ChoiceQuestion(this);
        }
    }
}
