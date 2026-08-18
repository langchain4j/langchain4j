package dev.langchain4j.service.guardrail;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * Validates a tool call before the tool runs.
 * <p>
 * This is the only place where a tool call can be inspected together with its arguments and stopped:
 * a {@link dev.langchain4j.service.tool.ToolProvider} decides which tools are <em>offered</em>, and an
 * {@link dev.langchain4j.guardrail.OutputGuardrail} only runs once every tool has already executed.
 * <p>
 * Example - refuse writes to an immutable area, and let the LLM see why:
 * <pre>{@code
 * public class ImmutableSourcesGuardrail implements ToolInputGuardrail {
 *
 *     @Override
 *     public ToolInputGuardrailResult validate(ToolInputGuardrailRequest request) {
 *         Object pageId = request.argumentsAsMap().get("pageId");
 *         if (pageId instanceof String id && id.startsWith("raw/")) {
 *             return failure("raw/ is read-only. Write your synthesis under pages/ instead.");
 *         }
 *         return success();
 *     }
 * }
 * }</pre>
 *
 * @since 1.19.0
 */
@Experimental
public interface ToolInputGuardrail extends ToolGuardrail<ToolInputGuardrailRequest, ToolInputGuardrailResult> {

    @Override
    ToolInputGuardrailResult validate(ToolInputGuardrailRequest request);

    /**
     * The tool call may proceed unchanged.
     */
    default ToolInputGuardrailResult success() {
        return ToolInputGuardrailResult.success();
    }

    /**
     * The tool call may proceed, with the given request replacing the original one.
     * Subsequent guardrails in the chain see the rewritten request.
     */
    default ToolInputGuardrailResult successWith(ToolExecutionRequest rewrittenRequest) {
        return ToolInputGuardrailResult.successWith(rewrittenRequest);
    }

    /**
     * The tool must not run. The tool call is turned into an error result that is handed back to the LLM,
     * which may then correct itself and try something else.
     */
    default ToolInputGuardrailResult failure(String message) {
        return new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure(message), false);
    }

    default ToolInputGuardrailResult failure(String message, Throwable cause) {
        return new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure(message, cause), false);
    }

    /**
     * The tool must not run and the whole invocation must be aborted with a {@link ToolGuardrailException}.
     * Use this when letting the LLM retry would itself be unsafe.
     */
    default ToolInputGuardrailResult fatal(String message) {
        return new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure(message), true);
    }

    default ToolInputGuardrailResult fatal(String message, Throwable cause) {
        return new ToolInputGuardrailResult(new ToolInputGuardrailResult.Failure(message, cause), true);
    }
}
