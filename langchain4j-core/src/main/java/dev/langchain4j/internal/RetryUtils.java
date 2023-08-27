package dev.langchain4j.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

import static java.lang.String.format;

public class RetryUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

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
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    throw new RuntimeException(e);
                }

                log.warn(format("Exception was thrown on attempt %s of %s", attempt, maxAttempts), e);

                try {
                    Thread.sleep(1000); // TODO make configurable
                } catch (InterruptedException ignored) {
                }
            }
        }
        throw new RuntimeException("Failed after " + maxAttempts + " attempts");
    }
}
