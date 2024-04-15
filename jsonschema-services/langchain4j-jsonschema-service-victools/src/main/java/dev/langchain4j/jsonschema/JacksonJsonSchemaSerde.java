package dev.langchain4j.jsonschema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.exception.JsonSchemaDeserializationException;
import dev.langchain4j.exception.JsonSchemaSerializationException;

import java.lang.reflect.Type;

/** JsonSchemaSerde implementation that uses {@link com.fasterxml.jackson}. */
public class JacksonJsonSchemaSerde implements JsonSchemaService.JsonSchemaSerde<JsonNode> {
    ObjectMapper mapper = new ObjectMapper();

    @Override
    public String serialize(Object object) throws JsonSchemaSerializationException {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new JsonSchemaSerializationException(e);
        }
    }

    @Override
    public Object deserialize(JsonNode argument, Type type)
            throws JsonSchemaDeserializationException {
        try {
            return mapper.treeToValue(argument, (Class<?>) type);
        } catch (JsonProcessingException e) {
            throw new JsonSchemaDeserializationException(e);
        }
    }

    @Override
    public JsonNode parse(String argument) throws JsonSchemaParsingException {
        try {
            return mapper.readValue(argument, JsonNode.class);
        } catch (JsonProcessingException e) {
            throw new JsonSchemaParsingException(e);
        }
    }
}
