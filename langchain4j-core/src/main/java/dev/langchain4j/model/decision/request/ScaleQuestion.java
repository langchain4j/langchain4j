package dev.langchain4j.model.decision.request;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.ArrayList;
import java.util.Collections;
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
 *         .level("Angry", "Uses insults or threatens to cancel")  // 2
 *         .build();
 * }</pre>
 * A level can have a description of when it applies, in addition to its label.
 *
 * @since 1.21.0
 */
@Experimental
public final class ScaleQuestion implements Question {

    private final String text;
    private final List<String> levels;
    private final List<String> levelDescriptions;

    private ScaleQuestion(Builder builder) {
        this.text = ensureNotBlank(builder.text, "text");
        ensureTrue(builder.levels.size() >= 2, "ScaleQuestion requires at least 2 levels");
        this.levels = copy(builder.levels);
        this.levelDescriptions = Collections.unmodifiableList(new ArrayList<>(builder.levelDescriptions));
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
     * The descriptions of the levels, in the same order as {@link #levels()}: the description of when a level
     * applies, or {@code null} for a level without description.
     *
     * @since 1.22.0
     */
    public List<String> levelDescriptions() {
        return levelDescriptions;
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
        return Objects.equals(text, that.text)
                && Objects.equals(levels, that.levels)
                && Objects.equals(levelDescriptions, that.levelDescriptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text, levels, levelDescriptions);
    }

    @Override
    public String toString() {
        return "ScaleQuestion{text=" + text + ", levels=" + levels + ", levelDescriptions=" + levelDescriptions + '}';
    }

    public static final class Builder {

        private String text;
        private final List<String> levels = new ArrayList<>();
        private final List<String> levelDescriptions = new ArrayList<>();

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Replaces all levels, ordered from lowest to highest.
         */
        public Builder levels(List<String> levels) {
            this.levels.clear();
            this.levelDescriptions.clear();
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
            levelDescriptions.add(null);
            return this;
        }

        /**
         * Adds the next (higher) level, with a label and a description of when it applies, for example
         * {@code level("Angry", "Uses insults or threatens to cancel")}.
         *
         * @since 1.22.0
         */
        public Builder level(String label, String description) {
            levels.add(ensureNotBlank(label, "level"));
            levelDescriptions.add(ensureNotBlank(description, "description of level '%s'".formatted(label)));
            return this;
        }

        public ScaleQuestion build() {
            return new ScaleQuestion(this);
        }
    }
}
