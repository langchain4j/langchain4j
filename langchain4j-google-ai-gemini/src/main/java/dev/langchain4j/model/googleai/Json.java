package dev.langchain4j.model.googleai;

import dev.langchain4j.Internal;
import dev.langchain4j.internal.WireJson;
import dev.langchain4j.internal.WireJsonSpec;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

@Internal
class Json {

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC = WireJson.codec(WireJsonSpec.builder()
            .inclusion(WireJsonSpec.Inclusion.NON_NULL)
            .prettyPrint(true)
            .build());

    private static final dev.langchain4j.internal.Json.JsonCodec CODEC_WITHOUT_INDENT =
            WireJson.codec(WireJsonSpec.builder().build());

    static String toJson(Object o) {
        return CODEC.toJson(o);
    }

    static String toJsonWithoutIndent(Object o) {
        return CODEC_WITHOUT_INDENT.toJson(o);
    }

    static <T> T fromJson(String json, Class<T> type) {
        return CODEC.fromJson(json, type);
    }

    /**
     * Re-reads an already-parsed value as {@code type}. The codec has no in-memory conversion, so
     * this goes through JSON; the batch paths that use it convert one response at a time.
     */
    static <T> T convertValue(Object fromValue, Type type) {
        return CODEC.fromJson(CODEC.toJson(fromValue), type);
    }

    /** Builds the {@link Type} of a generic class, so a type token is not needed. */
    static Type parameterized(Class<?> raw, Type... typeArguments) {
        return new ParameterizedType() {

            @Override
            public Type[] getActualTypeArguments() {
                return typeArguments.clone();
            }

            @Override
            public Type getRawType() {
                return raw;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }
        };
    }
}
