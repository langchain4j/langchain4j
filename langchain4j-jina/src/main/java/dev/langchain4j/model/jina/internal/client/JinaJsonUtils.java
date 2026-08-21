package dev.langchain4j.model.jina.internal.client;


import dev.langchain4j.internal.WireJsonSpec;
import dev.langchain4j.internal.WireJson;

class JinaJsonUtils {

    private JinaJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder()
                    .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
                    .prettyPrint(true)
                    .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
