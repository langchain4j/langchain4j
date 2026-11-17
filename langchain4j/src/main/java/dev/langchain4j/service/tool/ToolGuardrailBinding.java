package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.service.guardrail.ToolGuardrailService.ToolKey;
import java.util.Objects;

/**
 * What the tool guardrails need to know about one tool: where it comes from, and the key a
 * {@link ToolGuardrailService} returned for it, to execute its guardrails with.
 */
@Internal
public final class ToolGuardrailBinding {

    private final ToolSource toolSource;
    private final ToolGuardrailService service;
    private final ToolKey key;

    ToolGuardrailBinding(ToolSource toolSource, ToolGuardrailService service, ToolKey key) {
        this.toolSource = ensureNotNull(toolSource, "toolSource");
        this.service = ensureNotNull(service, "service");
        this.key = ensureNotNull(key, "key");
    }

    static ToolGuardrailBinding bind(ToolGuardrailService service, Class<?> toolClass, AiServiceTool tool) {
        return new ToolGuardrailBinding(tool.toolSource(), service, service.bind(toolClass, tool));
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

    /**
     * Whether the tool has tool input guardrails to execute. A bound tool can have none, so this is checked before
     * building a request for them.
     */
    public boolean hasInputGuardrails() {
        return service.hasInputGuardrails(key);
    }

    /**
     * Whether the tool has tool output guardrails to execute. A bound tool can have none, so this is checked before
     * building a request for them.
     */
    public boolean hasOutputGuardrails() {
        return service.hasOutputGuardrails(key);
    }

    @Override
    public boolean equals(Object o) {
        return (this == o)
                || ((o instanceof ToolGuardrailBinding that)
                        && Objects.equals(toolSource, that.toolSource)
                        && (service == that.service)
                        && Objects.equals(key, that.key));
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolSource, System.identityHashCode(service), key);
    }

    @Override
    public String toString() {
        return "ToolGuardrailBinding{toolSource=%s, key=%s}".formatted(toolSource, key);
    }
}
