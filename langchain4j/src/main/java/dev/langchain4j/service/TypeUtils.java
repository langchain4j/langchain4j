package dev.langchain4j.service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class TypeUtils {


    public static Class<?> getRawClass(Type type) {
        if (type == null) {
            throw new NullPointerException("Type should not be null.");
        }

        if (type instanceof Class<?>) {
            return (Class<?>)type;
        } else if (type instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) type).getRawType();
        } else {
            throw new IllegalArgumentException("Unable to extract raw class.");
        }
    }

    public static boolean typeHasRawClass(Type type, Class<?> rawClass) {
        if (type == null || rawClass == null) {
            return false;
        }

        return rawClass.equals(getRawClass(type));
    }

    public static Class<?> resolveFirstGenericParameterClass(Type returnType) {
        Type[] typeArguments = getTypeArguments(returnType);

        if (typeArguments.length == 0) {
            return null;
        }

        Type firstTypeArgument = typeArguments[0];
        if (firstTypeArgument instanceof Class<?>) {
            return (Class<?>) firstTypeArgument;
        } else if (firstTypeArgument instanceof ParameterizedType) {
            return (Class<?>) ((ParameterizedType) firstTypeArgument).getRawType();
        }

        return null;
    }

    private static Type[] getTypeArguments(Type returnType) {
        if (returnType == null) {
            throw new IllegalArgumentException("returnType parameter cannot be null.");
        }

        if (!(returnType instanceof ParameterizedType)) {
            return new Type[0];
        }

        ParameterizedType type = (ParameterizedType) returnType;
        Type[] typeArguments = type.getActualTypeArguments();

        if (typeArguments.length == 0) {
            throw new IllegalArgumentException("Parameterized type has no type arguments.");
        }
        return typeArguments;
    }
}
