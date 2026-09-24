package dev.langchain4j.model.structureddecision;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import java.util.List;
import java.util.Objects;

/** Contrastive criteria for the true outcome of a {@link NoulQuestion}. */
@Experimental
public final class NoulCriteria {

    private final String what;
    private final String notFor;
    private final List<String> examples;

    private NoulCriteria(Builder builder) {
        this.what = builder.what;
        this.notFor = builder.notFor;
        this.examples = copy(builder.examples);
        ensureTrue(
                isNotNullOrBlank(what) || isNotNullOrBlank(notFor) || !examples.isEmpty(),
                "NoulCriteria must define at least one of 'what', 'notFor', or 'examples'");
    }

    public String what() {
        return what;
    }

    public String notFor() {
        return notFor;
    }

    public List<String> examples() {
        return examples;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NoulCriteria that)) return false;
        return Objects.equals(what, that.what)
                && Objects.equals(notFor, that.notFor)
                && Objects.equals(examples, that.examples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(what, notFor, examples);
    }

    @Override
    public String toString() {
        return "NoulCriteria{what=" + what + ", notFor=" + notFor + ", examples=" + examples + '}';
    }

    public static final class Builder {
        private String what;
        private String notFor;
        private List<String> examples;

        public Builder what(String what) {
            this.what = what;
            return this;
        }

        public Builder notFor(String notFor) {
            this.notFor = notFor;
            return this;
        }

        public Builder examples(List<String> examples) {
            this.examples = examples;
            return this;
        }

        public NoulCriteria build() {
            return new NoulCriteria(this);
        }
    }
}
