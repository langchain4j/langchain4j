package dev.langchain4j.audit.api.event;

import dev.langchain4j.audit.event.DefaultInteractionSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.NonNull;

/**
 * Contains general information about the source of the interaction.
 */
public interface InteractionSource {
    /**
     * Unique identifier for an entire interaction with the LLM
     */
    UUID interactionId();

    /**
     * The fully-qualified name of the AI Service interface where the LLM interaction was initiated from
     * @see #methodName()
     */
    String interfaceName();

    /**
     * The method name on {@link #interfaceName()} where the LLM interaction was initiated from
     */
    String methodName();

    /**
     * The arguments passed into the initial LLM call
     */
    List<Object> methodArguments();

    /**
     * The memory id parameter of the method, if one exists
     */
    Optional<Object> memoryId();

    /**
     * Retrieves the point in time when the interaction occurred.
     */
    Instant timestamp();

    /**
     * Converts the current instance of {@code DefaultInteractionSource} into a {@code Builder},
     * pre-populated with the current values of the instance.
     */
    default Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates a new instance of the {@code Builder} class for constructing
     * instances of {@code DefaultInteractionSource}.
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * A builder class to create {@link InteractionSource} instances.
     */
    class Builder {
        private String interfaceName;
        private String methodName;
        private List<@NonNull Object> methodArguments = new ArrayList<>();
        private Object memoryId;
        private Instant timestamp;

        protected Builder() {}

        protected Builder(InteractionSource interactionSource) {
            interfaceName(interactionSource.interfaceName());
            methodName(interactionSource.methodName());
            methodArguments(interactionSource.methodArguments());
            memoryId(interactionSource.memoryId().orElse(null));
            timestamp(interactionSource.timestamp());
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
        public Builder memoryId(Object memoryId) {
            this.memoryId = memoryId;
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
         * Constructs an instance of {@link InteractionSource} using the current state of the builder.
         */
        public <T extends InteractionSource> T build() {
            return (T) new DefaultInteractionSource(this);
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public Object getMemoryId() {
            return memoryId;
        }

        public List<@NonNull Object> getMethodArguments() {
            return methodArguments;
        }

        public String getMethodName() {
            return methodName;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }
}
