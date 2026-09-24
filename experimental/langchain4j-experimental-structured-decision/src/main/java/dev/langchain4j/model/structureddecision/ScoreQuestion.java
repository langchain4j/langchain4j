package dev.langchain4j.model.structureddecision;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** A question that returns a position along an ordered set of named levels. */
@Experimental
public final class ScoreQuestion implements Question {

    private final String instructions;
    private final Map<String, OptionCriteria> levels;

    private ScoreQuestion(Builder builder) {
        this.instructions = ensureNotBlank(builder.instructions, "instructions");
        ensureTrue(builder.levels.size() >= 2, "ScoreQuestion requires at least 2 levels");
        this.levels = copy(builder.levels);
    }

    @Override
    public String instructions() {
        return instructions;
    }

    public Map<String, OptionCriteria> levels() {
        return levels;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScoreQuestion that)) return false;
        return Objects.equals(instructions, that.instructions) && Objects.equals(levels, that.levels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instructions, levels);
    }

    @Override
    public String toString() {
        return "ScoreQuestion{instructions=" + instructions + ", levels=" + levels + '}';
    }

    public static final class Builder {
        private String instructions;
        private final Map<String, OptionCriteria> levels = new LinkedHashMap<>();

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder levels(Map<String, OptionCriteria> levels) {
            this.levels.clear();
            if (levels != null) {
                levels.forEach(this::level);
            }
            return this;
        }

        public Builder level(String name, OptionCriteria criteria) {
            levels.put(ensureNotBlank(name, "level name"), ensureNotNull(criteria, "criteria"));
            return this;
        }

        public ScoreQuestion build() {
            return new ScoreQuestion(this);
        }
    }
}
