package dev.langchain4j.agent.tool;

import static dev.langchain4j.internal.Utils.quoted;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.Objects;

/**
 * TODO
 */
public class PartialToolCall {

    private final int index;
    private final String id;
    private final String name;
    private final String partiaArguments; // TODO name

    public PartialToolCall(Builder builder) {
        this.index = builder.index;
        this.id = builder.id;
        this.name = ensureNotBlank(builder.name, "name");
        this.partiaArguments = ensureNotBlank(builder.partiaArguments, "partiaArguments");
    }

    public int index() {
        return index;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String partialArguments() {
        return partiaArguments;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        PartialToolCall that = (PartialToolCall) object;
        return index == that.index
                && Objects.equals(id, that.id)
                && Objects.equals(name, that.name)
                && Objects.equals(partiaArguments, that.partiaArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, id, name, partiaArguments);
    }

    @Override
    public String toString() {
        return "PartialToolCall{" +
                "index=" + index +
                ", id=" + quoted(id) +
                ", name=" + quoted(name) +
                ", partiaArguments=" + quoted(partiaArguments) +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int index;
        private String id;
        private String name;
        private String partiaArguments;

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
            this.partiaArguments = partialArguments;
            return this;
        }

        public PartialToolCall build() {
            return new PartialToolCall(this);
        }
    }
}
