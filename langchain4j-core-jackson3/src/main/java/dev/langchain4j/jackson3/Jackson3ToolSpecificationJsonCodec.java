package dev.langchain4j.jackson3;

import dev.langchain4j.exception.JsonWriteException;
import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.agent.tool.ToolSpecificationJsonCodec;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 twin of the default tool specification codec.
 *
 * <p>It uses its own mapper, separate from the general-purpose codec, so that tool specification
 * serialization is not affected by customizations applied through {@code JsonCodecFactory}.
 */
public class Jackson3ToolSpecificationJsonCodec implements ToolSpecificationJsonCodec {

    private final ObjectMapper objectMapper;

    public Jackson3ToolSpecificationJsonCodec() {
        this(Jackson3Defaults.pinJackson2Defaults(JsonMapper.builder()).build());
    }

    public Jackson3ToolSpecificationJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JacksonException e) {
            throw new JsonWriteException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new JsonReadException(e);
        }
    }
}
