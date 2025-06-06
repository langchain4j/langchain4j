package dev.langchain4j.model.vertexai.gemini;

/**
 * Tool calling mode, to instruct Gemini whether it can request calls to any functions,
 * to just a subset of the available functions, or to none at all.
 */
public enum ToolCallingMode {
    /**
     * Lets Gemini decide if it needs to request a function call
     */
    AUTO,
    /**
     * Gemini must ask only for a specified set of function calls (defined by the allowedFunctionNames() method)
     */
    ANY,
    /**
     * Gemini shouldn't request to make any function call
     */
    NONE
}
