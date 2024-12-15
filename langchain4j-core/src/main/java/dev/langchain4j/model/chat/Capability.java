package dev.langchain4j.model.chat;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonSchema;

@Experimental // TODO name: ChatModelCapability?
public enum Capability {

    /**
     * TODO
     * TODO {@link ResponseFormat#jsonSchema()}
     *
     * @see ResponseFormat
     * @see JsonSchema
     */
    RESPONSE_FORMAT_JSON_SCHEMA
}
