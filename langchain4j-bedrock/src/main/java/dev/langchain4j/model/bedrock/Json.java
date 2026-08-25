package dev.langchain4j.model.bedrock;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;

@Internal
class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder()
            .inclusion(WireJsonSpec.Inclusion.NON_NULL)
            .prettyPrint(true)
            .build());

    public static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }
}
