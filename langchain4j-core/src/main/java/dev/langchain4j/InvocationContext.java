package dev.langchain4j;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

/**
 * TODO
 *
 * @since 1.4.0
 */
public class InvocationContext { // TODO name, module, package

    private final ExtraParameters extraParameters; // TODO name

    public InvocationContext(ExtraParameters extraParameters) {
        this.extraParameters = ensureNotNull(extraParameters, "extraParameters");
    }

    public ExtraParameters extraParameters() { // TODO name
        return extraParameters;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        InvocationContext that = (InvocationContext) object;
        return Objects.equals(extraParameters, that.extraParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(extraParameters);
    }

    @Override
    public String toString() {
        return "InvocationContext{" + // TODO names
                "extraParameters=" + extraParameters +
                '}';
    }
}
