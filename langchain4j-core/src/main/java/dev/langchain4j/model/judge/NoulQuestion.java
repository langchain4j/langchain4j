package dev.langchain4j.model.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import java.util.Objects;

/** A yes/no-style question whose answer is a probability from zero to one. */
@Experimental
public final class NoulQuestion implements Question {

    private final String instructions;
    private final NoulCriteria criteria;

    private NoulQuestion(Builder builder) {
        this.instructions = ensureNotBlank(builder.instructions, "instructions");
        this.criteria = builder.criteria;
    }

    @Override
    public String instructions() {
        return instructions;
    }

    public NoulCriteria criteria() {
        return criteria;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NoulQuestion that)) return false;
        return Objects.equals(instructions, that.instructions) && Objects.equals(criteria, that.criteria);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instructions, criteria);
    }

    @Override
    public String toString() {
        return "NoulQuestion{instructions=" + instructions + ", criteria=" + criteria + '}';
    }

    public static final class Builder {
        private String instructions;
        private NoulCriteria criteria;

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder criteria(NoulCriteria criteria) {
            this.criteria = criteria;
            return this;
        }

        public NoulQuestion build() {
            return new NoulQuestion(this);
        }
    }
}
