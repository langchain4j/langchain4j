package dev.langchain4j.jsonschema;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.exception.JsonSchemaDeserializationException;
import dev.langchain4j.exception.JsonSchemaGenerationException;
import dev.langchain4j.exception.JsonSchemaSerializationException;
import dev.langchain4j.spi.jsonschema.JsonSchemaServiceFactory;

import java.lang.reflect.Type;

public class JsonSchemaServiceFactories {
    public static final Service DEFAULT_SERVICE = loadService();

    private JsonSchemaServiceFactories() {}

    static Service loadService() {
        for (JsonSchemaServiceFactory factory : loadFactories(JsonSchemaServiceFactory.class)) {
            return factory.create();
        }
        // return DefaultJsonSchemaService.builder().build();
        return null;
    }

    /**
     * A service that generates JSON schema, serializes and deserializes objects.
     */
    public interface Service {

        /**
         * Generate a {@link JsonSchema} from a given type.
         *
         * @param type the type to generate the schema from.
         * @return the generated schema.
         */
        JsonSchema generate(Type type) throws JsonSchemaGenerationException;

        /**
         * Serialize an object to a string.
         *
         * @param object the object to serialize.
         * @return the serialized string.
         */
        String serialize(Object object) throws JsonSchemaSerializationException;

        /**
         * Deserialize a string to an object.
         *
         * @param serialized the string to deserialize.
         * @param type the type to deserialize to.
         * @return the deserialized object.
         * @throws JsonSchemaDeserializationException if the deserialization fails.
         */
        Object deserialize(String serialized, Type type) throws JsonSchemaDeserializationException;

        /**
         * Deserialize a string to an object.
         *
         * @param serialized the string to deserialize.
         * @param schema the schema to deserialize to.
         * @return the deserialized object.
         * @throws JsonSchemaDeserializationException if the deserialization fails.
         */
        Object deserialize(String serialized, JsonSchema schema)
                throws JsonSchemaDeserializationException;
    }
}
