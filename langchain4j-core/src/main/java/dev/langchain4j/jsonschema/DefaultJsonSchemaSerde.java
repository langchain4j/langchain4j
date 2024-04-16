package dev.langchain4j.jsonschema;

import com.google.gson.*;

import dev.langchain4j.exception.JsonSchemaDeserializationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.*;

/** A default implementation of {@link JsonSchemaService.JsonSchemaSerde} that uses Gson. */
@Data
@Builder
@AllArgsConstructor
public class DefaultJsonSchemaSerde implements JsonSchemaService.JsonSchemaSerde<JsonElement> {
    public static final Gson GSON = new Gson();

    @Override
    public String serialize(Object object) {
        return GSON.toJson(object);
    }

    @Override
    public Object deserialize(JsonElement argument, Type type)
            throws JsonSchemaDeserializationException {
        try {
            return GSON.fromJson(argument, type);
        } catch (JsonSyntaxException e) {
            throw new JsonSchemaDeserializationException(e);
        }
    }

    @Override
    public JsonElement parse(String argument) throws JsonSchemaParsingException {
        try {
            return GSON.fromJson(argument, JsonElement.class);
        } catch (JsonSyntaxException e) {
            throw new JsonSchemaParsingException(e);
        }
    }
}
