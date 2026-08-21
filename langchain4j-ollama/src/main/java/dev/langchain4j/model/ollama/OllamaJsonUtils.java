package dev.langchain4j.model.ollama;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;
import java.lang.reflect.Type;

@Internal
class OllamaJsonUtils {

    private OllamaJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = codec(true);
    private static final Json.JsonCodec CODEC_WITHOUT_IDENT = codec(false);

    private static Json.JsonCodec codec(boolean prettyPrint) {
        return WireJson.codec(WireJsonSpec.builder()
                .propertyNaming(WireJsonSpec.PropertyNaming.SNAKE_CASE)
                .prettyPrint(prettyPrint)
                .build());
    }

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static String toJsonWithoutIdent(Object object) {
        return CODEC_WITHOUT_IDENT.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }

    static <T> T fromJson(String jsonStr, Type type) {
        return CODEC.fromJson(jsonStr, type);
    }
}
