package dev.langchain4j.model.huggingface;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

class HuggingFaceJsonUtils {

    private HuggingFaceJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
            .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
            .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> type) {
        return CODEC.fromJson(jsonStr, type);
    }
}
