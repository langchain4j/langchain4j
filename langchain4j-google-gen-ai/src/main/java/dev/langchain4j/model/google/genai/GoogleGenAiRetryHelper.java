package dev.langchain4j.model.google.genai;

import java.util.function.Supplier;
import org.slf4j.Logger;

class GoogleGenAiRetryHelper {

    static <T> T executeWithRetry(Supplier<T> action, int maxRetries, Logger log) {
        T result = null;
        RuntimeException lastException = null;

        for (int i = 0; i <= maxRetries; i++) {
            try {
                result = action.get();
                break;
            } catch (Exception e) {
                lastException = new RuntimeException("Google GenAI call failed", e);
                log.error("Attempt {}/{} failed: {}", i + 1, maxRetries + 1, e.getMessage());
                if (i < maxRetries) {
                    try {
                        Thread.sleep((long) (Math.pow(2, i) * 1000));
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        if (result == null) {
            log.error("Google GenAI call failed after all retries");
            throw lastException;
        }

        return result;
    }

    private GoogleGenAiRetryHelper() {}
}
