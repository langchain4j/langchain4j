package dev.langchain4j.model.bedrock;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

@Internal
class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
            .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
            .prettyPrint(true)
            .build());

    public static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }
}
