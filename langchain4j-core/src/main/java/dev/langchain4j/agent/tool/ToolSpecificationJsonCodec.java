package dev.langchain4j.agent.tool;

/**
 * Codec for converting between an object representation of a {@link ToolSpecification}
 * and a JSON string.
 * <p>
 * The default implementation uses a dedicated Jackson {@code ObjectMapper}.
 * A custom implementation can be provided via SPI using
 * {@link dev.langchain4j.spi.agent.tool.ToolSpecificationJsonCodecFactory}.
 */
public interface ToolSpecificationJsonCodec {

    /**
     * Serializes an object to a JSON string.
     *
     * @param object the object to serialize.
     * @return the JSON string.
     */
    String toJson(Object object);

    /**
     * Deserializes a JSON string to an object of the given class.
     *
     * @param json the JSON string to deserialize.
     * @param type the class of the object.
     * @param <T>  the type of the object.
     * @return the deserialized object.
     */
    <T> T fromJson(String json, Class<T> type);
}
