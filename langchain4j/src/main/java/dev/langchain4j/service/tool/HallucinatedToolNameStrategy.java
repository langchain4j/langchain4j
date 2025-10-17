package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.Exceptions.runtime;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import java.util.function.Function;

public enum HallucinatedToolNameStrategy implements Function<ToolExecutionRequest, ToolExecutionResultMessage> {
    THROW_EXCEPTION,
    LET_LLM_TRY;

    public ToolExecutionResultMessage apply(ToolExecutionRequest toolExecutionRequest) {
        switch (this) {
            case THROW_EXCEPTION -> {
                throw runtime(
                        "The LLM is trying to execute the '%s' tool, but no such tool exists. Most likely, it is a "
                                + "hallucination. You can override this default strategy by setting the hallucinatedToolNameStrategy on the AiService",
                        toolExecutionRequest.name());
            }
            case LET_LLM_TRY -> {
                String s = toolExecutionRequest.name()
                        + "' is not a tool. please check the tool specifications again and use available tools.";
                return ToolExecutionResultMessage.from(null, toolExecutionRequest.name(), s);
            }
        }
        throw new UnsupportedOperationException();
    }
}
