package dev.langchain4j.model.chat.request.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ResponseFormat;

import java.util.ArrayList;
import java.util.List;

@Experimental
public class JsonSchemaParser {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static ResponseFormat fromJsonString(String jsonSchemaString) {
        try {
            return ResponseFormat.builder()
                .type(ResponseFormat.JSON.type())
                .jsonSchema(JsonSchema.builder()
                    .rootElement(parse(mapper.readTree(jsonSchemaString)))
                    .build())
                .build();
        }catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    private static JsonSchemaElement parse(JsonNode jsonNode) {
        String type = jsonNode.has("type") ? jsonNode.get("type").asText() : null;
        String description = jsonNode.has("description") ? jsonNode.get("description").asText() : null;

        if (type == null){
            throw new IllegalArgumentException("Type cannot be null");
        }

        return switch (type) {
            case "string" ->
                JsonStringSchema.builder().description(description).build();
            case "integer" ->
                JsonIntegerSchema.builder().description(description).build();
            case "boolean" ->
                JsonBooleanSchema.builder().description(description).build();
            case "number" ->
                JsonNumberSchema.builder().description(description).build();
            case "object" ->
                parseObject(jsonNode);
            case "array" ->
                parseArray(jsonNode);
            default -> throw new IllegalArgumentException("Unsupported JSON schema type: " + type);
        };
    }

    private static JsonObjectSchema parseObject(JsonNode jsonNode) {
        JsonObjectSchema.Builder builder = new JsonObjectSchema.Builder();

        if (jsonNode.has("properties")) {
            JsonNode propertiesNode = jsonNode.get("properties");
            propertiesNode.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                builder.addProperty(key, parse(value));
            });
        }

        if (jsonNode.has("required")) {
            List<String> required = new ArrayList<>();
            jsonNode.get("required").forEach(reqNode -> required.add(reqNode.asText()));
            builder.required(required);
        }

        return builder.build();
    }

    private static JsonArraySchema parseArray(JsonNode jsonNode) {
        JsonArraySchema.Builder builder = new JsonArraySchema.Builder();
        if (jsonNode.has("items")) {
            builder.items(parse(jsonNode.get("items")));
        }
        return builder.build();
    }

}
