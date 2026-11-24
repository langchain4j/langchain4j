package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.service.tool.ToolExecutionResult;
import java.util.Optional;

record DefaultGuardedToolExecution(
        ToolExecutionRequest request, ToolExecutionResult result, Optional<ToolGuardrailStage> failedStage)
        implements GuardedToolExecution {

    DefaultGuardedToolExecution {
        ensureNotNull(request, "request");
        ensureNotNull(result, "result");
        ensureNotNull(failedStage, "failedStage");
    }

    DefaultGuardedToolExecution(GuardedToolExecution.Builder builder) {
        this(builder.request, builder.result, Optional.ofNullable(builder.failedStage));
    }
}
