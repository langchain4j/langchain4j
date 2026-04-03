package dev.langchain4j.invocation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @since 1.6.0
 */
public class DefaultInvocationContext implements InvocationContext {

    private final UUID invocationId;
    private final String interfaceName;
    private final String methodName;
    private final List<Object> methodArguments = new ArrayList<>();
    private final Object chatMemoryId;
    private final InvocationParameters invocationParameters;
    private final Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters;
    private final Instant timestamp;

    public DefaultInvocationContext(InvocationContext.Builder builder) {
        this.invocationId = builder.invocationId();
        this.interfaceName = builder.interfaceName();
        this.methodName = builder.methodName();
        this.methodArguments.addAll(builder.methodArguments());
        this.chatMemoryId = builder.chatMemoryId();
        this.invocationParameters = builder.invocationParameters();
        this.managedParameters = builder.managedParameters();
        this.timestamp = builder.timestamp();
    }

    @Override
    public UUID invocationId() {
        return invocationId;
    }

    @Override
    public String interfaceName() {
        return interfaceName;
    }

    @Override
    public String methodName() {
        return methodName;
    }

    @Override
    public List<Object> methodArguments() {
        return methodArguments;
    }

    @Override
    public Object chatMemoryId() {
        return chatMemoryId;
    }

    @Override
    public InvocationParameters invocationParameters() {
        return invocationParameters;
    }

    @Override
    public Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters() {
        return managedParameters;
    }

    @Override
    public Instant timestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        DefaultInvocationContext that = (DefaultInvocationContext) object;
        return Objects.equals(invocationId, that.invocationId)
                && Objects.equals(interfaceName, that.interfaceName)
                && Objects.equals(methodName, that.methodName)
                && Objects.equals(methodArguments, that.methodArguments)
                && Objects.equals(chatMemoryId, that.chatMemoryId)
                && Objects.equals(invocationParameters, that.invocationParameters)
                && Objects.equals(managedParameters, that.managedParameters)
                && Objects.equals(timestamp, that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                invocationId,
                interfaceName,
                methodName,
                methodArguments,
                chatMemoryId,
                invocationParameters,
                managedParameters,
                timestamp);
    }

    @Override
    public String toString() {
        return "DefaultInvocationContext{" +
                "invocationId=" + invocationId +
                ", interfaceName='" + interfaceName + '\'' +
                ", methodName='" + methodName + '\'' +
                ", methodArguments=" + methodArguments +
                ", chatMemoryId=" + chatMemoryId +
                ", invocationParameters=" + invocationParameters +
                ", managedParameters=" + managedParameters +
                ", timestamp=" + timestamp +
                '}';
    }
}
