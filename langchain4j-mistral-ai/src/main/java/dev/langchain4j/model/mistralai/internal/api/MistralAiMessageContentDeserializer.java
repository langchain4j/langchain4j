package dev.langchain4j.model.mistralai.internal.api;

import static java.util.Collections.singletonList;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.util.List;

class MistralAiMessageContentDeserializer extends JsonDeserializer<List<MistralAiMessageContent>> {

    @Override
    public List<MistralAiMessageContent> deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }
        if (token == JsonToken.VALUE_STRING) {
            return singletonList(new MistralAiTextContent(p.getText()));
        }
        if (token == JsonToken.START_ARRAY) {
            JavaType contentListType =
                    ctxt.getTypeFactory().constructCollectionType(List.class, MistralAiMessageContent.class);
            return ctxt.readValue(p, contentListType);
        }
        throw new JsonParseException(
                p, String.format("Expected string or an array, but got: %s (%s)", token, p.getText()));
    }
}
