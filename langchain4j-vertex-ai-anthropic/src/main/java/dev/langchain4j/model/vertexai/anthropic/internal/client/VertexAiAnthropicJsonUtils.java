package dev.langchain4j.model.vertexai.anthropic.internal.client;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

@Internal
public class VertexAiAnthropicJsonUtils {

    private VertexAiAnthropicJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
            .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
            .build());

    public static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    public static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
