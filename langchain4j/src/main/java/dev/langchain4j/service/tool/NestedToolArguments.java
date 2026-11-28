package dev.langchain4j.service.tool;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Internal;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the values inside an object, collection, map or array tool argument before they are bound by the JSON
 * codec, which would otherwise convert them silently: a missing or {@code null} value for a primitive field becomes
 * {@code 0} / {@code false}, and a number with a fraction for an integer field is truncated ({@code 2.7} becomes
 * {@code 2}).
 * <p>
 * It applies the rules that {@link DefaultToolExecutor} already applies to top-level parameters:
 * a missing primitive is reported, and an integer accepts only integral numbers ({@code 3.0} is accepted).
 * A primitive field annotated with {@code @JsonProperty(required = false)} may be missing.
 */
@Internal
final class NestedToolArguments {

    private static final Set<Class<?>> INTEGER_TYPES = Set.of(
            int.class,
            Integer.class,
            long.class,
            Long.class,
            short.class,
            Short.class,
            byte.class,
            Byte.class,
            BigInteger.class);

    private NestedToolArguments() {}

    static void check(Object value, Type type, String path) {
        if (value == null) {
            return;
        }
        Class<?> rawClass = rawClass(type);
        if (rawClass == null) {
            return;
        }

        if (INTEGER_TYPES.contains(rawClass)) {
            if (value instanceof Number number && !isIntegral(number)) {
                throw new IllegalArgumentException(String.format(
                        "Argument \"%s\" has non-integer value for %s: <%s>", path, rawClass.getName(), value));
            }
        } else if (rawClass.isArray() || type instanceof GenericArrayType) {
            if (value instanceof List<?> list) {
                Type componentType = type instanceof GenericArrayType arrayType
                        ? arrayType.getGenericComponentType()
                        : rawClass.getComponentType();
                checkElements(list, componentType, path);
            }
        } else if (Collection.class.isAssignableFrom(rawClass)) {
            if (value instanceof List<?> list) {
                checkElements(list, typeArgument(type, 0), path);
            }
        } else if (Map.class.isAssignableFrom(rawClass)) {
            Type valueType = typeArgument(type, 1);
            if (value instanceof Map<?, ?> map && valueType != null) {
                map.forEach((key, entryValue) -> check(entryValue, valueType, path + "." + key));
            }
        } else if (isBean(rawClass) && value instanceof Map<?, ?> map) {
            checkFields(map, rawClass, path);
        }
    }

    private static void checkElements(List<?> list, Type elementType, String path) {
        if (elementType == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            check(list.get(i), elementType, path + "[" + i + "]");
        }
    }

    private static void checkFields(Map<?, ?> map, Class<?> beanClass, String path) {
        for (Class<?> c = beanClass; c != null && c != Object.class && !isJdkClass(c); c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)
                        || Modifier.isTransient(modifiers)
                        || field.isSynthetic()
                        || field.isAnnotationPresent(JsonIgnore.class)) {
                    continue;
                }

                JsonProperty jsonProperty = field.getAnnotation(JsonProperty.class);
                String name = jsonProperty != null && !jsonProperty.value().isEmpty()
                        ? jsonProperty.value()
                        : field.getName();
                String fieldPath = path + "." + name;
                Object fieldValue = map.get(name);

                if (field.getType().isPrimitive()
                        && fieldValue == null
                        && (jsonProperty == null || jsonProperty.required())) {
                    throw new IllegalArgumentException(String.format(
                            "Required argument \"%s\" of type %s is missing",
                            fieldPath, field.getType().getName()));
                }

                check(fieldValue, field.getGenericType(), fieldPath);
            }
        }
    }

    private static boolean isIntegral(Number number) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            return Double.isFinite(d) && d == Math.rint(d);
        }
        if (number instanceof BigDecimal decimal) {
            return decimal.signum() == 0 || decimal.stripTrailingZeros().scale() <= 0;
        }
        return true;
    }

    private static boolean isBean(Class<?> c) {
        return !c.isPrimitive()
                && !c.isEnum()
                && !c.isInterface()
                && !Modifier.isAbstract(c.getModifiers())
                && !isJdkClass(c);
    }

    private static boolean isJdkClass(Class<?> c) {
        String name = c.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.");
    }

    private static Class<?> rawClass(Type type) {
        if (type instanceof Class<?> c) {
            return c;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> c) {
            return c;
        }
        if (type instanceof GenericArrayType arrayType) {
            Class<?> component = rawClass(arrayType.getGenericComponentType());
            return component == null ? null : component.arrayType();
        }
        return null;
    }

    private static Type typeArgument(Type type, int index) {
        if (type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > index) {
            return parameterized.getActualTypeArguments()[index];
        }
        return null;
    }
}
