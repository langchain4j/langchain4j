package dev.langchain4j.invocation;

import dev.langchain4j.model.chat.ChatModel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * Represents the context of a single AI Service invocation.
 * A new instance is created each time an AI Service method is invoked,
 * and it exists until the end of the AI Service invocation,
 * potentially spanning multiple calls to the {@link ChatModel}.
 *
 * @since 1.6.0
 */
public interface InvocationContext {

    /**
     * Unique identifier for an entire AI Service invocation
     */
    UUID invocationId();

    /**
     * The fully-qualified name of the AI Service interface where the invocation was initiated from
     *
     * @see #methodName()
     */
    String interfaceName();

    /**
     * The method name on {@link #interfaceName()} where the invocation was initiated from
     */
    String methodName();

    /**
     * The arguments passed into the AI Service method
     */
    List<Object> methodArguments();

    /**
     * The chat memory id parameter of the method
     */
    Object chatMemoryId();

    /**
     * The invocation parameters
     */
    InvocationParameters invocationParameters();

    /**
     * LangChain4j managed parameters
     * @since 1.8.0
     */
    default Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters() {
        return Map.of();
    }

    /**
     * Retrieves the point in time when the invocation occurred.
     */
    Instant timestamp();

    /**
     * Converts the current instance of {@link DefaultInvocationContext} into a {@link Builder},
     * pre-populated with the current values of the instance.
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates a new instance of the {@code Builder} class for constructing
     * instances of {@link DefaultInvocationContext}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * A builder class to create {@link InvocationContext} instances.
     */
    class Builder {

        private UUID invocationId;
        private String interfaceName;
        private String methodName;
        private List<@NonNull Object> methodArguments = new ArrayList<>();
        private Object chatMemoryId;
        private InvocationParameters invocationParameters;
        private Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters;
        private Instant timestamp;

        protected Builder() {}

        protected Builder(InvocationContext invocationContext) {
            invocationId(invocationContext.invocationId());
            interfaceName(invocationContext.interfaceName());
            methodName(invocationContext.methodName());
            methodArguments(invocationContext.methodArguments());
            chatMemoryId(invocationContext.chatMemoryId());
            invocationParameters(invocationContext.invocationParameters());
            managedParameters(invocationContext.managedParameters());
            timestamp(invocationContext.timestamp());
        }

        /**
         * Sets the invocation ID.
         */
        public Builder invocationId(UUID invocationId) {
            this.invocationId = invocationId;
            return this;
        }

        /**
         * Sets the name of the interface associated with the builder.
         */
        public Builder interfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
            return this;
        }

        /**
         * Sets the name of the method associated with the builder.
         */
        public Builder methodName(String methodName) {
            this.methodName = methodName;
            return this;
        }

        /**
         * Sets the method arguments for the builder. If the provided list of method arguments is not null,
         * they will be added to the existing list of method arguments.
         */
        public Builder methodArguments(List<Object> methodArguments) {
            if (methodArguments != null) {
                this.methodArguments.addAll(methodArguments);
            }

            return this;
        }

        /**
         * Adds a single method argument to the builder's list of method arguments.
         * If the provided argument is not null, it will be added to the collection.
         */
        public Builder methodArgument(Object methodArgument) {
            if (methodArgument != null) {
                this.methodArguments.add(methodArgument);
            }

            return this;
        }

        /**
         * Sets the memory identifier for the builder.
         */
        public Builder chatMemoryId(Object memoryId) {
            this.chatMemoryId = memoryId;
            return this;
        }

        /**
         * Sets the invocation parameters for the builder.
         */
        public Builder invocationParameters(InvocationParameters invocationParameters) {
            this.invocationParameters = invocationParameters;
            return this;
        }

        /**
         * Sets the LC4j managed parameters for the builder.
         */
        public Builder managedParameters(Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters) {
            this.managedParameters = managedParameters;
            return this;
        }

        /**
         * Sets the timestamp for the builder.
         */
        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * Updates the builder's timestamp to the current system time.
         *
         * @return This Builder instance with the timestamp set to the current time.
         */
        public Builder timestampNow() {
            return timestamp(Instant.now());
        }

        /**
         * Constructs an instance of {@link InvocationContext} using the current state of the builder.
         */
        public <T extends InvocationContext> T build() {
            return (T) new DefaultInvocationContext(this);
        }

        public UUID invocationId() {
            return invocationId;
        }

        public String interfaceName() {
            return interfaceName;
        }

        public String methodName() {
            return methodName;
        }

        public List<@NonNull Object> methodArguments() {
            return methodArguments;
        }

        public Object chatMemoryId() {
            return chatMemoryId;
        }

        public InvocationParameters invocationParameters() {
            return invocationParameters;
        }

        public Map<Class<? extends LangChain4jManaged>, LangChain4jManaged> managedParameters() {
            return managedParameters;
        }

        public Instant timestamp() {
            return timestamp;
        }
    }
}
