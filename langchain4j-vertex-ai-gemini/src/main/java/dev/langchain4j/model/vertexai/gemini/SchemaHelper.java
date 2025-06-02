package dev.langchain4j.model.vertexai.gemini;

import com.google.cloud.vertexai.api.Schema;
import com.google.cloud.vertexai.api.Type;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helper class to create a <code>com.google.cloud.vertexai.api.Schema</code>
 * from a JSON schema string, or from a class by reflection on its public fields.
 */
public class SchemaHelper {

    /**
     * Create an instance of <code>Schema</code> from a JSON schema string.
     * @param jsonSchemaString the JSON schema string
     * @return a fully built schema
     */
    public static Schema fromJsonSchema(String jsonSchemaString) {
        try {
            // ensure types are in uppercase
            // Open API JSON Schema normally use lowercase types,
            // but it seems the Gemini SDK mandates uppercase types
            // as exposed in <code>com.google.cloud.vertexai.api.Type</code>
            String schemaJsonString = jsonSchemaString
                .replace("\"object\"", "\"OBJECT\"")
                .replace("\"integer\"", "\"INTEGER\"")
                .replace("\"string\"", "\"STRING\"")
                .replace("\"number\"", "\"NUMBER\"")
                .replace("\"array\"", "\"ARRAY\"")
                .replace("\"boolean\"", "\"BOOLEAN\"");

            Schema.Builder builder = Schema.newBuilder();
            JsonFormat.parser().merge(schemaJsonString, builder);
            return builder.build();
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create an instance of <code>Schema</code> from a class by reflection on its fields.
     * @param theClass the class for which to create a schema representation
     * @return a fully built schema
     */
    public static Schema fromClass(Class<?> theClass) {
        if (CharSequence.class.isAssignableFrom(theClass) ||
            Character.class.isAssignableFrom(theClass) ||
            char.class.isAssignableFrom(theClass)) {
            return Schema.newBuilder().setType(Type.STRING).build();
        } else if (Boolean.class.isAssignableFrom(theClass) ||
            boolean.class.isAssignableFrom(theClass)) {
            return Schema.newBuilder().setType(Type.BOOLEAN).build();
        } else if (Integer.class.isAssignableFrom(theClass) ||
            int.class.isAssignableFrom(theClass) ||
            Long.class.isAssignableFrom(theClass) ||
            long.class.isAssignableFrom(theClass)) {
            return Schema.newBuilder().setType(Type.INTEGER).build();
        } else if (Double.class.isAssignableFrom(theClass) ||
            double.class.isAssignableFrom(theClass) ||
            Float.class.isAssignableFrom(theClass) ||
            float.class.isAssignableFrom(theClass)) {
            return Schema.newBuilder().setType(Type.NUMBER).build();
        } else if (theClass.isArray()) {
            Class<?> componentType = theClass.getComponentType();
            return Schema.newBuilder().setType(Type.ARRAY).setItems(
                fromClass(componentType)
            ).build();
        } else if (Collection.class.isAssignableFrom(theClass)) {
            // Because of type erasure, we can't easily know the type of the items in the collection
            return Schema.newBuilder().setType(Type.ARRAY).build();
        } else if (theClass.isEnum()) {
            List<String> enumConstantNames = Arrays.stream(theClass.getEnumConstants())
                .map(Object::toString)
                .collect(Collectors.toList());
            return Schema.newBuilder()
                .setType(Type.STRING)
                .addAllEnum(enumConstantNames)
                .build();
        } else {
            // This is some kind of object, let's go through its fields
            Schema.Builder schemaBuilder = Schema.newBuilder().setType(Type.OBJECT);
            List<String> propertyNames = new ArrayList<>();
            Stream.concat(
                Arrays.stream(theClass.getDeclaredFields()),
                Arrays.stream(theClass.getFields()))
                .filter(field -> !field.getName().startsWith("this$"))
                .collect(Collectors.toSet())
                .forEach(field -> {
                    schemaBuilder.putProperties(field.getName(), fromClass(field.getType())).build();
                    propertyNames.add(field.getName());
            });
            schemaBuilder.addAllRequired(propertyNames);
            return schemaBuilder.build();
        }
    }

    public static Schema from(JsonSchemaElement jsonSchemaElement) {
        if (jsonSchemaElement instanceof JsonStringSchema) {
            JsonStringSchema jsonStringSchema = (JsonStringSchema) jsonSchemaElement;
            Schema.Builder builder = Schema.newBuilder()
                    .setType(Type.STRING);
            if (jsonStringSchema.description() != null) {
                builder.setDescription(jsonStringSchema.description());
            }
            return builder.build();
        } else if (jsonSchemaElement instanceof JsonBooleanSchema) {
            JsonBooleanSchema jsonBooleanSchema = (JsonBooleanSchema) jsonSchemaElement;
            Schema.Builder builder = Schema.newBuilder()
                    .setType(Type.BOOLEAN);
            if (jsonBooleanSchema.description() != null) {
                builder.setDescription(jsonBooleanSchema.description());
            }
            return builder.build();
        } else if (jsonSchemaElement instanceof JsonIntegerSchema) {
            JsonIntegerSchema jsonIntegerSchema = (JsonIntegerSchema) jsonSchemaElement;
            Schema.Builder builder = Schema.newBuilder()
                    .setType(Type.INTEGER);
            if (jsonIntegerSchema.description() != null) {
                builder.setDescription(jsonIntegerSchema.description());
            }
            return builder.build();
        } else if (jsonSchemaElement instanceof JsonNumberSchema) {
            JsonNumberSchema jsonNumberSchema = (JsonNumberSchema) jsonSchemaElement;
            Schema.Builder builder = Schema.newBuilder()
                    .setType(Type.NUMBER);
            if (jsonNumberSchema.description() != null) {
                builder.setDescription(jsonNumberSchema.description());
            }
            return builder.build();
        } else if (jsonSchemaElement instanceof JsonEnumSchema) {
            JsonEnumSchema jsonEnumSchema = (JsonEnumSchema) jsonSchemaElement;
            Schema.Builder builder = Schema.newBuilder()
                    .setType(Type.STRING)
                    .addAllEnum(jsonEnumSchema.enumValues());
            if (jsonEnumSchema.description() != null) {
                builder.setDescription(jsonEnumSchema.description());
            }
            return builder.build();
        } else if (jsonSchemaElement instanceof JsonArraySchema) {
            JsonArraySchema jsonArraySchema = (JsonArraySchema) jsonSchemaElement;
            Schema.Builder builder = Schema.newBuilder()
                    .setType(Type.ARRAY)
                    .setItems(from(jsonArraySchema.items()));
            if (jsonArraySchema.description() != null) {
                builder.setDescription(jsonArraySchema.description());
            }
            return builder.build();
        } else if (jsonSchemaElement instanceof JsonObjectSchema) {
            JsonObjectSchema jsonObjectSchema = (JsonObjectSchema) jsonSchemaElement;
            Map<String, Schema> properties = new LinkedHashMap<>();
            jsonObjectSchema.properties()
                    .forEach((property, value) -> properties.put(property, from(value)));
            Schema.Builder builder = Schema.newBuilder()
                    .setType(Type.OBJECT)
                    .putAllProperties(properties)
                    .addAllRequired(jsonObjectSchema.required());
            if (jsonObjectSchema.description() != null) {
                builder.setDescription(jsonObjectSchema.description());
            }
            return builder.build();
        } else {
            throw new RuntimeException("Unknown type: " + jsonSchemaElement.getClass());
        }
    }
}
