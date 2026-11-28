package dev.langchain4j.service.tool;

import dev.langchain4j.observability.api.event.CompensationReason;

/**
 * The {@link ToolService.FailedStep} of a round.
 */
record DefaultFailedStep(String toolName, CompensationReason reason) implements ToolService.FailedStep {}
