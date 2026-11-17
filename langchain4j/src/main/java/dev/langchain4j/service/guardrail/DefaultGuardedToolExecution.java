package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;

record DefaultGuardedToolExecution(ToolExecutionRequest request, ToolExecutionResult result)
        implements GuardedToolExecution {

    DefaultGuardedToolExecution {
        ensureNotNull(request, "request");
        ensureNotNull(result, "result");
    }

    DefaultGuardedToolExecution(GuardedToolExecution.Builder builder) {
        this(builder.request, builder.result);
    }
}
