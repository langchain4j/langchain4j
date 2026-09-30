package dev.langchain4j.model.mistralai.internal.client;


import dev.langchain4j.internal.ProviderJsonSpec;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.Internal;

@Internal
class MistralAiJsonUtils {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
                    .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
                    .prettyPrint(true)
                    .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String json, Class<T> clazz) {
        return CODEC.fromJson(json, clazz);
    }
}
