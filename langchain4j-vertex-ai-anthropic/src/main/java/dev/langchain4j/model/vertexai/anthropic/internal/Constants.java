package dev.langchain4j.model.vertexai.anthropic.internal;

/**
 * Constants used throughout the Vertex AI Anthropic integration.
 */
public final class Constants {

    // API configuration
    public static final String ANTHROPIC_VERSION = "vertex-2023-10-16";
    public static final String EXCEPTION_IN_LISTENER = "Exception in listener";

    // Default values
    public static final int DEFAULT_MAX_TOKENS = 4096;
    public static final int SUBSTANTIAL_CONTENT_THRESHOLD = 100;

    // Message roles
    public static final String USER_ROLE = "user";
    public static final String ASSISTANT_ROLE = "assistant";

    // Content types
    public static final String TEXT_CONTENT_TYPE = "text";
    public static final String IMAGE_CONTENT_TYPE = "image";
    public static final String TOOL_USE_CONTENT_TYPE = "tool_use";
    public static final String TOOL_RESULT_CONTENT_TYPE = "tool_result";

    private Constants() {
        // Utility class
    }
}
