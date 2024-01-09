package dev.langchain4j.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.Callable;

import static java.lang.String.format;

public final class RetryUtils {
    private RetryUtils() {}

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    public static RetryPolicy.Builder retryPolicyBuilder() {
        return new RetryPolicy.Builder();
    }

    public static final class RetryPolicy {
        public static final class Builder {
            private int maxAttempts = 3;
            private int delayMillis = 1000;
            private double jitterScale = 0.2;
            private double backoffExp = 1.5;

            public Builder maxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
                return this;
            }

            public Builder delayMillis(int delayMillis) {
                this.delayMillis = delayMillis;
                return this;
            }

            public Builder jitterScale(double jitterScale) {
                this.jitterScale = jitterScale;
                return this;
            }

            public Builder backoffExp(double backoffExp) {
                this.backoffExp = backoffExp;
                return this;
            }

            public RetryPolicy build() {
                return new RetryPolicy(maxAttempts, delayMillis, jitterScale, backoffExp);
            }
        }

        private final int maxAttempts;
        private final int delayMillis;
        private final double jitterScale;
        private final double backoffExp;

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

        public double rawDelayMs(int attempt) {
            return ((double) delayMillis) * Math.pow(backoffExp, attempt - 1);
        }

        public int jitterDelayMillis(int attempt) {
            Random rand = new Random();
            double delay = rawDelayMs(attempt);
            double jitter = delay * jitterScale;
            return (int) (delay + rand.nextInt((int) jitter));
        }

        @JacocoIgnoreCoverageGenerated
        public void sleep(int attempt) {
            try {
                Thread.sleep(jitterDelayMillis(attempt));
            } catch (InterruptedException ignored) {
                // pass
            }
        }

        public <T> T withRetry(Callable<T> action) {
            return withRetry(action, maxAttempts);
        }

        public <T> T withRetry(Callable<T> action, int maxAttempts) {
            int attempt = 1;
            while (true) {
                try {
                    return action.call();
                } catch (Exception e) {
                    if (attempt == maxAttempts) {
                        throw new RuntimeException(e);
                    }

                    log.warn(format("Exception was thrown on attempt %s of %s", attempt, maxAttempts), e);

                    sleep(attempt);
                }
                attempt++;
            }
        }
    }

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
     * @return The result of the action if it is successful.
     * @throws RuntimeException if the action fails on all attempts.
     */
    public static <T> T withRetry(Callable<T> action) {
        return DEFAULT_RETRY_POLICY.withRetry(action);
    }
}
