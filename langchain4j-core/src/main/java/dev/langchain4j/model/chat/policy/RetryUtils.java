package dev.langchain4j.model.chat.policy;

import dev.langchain4j.internal.JacocoIgnoreCoverageGenerated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Callable;

/**
 * Utility class for retrying actions.
 */
public final class RetryUtils {

    private static final Random RANDOM = new Random();

    private RetryUtils() {}

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    /**
     * This method returns a RetryPolicy.Builder.
     * @return A RetryPolicy.Builder.
     */
    public static RetryPolicy.Builder retryPolicyBuilder() {
        return new RetryPolicy.Builder();
    }

    /**
     * This class encapsulates a retry policy.
     */
    public static final class RetryPolicy implements InvocationPolicy {
        /**
         * This class encapsulates a retry policy builder.
         */
        public static final class Builder {
            private int maxAttempts = 3;
            private int delayMillis = 1000;
            private double jitterScale = 0.2;
            private double backoffExp = 1.5;

            /**
             * Construct a RetryPolicy.Builder.
             */
            public Builder() {}

            /**
             * Sets the default maximum number of attempts.
             * @param maxAttempts The maximum number of attempts.
             * @return {@code this}
             */
            public Builder maxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
                return this;
            }

            /**
             * Sets the base delay in milliseconds.
             *
             * <p>The delay is calculated as follows:
             * <ol>
             *     <li>Calculate the raw delay in milliseconds as
             *         {@code delayMillis * Math.pow(backoffExp, attempt - 1)}.</li>
             *     <li>Calculate the jitter delay in milliseconds as
             *         {@code rawDelayMs + rand.nextInt((int) (rawDelayMs * jitterScale))}.</li>
             *     <li>Sleep for the jitter delay in milliseconds.</li>
             * </ol>
             *
             * @param delayMillis The delay in milliseconds.
             * @return {@code this}
             */
            public Builder delayMillis(int delayMillis) {
                this.delayMillis = delayMillis;
                return this;
            }

            /**
             * Sets the jitter scale.
             *
             * <p>The jitter delay in milliseconds is calculated as
             * {@code rawDelayMs + rand.nextInt((int) (rawDelayMs * jitterScale))}.
             *
             * @param jitterScale The jitter scale.
             * @return {@code this}
             */
            public Builder jitterScale(double jitterScale) {
                this.jitterScale = jitterScale;
                return this;
            }

            /**
             * Sets the backoff exponent.
             * @param backoffExp The backoff exponent.
             * @return {@code this}
             */
            public Builder backoffExp(double backoffExp) {
                this.backoffExp = backoffExp;
                return this;
            }

            /**
             * Builds a RetryPolicy.
             * @return A RetryPolicy.
             */
            public RetryPolicy build() {
                return new RetryPolicy(maxAttempts, delayMillis, jitterScale, backoffExp);
            }
        }

        private final int maxAttempts;
        private final int delayMillis;
        private final double jitterScale;
        private final double backoffExp;

        /**
         * Construct a RetryPolicy.
         * @param maxAttempts The maximum number of attempts.
         * @param delayMillis The delay in milliseconds.
         * @param jitterScale The jitter scale.
         * @param backoffExp The backoff exponent.
         */
        public RetryPolicy(
                int maxAttempts,
                int delayMillis,
                double jitterScale,
                double backoffExp) {
            this.maxAttempts = maxAttempts;
            this.delayMillis = delayMillis;
            this.jitterScale = jitterScale;
            this.backoffExp = backoffExp;
        }

        public int maxAttempts() {
            return maxAttempts;
        }

        /**
         * This method returns the raw delay in milliseconds for a given attempt.
         * @param attempt The attempt number.
         * @return The raw delay in milliseconds.
         */
        public double rawDelayMs(int attempt) {
            return ((double) delayMillis) * Math.pow(backoffExp, attempt - 1);
        }

        /**
         * This method returns the jitter delay in milliseconds for a given attempt.
         * @param attempt The attempt number.
         * @return The jitter delay in milliseconds.
         */
        public int jitterDelayMillis(int attempt) {
            double delay = rawDelayMs(attempt);
            double jitter = delay * jitterScale;
            return (int) (delay + RANDOM.nextInt((int) jitter));
        }

        /**
         * This method sleeps for a given attempt.
         * @param attempt The attempt number.
         */
        @JacocoIgnoreCoverageGenerated
        public void sleep(int attempt) {
            try {
                Thread.sleep(jitterDelayMillis(attempt));
            } catch (InterruptedException ignored) {
                // pass
            }
        }

        /**
         * This method attempts to execute a given action up to a specified number of times with a 1-second delay.
         * If the action fails on all attempts, it throws a RuntimeException.
         *
         * @param action      The action to be executed.
         * @param <T> The type of the result of the action.
         * @return The result of the action if it is successful.
         * @throws RuntimeException if the action fails on all attempts.
         */
        public <T> T withRetry(Callable<T> action) {
            return withRetry(action, maxAttempts);
        }

        @Override
        public Callable<?> apply(final Callable<?> action) {
            return () -> withRetry(action);
        }

        /**
         * This method attempts to execute a given action up to a specified number of times with a 1-second delay.
         * If the action fails on all attempts, it throws a RuntimeException.
         *
         * @param action      The action to be executed.
         * @param maxAttempts The maximum number of attempts to execute the action.
         * @param <T> The type of the result of the action.
         * @return The result of the action if it is successful.
         * @throws RuntimeException if the action fails on all attempts.
         */
        public <T> T withRetry(Callable<T> action, int maxAttempts) {
            int attempt = 1;
            while (true) {
                try {
                    return action.call();
                } catch (Exception e) {
                    if (attempt >= maxAttempts) {
                        throw e instanceof RuntimeException re ? re : new RuntimeException(e);
                    }

                    log.warn(String.format("Exception was thrown on attempt %s of %s", attempt, maxAttempts), e);

                    sleep(attempt);
                }
                attempt++;
            }
        }
    }

    /**
     * Default retry policy used by {@link #withRetry(Callable)}.
     */
    public static final RetryPolicy DEFAULT_RETRY_POLICY = retryPolicyBuilder()
            .maxAttempts(3)
            .delayMillis(500)
            .jitterScale(0.2)
            .backoffExp(1.5)
            .build();

    /**
     * This method attempts to execute a given action up to a specified number of times with a 1-second delay.
     * If the action fails on all attempts, it throws a RuntimeException.
     *
     * @param action      The action to be executed.
     * @param maxAttempts The maximum number of attempts to execute the action.
     * @param <T> The type of the result of the action.
     *
     * @return The result of the action if it is successful.
     * @throws RuntimeException if the action fails on all attempts.
     */
    public static <T> T withRetry(Callable<T> action, int maxAttempts) {
        return DEFAULT_RETRY_POLICY.withRetry(action, maxAttempts);
    }

    /**
     * This method attempts to execute a given action up to a specified number of times with a 1-second delay.
     * If the action fails on all attempts, it throws a RuntimeException.
     *
     * @param action      The action to be executed.
     * @param <T> The type of the result of the action.
     * @return The result of the action if it is successful.
     * @throws RuntimeException if the action fails on all attempts.
     */
    public static <T> T withRetry(Callable<T> action) {
        return DEFAULT_RETRY_POLICY.withRetry(action);
    }
}
