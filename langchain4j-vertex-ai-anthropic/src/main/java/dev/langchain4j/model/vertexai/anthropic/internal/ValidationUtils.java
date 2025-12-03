package dev.langchain4j.model.vertexai.anthropic.internal;

/**
 * Utility class for common validation and listener notification methods.
 */
public final class ValidationUtils {

    /**
     * Validates that maxTokens is within acceptable range.
     */
    public static Integer validateMaxTokens(Integer maxTokens) {
        if (maxTokens == null) {
            return Constants.DEFAULT_MAX_TOKENS;
        }
        if (maxTokens < 1 || maxTokens > 200000) {
            throw new IllegalArgumentException("maxTokens must be between 1 and 200000");
        }
        return maxTokens;
    }

    /**
     * Validates that temperature is within acceptable range.
     */
    public static Double validateTemperature(Double temperature) {
        if (temperature == null) {
            return null;
        }
        if (temperature < 0.0 || temperature > 1.0) {
            throw new IllegalArgumentException("temperature must be between 0.0 and 1.0");
        }
        return temperature;
    }

    /**
     * Validates that topP is within acceptable range.
     */
    public static Double validateTopP(Double topP) {
        if (topP == null) {
            return null;
        }
        if (topP < 0.0 || topP > 1.0) {
            throw new IllegalArgumentException("topP must be between 0.0 and 1.0");
        }
        return topP;
    }

    /**
     * Validates that topK is within acceptable range.
     */
    public static Integer validateTopK(Integer topK) {
        if (topK == null) {
            return null;
        }
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
        return topK;
    }
}
