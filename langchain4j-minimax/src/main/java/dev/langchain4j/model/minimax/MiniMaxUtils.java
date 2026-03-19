package dev.langchain4j.model.minimax;

/**
 * Utility constants and methods for the MiniMax integration.
 */
class MiniMaxUtils {

    static final String DEFAULT_MINIMAX_URL = "https://api.minimax.io/v1";

    /**
     * Clamps temperature to the MiniMax-supported range [0.0, 1.0].
     * MiniMax accepts temperature values between 0.0 and 1.0 inclusive.
     *
     * @param temperature the requested temperature, or null
     * @return the clamped temperature, or null if input is null
     */
    static Double clampTemperature(Double temperature) {
        if (temperature == null) {
            return null;
        }
        return Math.max(0.0, Math.min(1.0, temperature));
    }
}
