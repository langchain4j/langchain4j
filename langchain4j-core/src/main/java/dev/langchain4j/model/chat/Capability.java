package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;

/**
 * Represents a capability of a {@link ChatLanguageModel} or {@link StreamingChatLanguageModel}.
 * This is required for the low-level {@link ChatLanguageModel} or {@link StreamingChatLanguageModel} API
 * to communicate to the high-level APIs (like AI Service) what capabilities are supported and can be utilized.
 */
@Experimental
public enum Capability {

    // TODO name: ChatModelCapability?

    /**
     * Indicates whether {@link ChatLanguageModel} or {@link StreamingChatLanguageModel}
     * supports responding in JSON format according to the specified JSON schema.
     *
     * @see ResponseFormat
     * @see JsonSchema
     */
    RESPONSE_FORMAT_JSON_SCHEMA
}
