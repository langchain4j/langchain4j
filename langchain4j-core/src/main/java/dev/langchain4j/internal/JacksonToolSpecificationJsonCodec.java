package dev.langchain4j.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolSpecificationJsonCodec;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Default {@link ToolSpecificationJsonCodec} implementation using a dedicated Jackson {@link ObjectMapper}.
 * <p>
 * This uses its own {@link ObjectMapper} instance, separate from the shared one in {@link Json},
 * to ensure that tool specification serialization is not affected by customizations applied
 * to the general-purpose codec via {@link dev.langchain4j.spi.json.JsonCodecFactory}.
 */
class JacksonToolSpecificationJsonCodec implements ToolSpecificationJsonCodec {

    private final ObjectMapper objectMapper;

    /**
     * Constructs a {@link JacksonToolSpecificationJsonCodec} with a default {@link ObjectMapper}.
     */
    public JacksonToolSpecificationJsonCodec() {
        this(new ObjectMapper());
    }

    /**
     * Constructs a {@link JacksonToolSpecificationJsonCodec} with the provided {@link ObjectMapper}.
     *
     * @param objectMapper the ObjectMapper to use for JSON serialization and deserialization.
     */
    public JacksonToolSpecificationJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = ensureNotNull(objectMapper, "objectMapper");
    }

    @Override
    public String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
