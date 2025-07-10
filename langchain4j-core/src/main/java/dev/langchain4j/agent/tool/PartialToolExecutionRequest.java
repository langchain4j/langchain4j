package dev.langchain4j.agent.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.util.Objects;

/**
 * TODO
 */
public class PartialToolExecutionRequest {

    private final int index;
    private final String toolId;
    private final String toolName;
    private final String partialToolArguments;

    public PartialToolExecutionRequest(Builder builder) {
        this.index = builder.index;
        this.toolId = builder.toolId;
        this.toolName = ensureNotBlank(builder.toolName, "toolName");
        this.partialToolArguments = ensureNotBlank(builder.partialToolArguments, "partialToolArguments");
    }

    public int index() {
        return index;
    }

    public String toolId() {
        return toolId;
    }

    public String toolName() {
        return toolName;
    }

    public String partialToolArguments() {
        return partialToolArguments;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        PartialToolExecutionRequest that = (PartialToolExecutionRequest) object;
        return index == that.index
                && Objects.equals(toolId, that.toolId)
                && Objects.equals(toolName, that.toolName)
                && Objects.equals(partialToolArguments, that.partialToolArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, toolId, toolName, partialToolArguments);
    }

    @Override
    public String toString() {
        return "PartialToolExecutionRequest{" +
                "index=" + index +
                ", toolId='" + toolId + '\'' +
                ", toolName='" + toolName + '\'' +
                ", partialToolArguments='" + partialToolArguments + '\'' +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private int index;
        private String toolId;
        private String toolName;
        private String partialToolArguments;

        public Builder index(int index) {
            this.index = index;
            return this;
        }

        public Builder toolId(String toolId) {
            this.toolId = toolId;
            return this;
        }

        public Builder toolName(String toolName) {
            this.toolName = toolName;
            return this;
        }

        public Builder partialToolArguments(String partialToolArguments) {
            this.partialToolArguments = partialToolArguments;
            return this;
        }

        public PartialToolExecutionRequest build() {
            return new PartialToolExecutionRequest(this);
        }
    }
}
