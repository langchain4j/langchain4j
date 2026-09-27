package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A question that places the state on an ordered scale, answered with a score and the probability of each level
 * (see {@link dev.langchain4j.model.decision.response.ScoreAnswer}).
 * <p>
 * Levels are ordered from lowest to highest; a level's number is its index, starting at 0. Each level is described
 * either with plain text or with structured content (a {@link java.util.Map} or a {@link java.util.List}) that is
 * passed to the model as is:
 * <pre>{@code
 * ScoreQuestion frustration = ScoreQuestion.builder()
 *         .instructions("How frustrated is the customer?")
 *         .level("Calm")        // 0
 *         .level("Frustrated")  // 1
 *         .level("Angry")       // 2
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class ScoreQuestion implements Question {

    private final String instructions;
    private final List<Object> levels;

    private ScoreQuestion(Builder builder) {
        this.instructions = ensureNotBlank(builder.instructions, "instructions");
        ensureTrue(builder.levels.size() >= 2, "ScoreQuestion requires at least 2 levels");
        this.levels = copy(builder.levels);
    }

    @Override
    public String instructions() {
        return instructions;
    }

    /**
     * The levels, from lowest (index 0) to highest. Each level is a {@link String}, a {@link java.util.Map} or a
     * {@link java.util.List}.
     */
    public List<Object> levels() {
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
        private final List<Object> levels = new ArrayList<>();

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        /**
         * Replaces all levels, ordered from lowest to highest. Each level is a {@link String}, a
         * {@link java.util.Map} or a {@link java.util.List}.
         */
        public Builder levels(List<?> levels) {
            this.levels.clear();
            if (levels != null) {
                levels.forEach(this::level);
            }
            return this;
        }

        /**
         * Adds the next (higher) level with a plain-text description.
         */
        public Builder level(String description) {
            return level((Object) description);
        }

        /**
         * Adds the next (higher) level, described as a {@link String}, a {@link java.util.Map} or a
         * {@link java.util.List}.
         */
        public Builder level(Object description) {
            levels.add(FreeFormValue.ensureValid(description, "level"));
            return this;
        }

        public ScoreQuestion build() {
            return new ScoreQuestion(this);
        }
    }
}
