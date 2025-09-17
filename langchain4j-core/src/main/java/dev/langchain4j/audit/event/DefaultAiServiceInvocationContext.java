package dev.langchain4j.audit.event;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dev.langchain4j.audit.api.event.AiServiceInvocationContext;

/**
 * Default implementation of the {@link AiServiceInvocationContext} interface used to represent
 * information about the source of an interaction, including details such as the unique
 * interaction ID, interface name, method name, method arguments, and optional memory ID.
 * Instances of this class are immutable and can be constructed using the nested {@link Builder}.
 */
public class DefaultAiServiceInvocationContext implements AiServiceInvocationContext {
    private final UUID interactionId = UUID.randomUUID();
    private final String interfaceName;
    private final String methodName;
    private final List<Object> methodArguments = new ArrayList<>();
    private final Object memoryId;
    private final Instant timestamp;

    public DefaultAiServiceInvocationContext(Builder builder) {
        ensureNotNull(builder, "builder");
        this.interfaceName = ensureNotBlank(builder.getInterfaceName(), "interfaceName");
        this.methodName = ensureNotBlank(builder.getMethodName(), "methodName");
        this.methodArguments.addAll(builder.getMethodArguments());
        this.memoryId = builder.getMemoryId();
        this.timestamp = Optional.ofNullable(builder.getTimestamp()).orElseGet(Instant::now);
    }

    @Override
    public UUID interactionId() {
        return this.interactionId;
    }

    @Override
    public String interfaceName() {
        return this.interfaceName;
    }

    @Override
    public String methodName() {
        return this.methodName;
    }

    @Override
    public List<Object> methodArguments() {
        return Collections.unmodifiableList(this.methodArguments);
    }

    @Override
    public Optional<Object> memoryId() {
        return Optional.ofNullable(this.memoryId);
    }

    @Override
    public Instant timestamp() {
        return this.timestamp;
    }
}
