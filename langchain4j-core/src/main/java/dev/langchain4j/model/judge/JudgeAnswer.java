package dev.langchain4j.model.judge;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.Objects;

/** One typed answer returned for a named judge question. */
@Experimental
public final class JudgeAnswer {

    private final Double noul;
    private final String choice;
    private final Double score;
    private final Double confidence;

    private JudgeAnswer(Builder builder) {
        int values = (builder.noul == null ? 0 : 1)
                + (builder.choice == null ? 0 : 1)
                + (builder.score == null ? 0 : 1);
        ensureTrue(values == 1, "JudgeAnswer must contain exactly one of 'noul', 'choice', or 'score'");
        ensureTrue(builder.noul == null || Double.isFinite(builder.noul), "noul must be a finite number");
        ensureTrue(
                builder.confidence == null || Double.isFinite(builder.confidence),
                "confidence must be a finite number");
        this.noul = builder.noul == null ? null : ensureBetween(builder.noul, 0, 1, "noul");
        this.choice = builder.choice == null ? null : ensureNotBlank(builder.choice, "choice");
        this.score = builder.score;
        this.confidence = builder.confidence == null
                ? null
                : ensureBetween(builder.confidence, 0, 1, "confidence");
    }

    public Double noul() {
        return noul;
    }

    public String choice() {
        return choice;
    }

    public Double score() {
        return score;
    }

    public Double confidence() {
        return confidence;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JudgeAnswer that)) return false;
        return Objects.equals(noul, that.noul)
                && Objects.equals(choice, that.choice)
                && Objects.equals(score, that.score)
                && Objects.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(noul, choice, score, confidence);
    }

    @Override
    public String toString() {
        return "JudgeAnswer{noul=" + noul + ", choice=" + choice + ", score=" + score
                + ", confidence=" + confidence + '}';
    }

    public static final class Builder {
        private Double noul;
        private String choice;
        private Double score;
        private Double confidence;

        public Builder noul(Double noul) {
            this.noul = noul;
            return this;
        }

        public Builder choice(String choice) {
            this.choice = choice;
            return this;
        }

        public Builder score(Double score) {
            this.score = score;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public JudgeAnswer build() {
            return new JudgeAnswer(this);
        }
    }
}
