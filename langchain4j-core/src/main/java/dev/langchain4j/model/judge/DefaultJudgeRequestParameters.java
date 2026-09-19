package dev.langchain4j.model.judge;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Experimental;
import java.util.Objects;

/** Default implementation of {@link JudgeRequestParameters}. */
@Experimental
public class DefaultJudgeRequestParameters implements JudgeRequestParameters {

    private final String modelName;

    protected DefaultJudgeRequestParameters(Builder<?> builder) {
        this.modelName = builder.modelName;
    }

    @Override
    public String modelName() {
        return modelName;
    }

    @Override
    public JudgeRequestParameters overrideWith(JudgeRequestParameters that) {
        if (that == null) {
            return this;
        }
        return builder().overrideWith(this).overrideWith(that).build();
    }

    public static Builder<?> builder() {
        return new Builder<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultJudgeRequestParameters that = (DefaultJudgeRequestParameters) o;
        return Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelName);
    }

    @Override
    public String toString() {
        return "DefaultJudgeRequestParameters{modelName=" + modelName + '}';
    }

    public static class Builder<B extends Builder<B>> {
        protected String modelName;

        public B modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        public B overrideWith(JudgeRequestParameters parameters) {
            if (parameters != null) {
                this.modelName = getOrDefault(parameters.modelName(), this.modelName);
            }
            return self();
        }

        @SuppressWarnings("unchecked")
        protected B self() {
            return (B) this;
        }

        public DefaultJudgeRequestParameters build() {
            return new DefaultJudgeRequestParameters(this);
        }
    }
}
