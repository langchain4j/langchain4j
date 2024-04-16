package dev.langchain4j.jsonschema;

import dev.langchain4j.exception.JsonSchemaDeserializationException;
import dev.langchain4j.exception.JsonSchemaGenerationException;
import dev.langchain4j.exception.JsonSchemaSerializationException;

import java.lang.reflect.Type;

/**
 * An abstract service that implements {@link JsonSchemaServiceFactories.Service} by delegating the
 * actual work to the underline generator, serde, and sanitizer.
 *
 * @param <E> type of the parsed JSON object, e.g. {@link com.google.gson.JsonElement} if using
 *     Gson.
 */
public abstract class JsonSchemaService<E> implements JsonSchemaServiceFactories.Service {

    /**
     * @return the generator that generates {@link JsonSchema} from a given type.
     */
    protected abstract JsonSchemaGenerator generator();

    /**
     * @return the serde that serializes and deserializes objects.
     */
    protected abstract JsonSchemaSerde<E> serde();

    /**
     * @return the sanitizer that validates and coerces the parsed JSON object.
     */
    protected abstract JsonSchemaSanitizer<E> sanitizer();

    /**
     * The default implementation delegates {@link JsonSchemaGenerator#generate(Type)} to {@link
     * JsonSchemaService#generator()}.
     */
    @Override
    public JsonSchema generate(Type type) throws JsonSchemaGenerationException {
        return generator().generate(type);
    }

    /**
     * The default implementation delegates {@link JsonSchemaSerde#serialize(Object)} to {@link
     * JsonSchemaService#serde()}.
     */
    @Override
    public String serialize(Object object) throws JsonSchemaSerializationException {
        return serde().serialize(object);
    }

    /**
     * The default implementation delegates {@link JsonSchemaSerde#deserialize(Object, Type)} to
     * {@link JsonSchemaService#serde()} and {@link JsonSchemaSanitizer#sanitize(Object, Type)} to
     * {@link JsonSchemaService#sanitizer()}.
     *
     * <p>The serialized string will be parsed into a JSON object, sanitized based on the schema,
     * and finally deserialized into the target type instance.
     */
    @Override
    public Object deserialize(String serialized, Type type)
            throws JsonSchemaDeserializationException {
        E parsed = serde().parse(serialized);
        E sanitized = sanitizer().sanitize(parsed, type);
        return serde().deserialize(sanitized, type);
    }

    /**
     * The default implementation delegates {@link JsonSchemaSerde#deserialize(Object, JsonSchema)}
     * to {@link JsonSchemaService#serde()} and {@link JsonSchemaSanitizer#sanitize(Object,
     * JsonSchema)} to {@link JsonSchemaService#sanitizer()}.
     */
    @Override
    public Object deserialize(String serialized, JsonSchema schema)
            throws JsonSchemaDeserializationException {
        E parsed = serde().parse(serialized);
        E sanitized = sanitizer().sanitize(parsed, schema);
        return serde().deserialize(sanitized, schema);
    }

    /** Parse a string to a parsed JSON object. */
    public interface JsonSchemaGenerator {

        /**
         * Generate a {@link JsonSchema} from a given type.
         *
         * @param type the type to generate the schema from.
         * @return the generated schema.
         */
        JsonSchema generate(Type type) throws JsonSchemaGenerationException;
    }

    /** Serialize and deserialize objects. */
    public interface JsonSchemaSerde<E> {

        /**
         * Serialize an object to a string.
         *
         * @param object the object to serialize.
         * @return the serialized string.
         */
        String serialize(Object object) throws JsonSchemaSerializationException;

        /**
         * Deserialize a parsed JSON object to an instance of the given type.
         *
         * @param parsed the parsed JSON object.
         * @param type the type to deserialize to.
         * @return the deserialized object.
         * @throws JsonSchemaDeserializationException if the deserialization fails.
         */
        Object deserialize(E parsed, Type type) throws JsonSchemaDeserializationException;

        /**
         * Deserialize a string to an instance of the given type.
         *
         * @param parsed the parsed JSON object to deserialize.
         * @param schema the schema to deserialize to.
         * @return the deserialized object.
         * @throws JsonSchemaDeserializationException if the deserialization fails.
         */
        default Object deserialize(E parsed, JsonSchema schema)
                throws JsonSchemaDeserializationException {
            return deserialize(parsed, schema.getOriginalType());
        }

        /**
         * Parse a string to a parsed serialized.
         *
         * @param serialized the string to parse.
         * @return the JSON object that is parsed from the string.
         */
        E parse(String serialized) throws JsonSchemaParsingException;
    }

    /**
     * Sanitize the parsed JSON object.
     *
     * <p>The sanitizer is responsible to validate the parsed JSON object according to the schema
     * and coerce the JSON object to the correct type.
     *
     * @param <E> type of the parsed JSON object, e.g. {@link com.google.gson.JsonElement} if using
     *     Gson.
     */
    public interface JsonSchemaSanitizer<E> {

        /**
         * Sanitize the parsed JSON object.
         *
         * @param parsed the parsed JSON object.
         * @param type the type to sanitize to.
         * @return the sanitized JSON object.
         * @throws JsonSchemaSanitizationException if the sanitization fails.
         */
        default E sanitize(E parsed, Type type) throws JsonSchemaSanitizationException {
            throw new UnsupportedOperationException();
        }

        /**
         * Sanitize the parsed JSON object.
         *
         * @param parsed the parsed JSON object.
         * @param schema the schema to sanitize to. This schema may contain additional information
         *     to help with the JSON object validation and coercion.
         * @return the sanitized JSON object.
         * @throws JsonSchemaSanitizationException if the sanitization fails.
         */
        default E sanitize(E parsed, JsonSchema schema) throws JsonSchemaSanitizationException {
            return sanitize(parsed, schema.getOriginalType());
        }
    }
}
