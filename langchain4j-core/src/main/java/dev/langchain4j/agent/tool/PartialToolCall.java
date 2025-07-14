package dev.langchain4j.agent.tool;

import dev.langchain4j.Experimental;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.Objects;

/**
 * TODO
 * @since 1.2.0
 */
@Experimental
public class PartialToolCall {
    // TODO location

    private final int index;
    private final String id;
    private final String name;
    private final String partialArguments;

    public PartialToolCall(Builder builder) {
        this.index = builder.index;
        this.id = builder.id;
        this.name = ensureNotBlank(builder.name, "name");
        this.partialArguments = ensureNotBlank(builder.partialArguments, "partialArguments");
    }

    /**
     * TODO
     */
    public int index() {
        return index;
    }

    /**
     * Please note that some LLM providers (e.g., Google, Ollama) do not include a tool call ID.
     */
    public String id() {
        return id;
    }

    /**
     * TODO
     */
    public String name() {
        return name;
    }

    /**
     * TODO
     */
    public String partialArguments() {
        return partialArguments;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        PartialToolCall that = (PartialToolCall) object;
        return index == that.index
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(partialArguments, that.partialArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, id, name, partialArguments);
    }

    @Override
    public String toString() {
        return "PartialToolCall{" +
                "index=" + index +
                ", id=" + quoted(id) +
                ", name=" + quoted(name) +
                ", partialArguments=" + quoted(partialArguments) +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int index;
        private String id;
        private String name;
        private String partialArguments;

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder partialArguments(String partialArguments) {
            this.partialArguments = partialArguments;
            return this;
        }

        public PartialToolCall build() {
            return new PartialToolCall(this);
        }
    }
}
