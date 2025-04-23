package dev.langchain4j.model.chat;

import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;

/**
 * Represents a capability of a {@link ChatModel} or {@link StreamingChatModel}.
 * This is required for the low-level {@link ChatModel} or {@link StreamingChatModel} API
 * to communicate to the high-level APIs (like AI Service) what capabilities are supported and can be utilized.
 */
public enum Capability {

    /**
     * Indicates whether {@link ChatModel} or {@link StreamingChatModel}
     * supports responding in JSON format according to the specified JSON schema.
     *
     * @see ResponseFormat
     * @see JsonSchema
     */
    RESPONSE_FORMAT_JSON_SCHEMA
}
