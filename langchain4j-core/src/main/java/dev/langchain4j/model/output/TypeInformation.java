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


@Data
@Builder
@With
public class TypeInformation {
    @NonNull private final Class<?> rawType;
    @Builder.Default @NonNull private final List<Class<?>> genericTypes = Collections.emptyList();
    @Builder.Default @NonNull private final List<Annotation> annotations = Collections.emptyList();

    public static TypeInformation of(final Method method) {
        return of(method.getGenericReturnType()).withAnnotations(Arrays.asList(method.getAnnotations()));
    }

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

    public static TypeInformation of(final Class<?> type) {
        return builder().rawType(type).build();
    }

    public boolean isGeneric() {
        return !genericTypes.isEmpty();
    }
}
