package dev.langchain4j.service.decision;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * The question a {@link ThresholdProvider} is asked the threshold for.
 *
 * @since 1.21.0
 */
@Experimental
public final class ThresholdContext {

    private final Class<?> serviceInterface;
    private final Method method;
    private final String questionName;
    private final String modelName;

    private ThresholdContext(Builder builder) {
        this.serviceInterface = ensureNotNull(builder.serviceInterface, "serviceInterface");
        this.method = ensureNotNull(builder.method, "method");
        this.questionName = ensureNotBlank(builder.questionName, "questionName");
        this.modelName = builder.modelName;
    }

    /**
     * The decision service interface.
     */
    public Class<?> serviceInterface() {
        return serviceInterface;
    }

    /**
     * The method that is invoked.
     */
    public Method method() {
        return method;
    }

    /**
     * The name of the question: the name of the method, or the name of the field for methods returning an object.
     */
    public String questionName() {
        return questionName;
    }

    /**
     * The name of the model that answered the question, as reported by the provider (which can be more specific than
     * the requested name, for example a pinned version instead of an alias). If the provider does not report it, the
     * requested model name, or {@code null} if it is not known.
     */
    public String modelName() {
        return modelName;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThresholdContext that)) return false;
        return serviceInterface.equals(that.serviceInterface)
                && method.equals(that.method)
                && questionName.equals(that.questionName)
                && Objects.equals(modelName, that.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceInterface, method, questionName, modelName);
    }

    @Override
    public String toString() {
        return "ThresholdContext{serviceInterface=" + serviceInterface.getName() + ", method=" + method.getName()
                + ", questionName=" + questionName + ", modelName=" + modelName + '}';
    }

    public static final class Builder {

        private Class<?> serviceInterface;
        private Method method;
        private String questionName;
        private String modelName;

        public Builder serviceInterface(Class<?> serviceInterface) {
            this.serviceInterface = serviceInterface;
            return this;
        }

        public Builder method(Method method) {
            this.method = method;
            return this;
        }

        public Builder questionName(String questionName) {
            this.questionName = questionName;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public ThresholdContext build() {
            return new ThresholdContext(this);
        }
    }
}
