package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Exceptions.runtime;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.function.Function;

public enum ToolHallucinationStrategy implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {
    THROW_EXCEPTION;

    public ToolExecutionResultMessage apply(ToolExecutionRequest toolExecutionRequest) {
        switch (this) {
            case THROW_EXCEPTION -> {
                throw runtime(
                        "Something is wrong, the tool %s was called but it is not a part of the available tools",
                        toolExecutionRequest.name());
            }
        }
        throw new UnsupportedOperationException();
    }
}
