package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.service.guardrail.ToolGuardrailService.ToolKey;
import java.util.Objects;

/**
 * What the tool guardrails need to know about one tool: its specification, where it comes from, and the key a
 * {@link ToolGuardrailService} returned for it, to execute its guardrails with.
 *
 * @since 1.25.0
 */
@Internal
public final class ToolGuardrailBinding {

    private final ToolSpecification toolSpecification;
    private final ToolSource toolSource;
    private final ToolGuardrailService service;
    private final ToolKey key;

    ToolGuardrailBinding(
            ToolSpecification toolSpecification, ToolSource toolSource, ToolGuardrailService service, ToolKey key) {
        this.toolSpecification = ensureNotNull(toolSpecification, "toolSpecification");
        this.toolSource = ensureNotNull(toolSource, "toolSource");
        this.service = ensureNotNull(service, "service");
        this.key = ensureNotNull(key, "key");
    }

    static ToolGuardrailBinding bind(ToolGuardrailService service, Class<?> toolClass, AiServiceTool tool) {
        return new ToolGuardrailBinding(
                tool.toolSpecification(), tool.toolSource(), service, service.bind(toolClass, tool));
    }

    /**
     * The specification of the tool.
     */
    public ToolSpecification toolSpecification() {
        return toolSpecification;
    }

    /**
     * Where the tool comes from.
     */
    public ToolSource toolSource() {
        return toolSource;
    }

    /**
     * The service that executes the guardrails of the tool.
     */
    public ToolGuardrailService service() {
        return service;
    }

    /**
     * The key of the tool in {@link #service()}.
     */
    public ToolKey key() {
        return key;
    }

    @Override
    public boolean equals(Object o) {
        return (this == o)
                || ((o instanceof ToolGuardrailBinding that)
                        && Objects.equals(toolSpecification, that.toolSpecification)
                        && Objects.equals(toolSource, that.toolSource)
                        && (service == that.service)
                        && Objects.equals(key, that.key));
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolSpecification, toolSource, System.identityHashCode(service), key);
    }

    @Override
    public String toString() {
        return "ToolGuardrailBinding{toolName=%s, toolSource=%s, key=%s}"
                .formatted(toolSpecification.name(), toolSource, key);
    }
}
