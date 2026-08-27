package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import dev.langchain4j.exception.JsonException;
import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.spi.json.JsonCodecFactory;

import java.lang.reflect.Type;

import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * JSON helper class. It is supposed to be used by "tools" and "structured output" functionalities.
 */
@Internal
public class Json {

    private Json() {
    }

    /**
     * The abstract JSON codec interface.
     *
     * <p>An implementation reports every failure as a {@link JsonException}: a
     * {@link JsonReadException} when reading, a {@link JsonWriteException} when writing. Callers
     * branch on those, so an implementation that lets its JSON library's own exception escape - or
     * wraps it in something else - is not interchangeable with the others. Keep the library's
     * exception as the {@linkplain Throwable#getCause() cause} rather than discarding it.
     */
    @Internal
    public interface JsonCodec {

        /**
         * Convert the given object to JSON.
         *
         * @param o the object to convert.
         * @return the JSON string.
         * @throws JsonWriteException if the object has no JSON representation.
         */
        String toJson(Object o);

        /**
         * Convert the given JSON string to an object of the given class.
         *
         * @param json the JSON string.
         * @param type the class of the object.
         * @param <T>  the type of the object.
         * @return the object.
         * @throws JsonReadException if the JSON is malformed or does not describe the given type.
         */
        <T> T fromJson(String json, Class<T> type);

        /**
         * Convert the given JSON string to an object of the given type.
         *
         * @param json the JSON string.
         * @param type the type of the object.
         * @param <T>  the type of the object.
         * @return the object.
         * @throws JsonReadException if the JSON is malformed or does not describe the given type.
         */
        <T> T fromJson(String json, Type type);
    }

    private static final JsonCodec CODEC = loadCodec();

    private static JsonCodec loadCodec() {
        for (JsonCodecFactory factory : loadFactories(JsonCodecFactory.class)) {
            return factory.create();
        }
        return new JacksonJsonCodec();
    }

    /**
     * Convert the given object to JSON.
     *
     * @param o the object to convert.
     * @return the JSON string.
     * @throws JsonWriteException if the object has no JSON representation.
     */
    public static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    /**
     * Convert the given JSON string to an object of the given class.
     *
     * @param json the JSON string.
     * @param type the class of the object.
     * @param <T>  the type of the object.
     * @return the object.
     * @throws JsonReadException if the JSON is malformed or does not describe the given type.
     */
    public static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }

    /**
     * Convert the given JSON string to an object of the given type.
     *
     * @param json the JSON string.
     * @param type the type of the object.
     * @param <T>  the type of the object.
     * @return the object.
     * @throws JsonReadException if the JSON is malformed or does not describe the given type.
     */
    public static <T> T fromJson(String json, Type type) {
        return CODEC.fromJson(json, type);
    }
}
