package dev.langchain4j.agent.tool;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

/**
 * Represents an LLM-generated request to execute a tool.
 */
public class ToolExecutionRequest {
    private final String id;
    private final String name;
    private final String arguments;

    /**
     * Creates a {@link ToolExecutionRequest} from a {@link Builder}.
     * @param builder the builder.
     */
    private ToolExecutionRequest(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.arguments = builder.arguments;
    }

    /**
     * Returns the id of the tool.
     * @return the id of the tool.
     */
    public String id() {
        return id;
    }

    /**
     * Returns the name of the tool.
     * @return the name of the tool.
     */
    public String name() {
        return name;
    }

    /**
     * Returns the arguments of the tool.
     * @return the arguments of the tool.
     */
    public String arguments() {
        return arguments;
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof ToolExecutionRequest
                && equalTo((ToolExecutionRequest) another);
    }

    private boolean equalTo(ToolExecutionRequest another) {
        return Objects.equals(id, another.id)
                && Objects.equals(name, another.name)
                && Objects.equals(arguments, another.arguments);
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(id);
        h += (h << 5) + Objects.hashCode(name);
        h += (h << 5) + Objects.hashCode(arguments);
        return h;
    }

    @Override
    public String toString() {
        return "ToolExecutionRequest {"
                + " id = " + quoted(id)
                + ", name = " + quoted(name)
                + ", arguments = " + quoted(arguments)
                + " }";
    }

    /**
     * Creates builder to build {@link ToolExecutionRequest}.
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * {@code ToolExecutionRequest} builder static inner class.
     */
    public static final class Builder {
        private String id;
        private String name;
        private String arguments;

        /**
         * Creates a builder for {@code ToolExecutionRequest}.
         */
        private Builder() {
        }

        /**
         * Sets the {@code id}.
         * @param id the {@code id}
         * @return the {@code Builder}
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the {@code name}.
         * @param name the {@code name}
         * @return the {@code Builder}
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the {@code arguments}.
         * @param arguments the {@code arguments}
         * @return the {@code Builder}
         */
        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        /**
         * Returns a {@code ToolExecutionRequest} built from the parameters previously set.
         * @return a {@code ToolExecutionRequest}
         */
        public ToolExecutionRequest build() {
            return new ToolExecutionRequest(this);
        }
    }
}
