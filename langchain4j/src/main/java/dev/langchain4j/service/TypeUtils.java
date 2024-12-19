package dev.langchain4j.service;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static java.util.stream.Collectors.toList;

public class TypeUtils {

    public static Class<?> getRawClass(Type type) {
        if (type == null) {
            throw new NullPointerException("Type should not be null.");
        }

        if (type instanceof Class<?>) {
            return (Class<?>) type;
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

    public static boolean isResultRawString(Type returnType) {
        if (!(returnType instanceof ParameterizedType paramType)) {
            return false;
        }
        return Arrays.stream(paramType.getActualTypeArguments())
                .findFirst()
                .map(String.class::equals)
                .orElse(false);
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

        if (!(returnType instanceof ParameterizedType type)) {
            return new Type[0];
        }

        Type[] typeArguments = type.getActualTypeArguments();

        if (typeArguments.length == 0) {
            throw new IllegalArgumentException("Parameterized type has no type arguments.");
        }
        return typeArguments;
    }


    /**
     * <p>Ensures that no wildcard and/or parametrized types are being used as service method return type.</p>
     * <p>For example - such (service) method return types will pass:</p>
     * <ul>
     * <li>String</li>
     * <li>MyCustomPojo</li>
     * <li>List&lt;MyCustomPojo&gt;</li>
     * <li>Set&lt;MyCustomPojo&gt;</li>
     * <li>Result&lt;String&gt;</li>
     * <li>Result&lt;MyCustomPojo&gt;</li>
     * <li>Result&lt;List&lt;MyCustomPojo&gt;&gt;</li>
     * </ul>
     * ... and there are few examples that will fail:
     * <ul>
     * <li>List&lt;?&gt;</li>
     * <li>Result&lt;?&gt;</li>
     * <li>Result&lt;List&lt;?&gt;&gt;</li>
     * <li>List&lt;T&gt;</li>
     * <li>Result&lt;T&gt;</li>
     * <li>Result&lt;List&lt;T&gt;&gt;</li>
     * </ul>*
     *
     * @param methodName the method name
     * @param type       the return type
     */
    public static void validateReturnTypesAreProperlyParametrized(String methodName, Type type) {
        TypeUtils.validateReturnTypesAreProperlyParametrized(methodName, type, new ArrayList<>());
    }

    private static void validateReturnTypesAreProperlyParametrized(String methodName, Type type, List<Type> typeChain) {
        if (type instanceof ParameterizedType parameterizedType) {
            // Recursively check all parametrized types
            for (Type actualTypeArgument : parameterizedType.getActualTypeArguments()) {
                typeChain.add(parameterizedType);
                validateReturnTypesAreProperlyParametrized(methodName, actualTypeArgument, typeChain);
            }
        } else if (type instanceof WildcardType) {
            // Wildcard usage: Result<?> ask(String question)
            typeChain.add(type);
            throw genericNotProperlySpecifiedException(methodName, typeChain);
        } else if (type instanceof TypeVariable) {
            // Type variable: Result<T> ask(String question)
            typeChain.add(type);
            throw genericNotProperlySpecifiedException(methodName, typeChain);
        } else if (type instanceof Class<?> clazz && clazz.getTypeParameters().length > 0) {
            //  Raw type:  Result ask(String question)
            typeChain.add(type);
            throw genericNotProperlySpecifiedException(methodName, typeChain);
        }
    }


    private static IllegalArgumentException genericNotProperlySpecifiedException(String methodName, List<Type> typeChain) {

        String actualDeclaration = getActualDeclaration(typeChain);
        String exampleStringDeclaration = getExemplarDeclaration(typeChain, "String");
        String examplePojoDeclaration = getExemplarDeclaration(typeChain, "MyCustomPojo");

        return illegalArgument("The return type '%s' of the method '%s' must be parameterized with a concrete type, " +
                "for example: %s or %s", actualDeclaration, methodName, exampleStringDeclaration, examplePojoDeclaration);
    }

    private static String getActualDeclaration(List<Type> typeChain) {
        StringBuilder actualDeclaration = new StringBuilder(typeChain.stream().map(type -> {
            if (type instanceof WildcardType) {
                return "?";
            } else if (type instanceof TypeVariable) {
                return type.getTypeName();
            } else {
                return TypeUtils.getRawClass(type).getSimpleName();
            }
        }).collect(Collectors.joining("<")));
        actualDeclaration.append(">".repeat(Math.max(0, typeChain.size() - 1)));
        return actualDeclaration.toString();
    }

    private static String getExemplarDeclaration(List<Type> typeChain, String forType) {
        List<Type> rawTypesOnly = typeChain.stream().filter(type -> !(type instanceof WildcardType || type instanceof TypeVariable)).collect(toList());
        StringBuilder declarationExample = new StringBuilder(rawTypesOnly.stream().map(type -> TypeUtils.getRawClass(type).getSimpleName()).collect(Collectors.joining("<")));
        declarationExample.append("<").append(forType);
        declarationExample.append(">".repeat(rawTypesOnly.size()));
        return declarationExample.toString();
    }

}
