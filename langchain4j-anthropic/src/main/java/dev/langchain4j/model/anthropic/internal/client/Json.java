package dev.langchain4j.model.anthropic.internal.client;

import dev.langchain4j.internal.WireJsonSpec;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.Internal;


@Internal
public class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder()
                    .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
                    .prettyPrint(true)
                    .build());

    public static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    public static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }
}
