package dev.langchain4j.service.guardrail;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * Describes the tool a guardrail is guarding.
 *
 * @param specification the tool specification exposed to the LLM
 * @param toolInstance  the object declaring the {@code @Tool} method, or {@code null} for tools that have
 *                      no declaring object, such as MCP tools or tools registered programmatically
 * @since 1.19.0
 */
@Experimental
public record ToolMetadata(ToolSpecification specification, Object toolInstance) {

    public ToolMetadata {
        ensureNotNull(specification, "specification");
    }

    public static ToolMetadata from(ToolSpecification specification) {
        return new ToolMetadata(specification, null);
    }

    public String toolName() {
        return specification.name();
    }

    public String description() {
        return specification.description();
    }
}
