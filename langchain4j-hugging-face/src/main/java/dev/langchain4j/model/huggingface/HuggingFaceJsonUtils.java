package dev.langchain4j.model.huggingface;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;

class HuggingFaceJsonUtils {

    private HuggingFaceJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder()
            .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
            .inclusion(WireJsonSpec.Inclusion.NON_NULL)
            .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> type) {
        return CODEC.fromJson(jsonStr, type);
    }
}
