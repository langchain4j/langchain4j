package dev.langchain4j.model.jlama;

import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC =
            ProviderJson.codec(ProviderJsonSpec.builder().build());

    static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }
}
