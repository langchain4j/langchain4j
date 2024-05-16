package dev.langchain4j.model.output;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.With;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;


/**
 * Information about a type.
 * This class breaks down a type into its raw type, generic types, and annotations.
 */
@Data
@Builder
@With
public class TypeInformation {
    @NonNull private final Class<?> rawType;
    @Builder.Default @NonNull private final List<Class<?>> genericTypes = Collections.emptyList();
    @Builder.Default @NonNull private final List<Annotation> annotations = Collections.emptyList();

    /**
     * Get the type information for a method (including its annotations, like {@link dev.langchain4j.model.output.structured.Parse}.
     * @param method the method
     * @return the type information
     */
    public static TypeInformation of(final Method method) {
        return of(method.getGenericReturnType()).withAnnotations(Arrays.asList(method.getAnnotations()));
    }

    /**
     * Get the type information for a type.
     * @param type the type
     * @return the type information
     */
    public static TypeInformation of(final Type type) {
        if (type instanceof ParameterizedType) {
            final ParameterizedType pt = (ParameterizedType) type;
            return of(pt);
        }

        if (!(type instanceof Class<?>)) {
            throw new IllegalArgumentException("Type is not a class: " + type);
        }
        return builder()
                .rawType((Class<?>) type)
                .build();
    }

    /**
     * Get the type information for a parameterized type.
     * @param pt the parameterized type
     * @return the type information
     */
    public static TypeInformation of(final ParameterizedType pt) {
        final Type rawType = pt.getRawType();
        if (!(rawType instanceof Class<?>)) {
            throw new IllegalArgumentException("Raw type is not a class: " + rawType);
        }
        final Type[] typeArguments = pt.getActualTypeArguments();
        return builder()
                .rawType((Class<?>) rawType)
                .genericTypes(Arrays.stream(typeArguments)
                        .filter(t -> t instanceof Class<?>)
                        .map(t -> (Class<?>) t)
                        .collect(Collectors.toList()))
                .build();
    }

    /**
     * Get the type information for a class.
     * @param type the class
     * @return the type information
     */
    public static TypeInformation of(final Class<?> type) {
        return builder().rawType(type).build();
    }

    /**
     * Returns true if the type is generic.
     * @return true if the type is generic
     */
    public boolean isGeneric() {
        return !genericTypes.isEmpty();
    }
}
