package dev.langchain4j.model.vertexai.gemini;

/**
 * Possible harm categories for the generation of responses that have been blocked by the model.
 */
public enum HarmCategory {
    HARM_CATEGORY_UNSPECIFIED,
    HARM_CATEGORY_HATE_SPEECH,
    HARM_CATEGORY_DANGEROUS_CONTENT,
    HARM_CATEGORY_HARASSMENT,
    HARM_CATEGORY_SEXUALLY_EXPLICIT,
    UNRECOGNIZED
}
