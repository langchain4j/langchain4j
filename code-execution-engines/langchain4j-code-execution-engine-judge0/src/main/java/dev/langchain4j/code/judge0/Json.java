package dev.langchain4j.code.judge0;

import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;

class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC =
            WireJson.codec(WireJsonSpec.builder().prettyPrint(true).build());

    static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }
}
