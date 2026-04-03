package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;
import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * @since 1.2.0
 */
@Experimental
public class BeforeToolExecution {

    private final ToolExecutionRequest request;

    private BeforeToolExecution(Builder builder) {
        this.request = ensureNotNull(builder.request, "request");
    }

    /**
     * Returns the tool execution request that is about to be executed.
     *
     * @return the tool execution request
     */
    public ToolExecutionRequest request() {
        return request;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BeforeToolExecution that = (BeforeToolExecution) obj;
        return Objects.equals(request, that.request);
    }

    @Override
    public String toString() {
        return "BeforeToolExecution {" + " request = " + request + " }";
    }

    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(request);
        return h;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private ToolExecutionRequest request;

        private Builder() {}

        public Builder request(ToolExecutionRequest request) {
            this.request = request;
            return this;
        }

        public BeforeToolExecution build() {
            return new BeforeToolExecution(this);
        }
    }
}
