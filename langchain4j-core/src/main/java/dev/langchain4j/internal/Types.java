package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Builds {@link Type} instances for generic types, so that a caller can ask a
 * {@link Json.JsonCodec} for a {@code List<Foo>} without a JSON library's type token.
 *
 * <p>{@code TypeReference} and its equivalents belong to a particular library and moved package in
 * Jackson 3, which is exactly what the codec SPI exists to avoid depending on.
 */
@Internal
public final class Types {

    private Types() {}

    /**
     * The type of a {@code List<T>}.
     */
    public static Type listOf(Type elementType) {
        return parameterized(List.class, elementType);
    }

    /**
     * The type of {@code raw<typeArguments...>}, for example {@code Map<String, Object>}.
     */
    public static Type parameterized(Class<?> raw, Type... typeArguments) {
        Type[] arguments = typeArguments.clone();
        return new ParameterizedType() {

            @Override
            public Type[] getActualTypeArguments() {
                return arguments.clone();
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
