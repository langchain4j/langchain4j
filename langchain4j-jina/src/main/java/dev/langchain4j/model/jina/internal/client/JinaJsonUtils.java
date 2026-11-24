package dev.langchain4j.model.jina.internal.client;


import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.internal.ProviderJson;

class JinaJsonUtils {

    private JinaJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
                    .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
                    .prettyPrint(true)
                    .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
