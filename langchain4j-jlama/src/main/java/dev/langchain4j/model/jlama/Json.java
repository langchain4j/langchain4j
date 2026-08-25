package dev.langchain4j.model.jlama;

import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;

class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC =
            WireJson.codec(WireJsonSpec.builder().build());

    static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }
}
