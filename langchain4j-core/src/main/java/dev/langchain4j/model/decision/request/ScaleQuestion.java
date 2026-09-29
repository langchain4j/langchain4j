package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A question that places the input on an ordered scale, answered with the mean level and the probability of each level
 * (see {@link dev.langchain4j.model.decision.response.ScaleAnswer}).
 * <p>
 * Levels are described with text and ordered from lowest to highest; a level's number is its index, starting at 0:
 * <pre>{@code
 * ScaleQuestion frustration = ScaleQuestion.builder()
 *         .text("How frustrated is the customer?")
 *         .level("Calm")        // 0
 *         .level("Frustrated")  // 1
 *         .level("Angry")       // 2
 *         .build();
 * }</pre>
 *
 * @since 1.21.0
 */
@Experimental
public final class ScaleQuestion implements Question {

    private final String text;
    private final List<String> levels;

    private ScaleQuestion(Builder builder) {
        this.text = ensureNotBlank(builder.text, "text");
        ensureTrue(builder.levels.size() >= 2, "ScaleQuestion requires at least 2 levels");
        this.levels = copy(builder.levels);
    }

    @Override
    public String text() {
        return text;
    }

    /**
     * The levels, from lowest (index 0) to highest.
     */
    public List<String> levels() {
        return levels;
    }

    /**
     * Creates a scale question with the given text and levels, ordered from lowest to highest.
     */
    public static ScaleQuestion of(String text, List<String> levels) {
        return builder().text(text).levels(levels).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScaleQuestion that)) return false;
        return Objects.equals(text, that.text) && Objects.equals(levels, that.levels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, levels);
    }

    @Override
    public String toString() {
        return "ScaleQuestion{text=" + text + ", levels=" + levels + '}';
    }

    public static final class Builder {

        private String text;
        private final List<String> levels = new ArrayList<>();

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Replaces all levels, ordered from lowest to highest.
         */
        public Builder levels(List<String> levels) {
            this.levels.clear();
            if (levels != null) {
                levels.forEach(this::level);
            }
            return this;
        }

        /**
         * Adds the next (higher) level, described with text.
         */
        public Builder level(String description) {
            levels.add(ensureNotBlank(description, "level"));
            return this;
        }

        public ScaleQuestion build() {
            return new ScaleQuestion(this);
        }
    }
}
