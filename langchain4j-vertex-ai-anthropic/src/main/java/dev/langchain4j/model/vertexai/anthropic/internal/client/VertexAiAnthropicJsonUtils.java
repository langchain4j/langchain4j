package dev.langchain4j.model.vertexai.anthropic.internal.client;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;

@Internal
public class VertexAiAnthropicJsonUtils {

    private VertexAiAnthropicJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder()
            .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
            .inclusion(WireJsonSpec.Inclusion.NON_NULL)
            .build());

    public static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    public static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
