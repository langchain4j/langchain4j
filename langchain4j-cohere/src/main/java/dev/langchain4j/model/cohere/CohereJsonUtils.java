package dev.langchain4j.model.cohere;

import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.internal.ProviderJson;

class CohereJsonUtils {

    private CohereJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
                    .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
                    .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
                    .prettyPrint(true)
                    .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
