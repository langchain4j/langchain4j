package dev.langchain4j.model.googleai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the media resolution settings for a content part.
 * This is used for per-part media resolution setting (Gemini 3 only).
 *
 * @see <a href="https://ai.google.dev/gemini-api/docs/media-resolution">Media Resolution Documentation</a>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record GeminiMediaResolution(@JsonProperty("level") GeminiMediaResolutionLevel level) {
    static GeminiMediaResolution of(GeminiMediaResolutionLevel level) {
        return level != null ? new GeminiMediaResolution(level) : null;
    }
}
