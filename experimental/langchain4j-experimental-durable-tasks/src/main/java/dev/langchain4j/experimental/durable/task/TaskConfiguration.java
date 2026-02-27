package dev.langchain4j.experimental.durable.task;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for starting a durable long-lived task.
 *
 * <p>Controls execution behaviour: checkpoint policy, retry policy, timeout, and
 * user-defined labels for categorisation.
 */
@Experimental
public final class TaskConfiguration {

    private final String agentName;
    private final CheckpointPolicy checkpointPolicy;
    private final RetryPolicy retryPolicy;
    private final Map<String, String> labels;
    private final Duration timeout;

    private TaskConfiguration(Builder builder) {
        this.agentName = ensureNotBlank(builder.agentName, "agentName");
        this.checkpointPolicy = builder.checkpointPolicy;
        this.retryPolicy = builder.retryPolicy;
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(builder.labels));
        this.timeout = builder.timeout;
    }

    public String agentName() {
        return agentName;
    }

    public CheckpointPolicy checkpointPolicy() {
        return checkpointPolicy;
    }

    /**
     * Returns the retry policy, or {@code null} if retries are disabled.
     *
     * @return the retry policy, or null
     */
    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    public Map<String, String> labels() {
        return labels;
    }

    /**
     * Returns the optional execution timeout, or {@code null} if no timeout is configured.
     *
     * @return the timeout, or null
     */
    public Duration timeout() {
        return timeout;
    }

    /**
     * Creates a new builder for {@link TaskConfiguration}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String agentName;
        private CheckpointPolicy checkpointPolicy;
        private RetryPolicy retryPolicy;
        private Map<String, String> labels = Collections.emptyMap();
        private Duration timeout;

        private Builder() {}

        public Builder agentName(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder checkpointPolicy(CheckpointPolicy checkpointPolicy) {
            this.checkpointPolicy = ensureNotNull(checkpointPolicy, "checkpointPolicy");
            return this;
        }

        /**
         * Sets the retry policy. If not set, failed tasks are not retried.
         *
         * @param retryPolicy the retry policy
         * @return this builder
         */
        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = ensureNotNull(labels, "labels");
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public TaskConfiguration build() {
            return new TaskConfiguration(this);
        }
    }
}
