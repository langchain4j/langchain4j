package dev.langchain4j.code.judge0;

import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC =
            ProviderJson.codec(ProviderJsonSpec.builder().prettyPrint(true).build());

    static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }
}
