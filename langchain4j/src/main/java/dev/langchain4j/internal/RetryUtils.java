package dev.langchain4j.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;

import static java.lang.String.format;

public class RetryUtils {

    private static final Logger log = LoggerFactory.getLogger(RetryUtils.class);

    public static <T> T withRetry(Callable<T> action, int maxAttempts) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    throw new RuntimeException(e);
                }
                log.warn(format("Exception was thrown on attempt %s of %s", action, maxAttempts), e);
            }
        }
        throw new RuntimeException("Failed after " + maxAttempts + " attempts");
    }
}
