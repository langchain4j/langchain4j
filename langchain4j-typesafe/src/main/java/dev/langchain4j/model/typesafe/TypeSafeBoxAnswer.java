package dev.langchain4j.model.typesafe;

import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.response.DecisionAnswer;
import java.util.Objects;

/**
 * The answer to a {@link TypeSafeBoxQuestion}: the smallest box around the target, given as its four edges.
 * <p>
 * Each edge is a fraction of the image's size, measured from the top-left corner: {@link #left()} and
 * {@link #right()} are fractions of the width, {@link #top()} and {@link #bottom()} fractions of the height. To get
 * pixels, multiply them by the image's width or height.
 *
 * @since 1.23.0
 */
@Experimental
public final class TypeSafeBoxAnswer implements DecisionAnswer {

    private final double top;
    private final double bottom;
    private final double left;
    private final double right;
    private final Double confidence;

    private TypeSafeBoxAnswer(Builder builder) {
        this.top = ensureBetween(builder.top, 0, 1, "top");
        this.bottom = ensureBetween(builder.bottom, 0, 1, "bottom");
        this.left = ensureBetween(builder.left, 0, 1, "left");
        this.right = ensureBetween(builder.right, 0, 1, "right");
        ensureTrue(top <= bottom, "top must not be greater than bottom");
        ensureTrue(left <= right, "left must not be greater than right");
        this.confidence = builder.confidence == null ? null : ensureBetween(builder.confidence, 0, 1, "confidence");
    }

    /**
     * The top edge, as a fraction of the image's height, from 0 to 1.
     */
    public double top() {
        return top;
    }

    /**
     * The bottom edge, as a fraction of the image's height, from 0 to 1.
     */
    public double bottom() {
        return bottom;
    }

    /**
     * The left edge, as a fraction of the image's width, from 0 to 1.
     */
    public double left() {
        return left;
    }

    /**
     * The right edge, as a fraction of the image's width, from 0 to 1.
     */
    public double right() {
        return right;
    }

    /**
     * How sure the model is of the box, from 0 to 1, or {@code null} if not reported.
     */
    public Double confidence() {
        return confidence;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypeSafeBoxAnswer that)) return false;
        return Double.compare(top, that.top) == 0
                && Double.compare(bottom, that.bottom) == 0
                && Double.compare(left, that.left) == 0
                && Double.compare(right, that.right) == 0
                && Objects.equals(confidence, that.confidence);
    }

    @Override
    public int hashCode() {
        return Objects.hash(top, bottom, left, right, confidence);
    }

    @Override
    public String toString() {
        return "TypeSafeBoxAnswer{top=" + top + ", bottom=" + bottom + ", left=" + left + ", right=" + right
                + ", confidence=" + confidence + '}';
    }

    public static final class Builder {

        private Double top;
        private Double bottom;
        private Double left;
        private Double right;
        private Double confidence;

        public Builder top(Double top) {
            this.top = top;
            return this;
        }

        public Builder bottom(Double bottom) {
            this.bottom = bottom;
            return this;
        }

        public Builder left(Double left) {
            this.left = left;
            return this;
        }

        public Builder right(Double right) {
            this.right = right;
            return this;
        }

        public Builder confidence(Double confidence) {
            this.confidence = confidence;
            return this;
        }

        public TypeSafeBoxAnswer build() {
            return new TypeSafeBoxAnswer(this);
        }
    }
}
