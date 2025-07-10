package dev.langchain4j.agent.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

public class CompleteToolExecutionRequest {

    private final int index;
    private final ToolExecutionRequest request;

    public CompleteToolExecutionRequest(int index, ToolExecutionRequest request) {
        this.index = index;
        this.request = ensureNotNull(request, "request");
    }

    public int index() {
        return index;
    }

    public ToolExecutionRequest request() {
        return request;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CompleteToolExecutionRequest that = (CompleteToolExecutionRequest) object;
        return index == that.index
                && Objects.equals(request, that.request);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, request);
    }

    @Override
    public String toString() {
        return "CompleteToolExecutionRequest{" +
                "index=" + index +
                ", request=" + request +
                '}';
    }
}
