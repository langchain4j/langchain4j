package dev.langchain4j.experimental.durable.task;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Configures automatic retry behaviour for failed durable tasks.
 *
 * <p>When a task throws a retryable exception, the service will automatically
 * re-execute the workflow up to {@link #maxRetries()} times, applying an
 * exponential back-off delay between attempts.
 *
 * <p>If no {@code retryableExceptions} are specified, <em>all</em> exceptions
 * (except {@link TaskPausedException}) are considered retryable.
 *
 * <p>Usage:
 * <pre>{@code
 * RetryPolicy policy = RetryPolicy.builder()
 *         .maxRetries(3)
 *         .initialDelay(Duration.ofSeconds(1))
 *         .backoffMultiplier(2.0)
 *         .retryableException(IOException.class)
 *         .build();
 * }</pre>
 */
@Experimental
public final class RetryPolicy {

    /** A policy that disables retries entirely. */
    public static final RetryPolicy NONE = new RetryPolicy(0, Duration.ZERO, 1.0, Duration.ZERO, Set.of());

    private final int maxRetries;
    private final Duration initialDelay;
    private final double backoffMultiplier;
    private final Duration maxDelay;
    private final Set<Class<? extends Throwable>> retryableExceptions;

    private RetryPolicy(
            int maxRetries,
            Duration initialDelay,
            double backoffMultiplier,
            Duration maxDelay,
            Set<Class<? extends Throwable>> retryableExceptions) {
        this.maxRetries = maxRetries;
        this.initialDelay = ensureNotNull(initialDelay, "initialDelay");
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelay = ensureNotNull(maxDelay, "maxDelay");
        this.retryableExceptions = Collections.unmodifiableSet(new LinkedHashSet<>(retryableExceptions));
    }

    /**
     * Maximum number of retry attempts after the initial execution.
     * Zero means no retries (fail immediately).
     *
     * @return the maximum retry count
     */
    public int maxRetries() {
        return maxRetries;
    }

    /**
     * Delay before the first retry attempt.
     *
     * @return the initial delay
     */
    public Duration initialDelay() {
        return initialDelay;
    }

    /**
     * Multiplier applied to the delay after each retry attempt.
     * For example, with an initial delay of 1 second and a multiplier of 2.0,
     * retries happen at 1s, 2s, 4s, 8s, etc.
     *
     * @return the backoff multiplier
     */
    public double backoffMultiplier() {
        return backoffMultiplier;
    }

    /**
     * Upper bound for the computed delay. Prevents excessively long waits
     * when the backoff multiplier produces large values.
     *
     * @return the maximum delay
     */
    public Duration maxDelay() {
        return maxDelay;
    }

    /**
     * Exception types that trigger a retry. If empty, all exceptions are retryable
     * (except {@link TaskPausedException}).
     *
     * @return the set of retryable exception classes
     */
    public Set<Class<? extends Throwable>> retryableExceptions() {
        return retryableExceptions;
    }

    /**
     * Returns {@code true} if the given exception should trigger a retry.
     *
     * <p>When retryable exception classes are configured, the entire cause chain
     * is checked: if any exception in the chain matches a retryable class, the
     * exception is considered retryable.
     *
     * <p>{@link TaskPausedException} is never retried regardless of configuration.
     *
     * @param throwable the exception to test
     * @return true if retryable
     */
    public boolean isRetryable(Throwable throwable) {
        if (throwable instanceof TaskPausedException) {
            return false;
        }
        // Walk cause chain for TaskPausedException
        Throwable current = throwable.getCause();
        while (current != null) {
            if (current instanceof TaskPausedException) {
                return false;
            }
            current = current.getCause();
        }
        if (retryableExceptions.isEmpty()) {
            return true;
        }
        // Check entire cause chain against retryable exceptions
        Throwable t = throwable;
        while (t != null) {
            for (Class<? extends Throwable> retryable : retryableExceptions) {
                if (retryable.isInstance(t)) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Computes the delay for a given attempt number (1-based).
     *
     * @param attempt the retry attempt number (1 for first retry, 2 for second, etc.)
     * @return the computed delay
     */
    public Duration delayForAttempt(int attempt) {
        if (attempt <= 1 || initialDelay.isZero()) {
            return initialDelay;
        }
        double rawMillis = initialDelay.toMillis() * Math.pow(backoffMultiplier, attempt - 1);
        long millis = rawMillis >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) rawMillis;
        Duration computed = Duration.ofMillis(millis);
        return maxDelay.isZero() ? computed : computed.compareTo(maxDelay) > 0 ? maxDelay : computed;
    }

    /**
     * Creates a new builder for {@link RetryPolicy}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link RetryPolicy}.
     */
    public static final class Builder {

        private int maxRetries = 3;
        private Duration initialDelay = Duration.ofSeconds(1);
        private double backoffMultiplier = 2.0;
        private Duration maxDelay = Duration.ofMinutes(1);
        private final Set<Class<? extends Throwable>> retryableExceptions = new LinkedHashSet<>();

        private Builder() {}

        /**
         * Sets the maximum number of retry attempts. Defaults to 3.
         *
         * @param maxRetries the max retry count (0 to disable retries)
         * @return this builder
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be >= 0");
            }
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the initial delay before the first retry. Defaults to 1 second.
         *
         * @param initialDelay the initial delay
         * @return this builder
         */
        public Builder initialDelay(Duration initialDelay) {
            this.initialDelay = ensureNotNull(initialDelay, "initialDelay");
            return this;
        }

        /**
         * Sets the exponential backoff multiplier. Defaults to 2.0.
         *
         * @param backoffMultiplier the multiplier (&gt;= 1.0)
         * @return this builder
         */
        public Builder backoffMultiplier(double backoffMultiplier) {
            if (backoffMultiplier < 1.0) {
                throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
            }
            this.backoffMultiplier = backoffMultiplier;
            return this;
        }

        /**
         * Sets the maximum delay cap. Defaults to 1 minute.
         *
         * @param maxDelay the maximum delay
         * @return this builder
         */
        public Builder maxDelay(Duration maxDelay) {
            this.maxDelay = ensureNotNull(maxDelay, "maxDelay");
            return this;
        }

        /**
         * Adds an exception type that should trigger a retry.
         * If no retryable exceptions are added, all exceptions are retryable.
         *
         * @param exceptionClass the exception class
         * @return this builder
         */
        public Builder retryableException(Class<? extends Throwable> exceptionClass) {
            this.retryableExceptions.add(ensureNotNull(exceptionClass, "exceptionClass"));
            return this;
        }

        /**
         * Builds the {@link RetryPolicy}.
         *
         * @return a new retry policy
         */
        public RetryPolicy build() {
            return new RetryPolicy(maxRetries, initialDelay, backoffMultiplier, maxDelay, retryableExceptions);
        }
    }
}
