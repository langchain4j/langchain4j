package dev.langchain4j.model.vertexai.gemini;

/**
 * Safety thresholds, for the harm categories for the generation of responses that have been blocked by the model.
 */
public enum SafetyThreshold {
    HARM_BLOCK_THRESHOLD_UNSPECIFIED,
    BLOCK_LOW_AND_ABOVE,
    BLOCK_MEDIUM_AND_ABOVE,
    BLOCK_ONLY_HIGH,
    BLOCK_NONE,
    UNRECOGNIZED
}
