package dev.langchain4j.model.cohere;

import dev.langchain4j.internal.WireJsonSpec;
import dev.langchain4j.internal.WireJson;

class CohereJsonUtils {

    private CohereJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder()
                    .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
                    .inclusion(WireJsonSpec.Inclusion.NON_NULL)
                    .prettyPrint(true)
                    .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
