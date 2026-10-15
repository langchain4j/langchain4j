package dev.langchain4j.model.openai.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.model.openai.OpenAiStreamingException;
import dev.langchain4j.model.openai.internal.shared.OpenAiError;

/**
 * Detects OpenAI-compatible mid-stream error payloads. Official OpenAI SDKs raise an API error for any
 * streamed chunk that contains a top-level {@code error} key.
 */
final class OpenAiStreamError {

    private OpenAiStreamError() {}

    static OpenAiStreamingException tryParse(String data) {
        if (data == null || data.isBlank() || "[DONE]".equals(data)) {
            return null;
        }
        try {
            Envelope envelope = Json.fromJson(data, Envelope.class);
            if (envelope == null || envelope.error == null) {
                return null;
            }
            return new OpenAiStreamingException(envelope.error);
        } catch (Exception ignored) {
            // Not a JSON object / not an error envelope — leave to normal response parsing.
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class Envelope {
        @JsonProperty
        OpenAiError error;
    }
}
