package dev.langchain4j.model.openai.internal;

import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;

class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC =
            WireJson.codec(WireJsonSpec.builder()
                    .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
                    .prettyPrint(true)
                    .build());

    static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }
}
