package dev.langchain4j.service.tool;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Internal;
import dev.langchain4j.service.guardrail.ToolGuardrailService;
import dev.langchain4j.service.guardrail.ToolGuardrailService.ToolKey;
import java.util.Objects;

/**
 * What the tool guardrails need to know about one tool: the {@link ToolGuardrailService} that executes its guardrails,
 * and the key the service returned for it, which also carries its specification and source.
 *
 * @since 1.25.0
 */
@Internal
public final class ToolGuardrailBinding {

    private final ToolGuardrailService service;
    private final ToolKey key;

    ToolGuardrailBinding(ToolGuardrailService service, ToolKey key) {
        this.service = ensureNotNull(service, "service");
        this.key = ensureNotNull(key, "key");
    }

    static ToolGuardrailBinding bind(ToolGuardrailService service, Class<?> toolClass, AiServiceTool tool) {
        return new ToolGuardrailBinding(service, service.bind(toolClass, tool));
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
                        && (service == that.service)
                        && Objects.equals(key, that.key));
    }

    @Override
    public int hashCode() {
        return Objects.hash(System.identityHashCode(service), key);
    }

    @Override
    public String toString() {
        return "ToolGuardrailBinding{toolName=%s, toolSource=%s, key=%s}"
                .formatted(key.toolName(), key.toolSource(), key);
    }
}
