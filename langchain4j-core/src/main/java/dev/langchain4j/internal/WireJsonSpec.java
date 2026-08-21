package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.Internal;
import java.util.Objects;

/**
 * Describes how a provider's wire DTOs are mapped to JSON, in terms that carry no dependency on
 * a particular JSON library. This is what lets the underlying codec be swapped.
 */
@Internal
public class WireJsonSpec {

    /**
     * How Java property names are translated to JSON field names.
     */
    public enum PropertyNaming {
        /** Property names are used as declared. */
        IDENTITY,
        /** {@code maxCompletionTokens} becomes {@code max_completion_tokens}. */
        SNAKE_CASE
    }

    /**
     * Which property values are written.
     */
    public enum Inclusion {
        ALWAYS,
        NON_NULL,
        NON_EMPTY
    }

    private final PropertyNaming propertyNaming;
    private final Inclusion inclusion;
    private final boolean prettyPrint;

    public WireJsonSpec(Builder builder) {
        this.propertyNaming = getOrDefault(builder.propertyNaming, PropertyNaming.IDENTITY);
        this.inclusion = getOrDefault(builder.inclusion, Inclusion.ALWAYS);
        this.prettyPrint = getOrDefault(builder.prettyPrint, false);
    }

    public PropertyNaming propertyNaming() {
        return propertyNaming;
    }

    public Inclusion inclusion() {
        return inclusion;
    }

    public boolean prettyPrint() {
        return prettyPrint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WireJsonSpec that)) return false;
        return prettyPrint == that.prettyPrint
                && propertyNaming == that.propertyNaming
                && inclusion == that.inclusion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(propertyNaming, inclusion, prettyPrint);
    }

    @Override
    public String toString() {
        return "WireJsonSpec{" + "propertyNaming="
                + propertyNaming + ", inclusion="
                + inclusion + ", prettyPrint="
                + prettyPrint + "}";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private PropertyNaming propertyNaming;
        private Inclusion inclusion;
        private Boolean prettyPrint;

        public Builder propertyNaming(PropertyNaming propertyNaming) {
            this.propertyNaming = propertyNaming;
            return this;
        }

        public Builder inclusion(Inclusion inclusion) {
            this.inclusion = inclusion;
            return this;
        }

        public Builder prettyPrint(Boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
            return this;
        }

        public WireJsonSpec build() {
            return new WireJsonSpec(this);
        }
    }
}
