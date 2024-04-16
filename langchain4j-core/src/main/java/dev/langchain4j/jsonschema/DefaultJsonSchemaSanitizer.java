package dev.langchain4j.jsonschema;

import com.google.gson.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A default JSON schema sanitizer that validates and coerces the parsed JSON object.
 *
 * <p>The JSON schema is supposed to be created by {@link DefaultJsonSchemaGenerator} from only
 * Java's built-in types. Therefore, this sanitizer will validate and convert the parsed JSON object
 * to match directly on the Java built-in types instead of the JSON schema.
 *
 * <p>If in a strict mode, this sanitizer will throw an exception when the parsed JSON object does
 * not match the Java built-in types. Otherwise, it will try best to fix the parsed JSON object to
 * match the Java built-in types. The fixed rules are:
 *
 * <ul>
 *   <li>Automatically casting between number types.
 *   <li>Automatically converting between string and boolean/number types.
 * </ul>
 */
@Data
@AllArgsConstructor
@Builder
public class DefaultJsonSchemaSanitizer
        implements JsonSchemaService.JsonSchemaSanitizer<JsonElement> {
    @Builder.Default private final boolean strict = true;

    @Override
    public JsonElement sanitize(JsonElement parsed, Type type)
            throws JsonSchemaSanitizationException {
        try {
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) type;
                return sanitize(parsed, (Class<?>) parameterizedType.getRawType(), () -> type);
            }
            return sanitize(parsed, (Class<?>) type, () -> type);
        } catch (JsonSchemaSanitizationException e) {
            if (!strict && e instanceof JsonSchemaSanitizationExceptions.MismatchTypeException) {
                return JsonNull.INSTANCE;
            }
            throw e;
        }
    }

    private JsonElement sanitize(
            JsonElement parsed, Class<?> type, Supplier<Type> resolveGenericType)
            throws JsonSchemaSanitizationException {
        if (type == Object.class) {
            return parsed;
        }
        if (parsed == null || parsed.isJsonNull()) {
            return JsonNull.INSTANCE;
        }

        switch (JsonSchemaUtil.getJsonSchemaElementType(type)) {
            case BOOLEAN:
                return sanitizeBoolean(parsed, type);
            case INTEGER:
                return sanitizeInteger(parsed, type);
            case NUMBER:
                return sanitizeDecimal(parsed, type);
            case STRING:
                return sanitizeString(parsed, type);
            case ENUM:
                return sanitizeEnum(parsed, type);
            case ARRAY:
                return sanitizeArray(parsed, type, resolveGenericType);
            case OBJECT:
            default:
                return sanitizeObject(parsed, type, resolveGenericType);
        }
    }

    /**
     * Sanitize a string JSON element.
     *
     * <p>If the parsed is not in string type and {@code strict} is "false", The object will be
     * converted to string.
     */
    private JsonElement sanitizeString(JsonElement parsed, Class<?> type)
            throws JsonSchemaSanitizationException {

        JsonPrimitive primitive = ensureJsonType(JsonPrimitive.class, parsed, type);
        if (primitive.isString()) {
            return parsed;
        }
        return tryFix(
                () -> new JsonPrimitive(parsed.getAsString()),
                JsonSchemaSanitizationExceptions.mismatchType(type, parsed));
    }

    /**
     * Sanitize a boolean JSON element.
     *
     * <p>If the parsed can be converted to "true" or "false" and {@code strict} is "false", The
     * object will be converted to boolean.
     */
    private JsonElement sanitizeBoolean(JsonElement parsed, Class<?> type)
            throws JsonSchemaSanitizationException {

        JsonPrimitive primitive = ensureJsonType(JsonPrimitive.class, parsed, type);
        if (primitive.isBoolean()) {
            return parsed;
        }
        return tryFix(
                () -> {
                    assert (primitive.isString() && parsed.getAsString().matches("true|false"));
                    return new JsonPrimitive(parsed.getAsString().equals("true"));
                },
                JsonSchemaSanitizationExceptions.mismatchType(type, parsed));
    }

    private JsonElement sanitizeInteger(JsonElement parsed, Class<?> type)
            throws JsonSchemaSanitizationException {

        JsonPrimitive primitive = ensureJsonType(JsonPrimitive.class, parsed, type);
        if (type == Byte.class || type == byte.class) {
            return sanitizeBoundedNumber(
                    primitive, type, Number::byteValue, Byte.MIN_VALUE, Byte.MAX_VALUE);
        }
        if (type == Short.class || type == short.class) {
            return sanitizeBoundedNumber(
                    primitive, type, Number::shortValue, Short.MIN_VALUE, Short.MAX_VALUE);
        }
        if (type == Integer.class || type == int.class) {
            return sanitizeBoundedNumber(
                    primitive, type, Number::intValue, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        if (type == Long.class || type == long.class) {
            return sanitizeBoundedNumber(
                    primitive, type, Number::longValue, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        if (type == BigInteger.class) {
            return sanitizeBoundedNumber(
                    primitive,
                    type,
                    (n) -> BigInteger.valueOf(n.longValue()),
                    Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY);
        }
        throw JsonSchemaSanitizationExceptions.mismatchType(type, parsed);
    }

    private JsonElement sanitizeDecimal(JsonElement parsed, Class<?> type)
            throws JsonSchemaSanitizationException {

        JsonPrimitive primitive = ensureJsonType(JsonPrimitive.class, parsed, type);
        if (type == Float.class || type == float.class) {
            return sanitizeBoundedNumber(
                    primitive, type, Number::floatValue, -Float.MAX_VALUE, Float.MAX_VALUE);
        }
        if (type == Double.class || type == double.class) {
            return sanitizeBoundedNumber(
                    primitive, type, Number::doubleValue, -Double.MAX_VALUE, Double.MAX_VALUE);
        }
        if (type == BigDecimal.class) {
            return sanitizeBoundedNumber(
                    primitive,
                    type,
                    (n) -> new BigDecimal(n.toString()),
                    Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY);
        }
        throw JsonSchemaSanitizationExceptions.mismatchType(type, parsed);
    }

    private JsonElement sanitizeEnum(JsonElement parsed, Class<?> type)
            throws JsonSchemaSanitizationException {

        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Class<Enum> enumClass = (Class<Enum>) type;
            // noinspection unchecked
            Enum.valueOf(enumClass, Objects.requireNonNull(parsed.getAsString()));
        } catch (Exception | Error e) {
            throw JsonSchemaSanitizationExceptions.invalidEnumValue(type, parsed);
        }
        return parsed;
    }

    private JsonElement sanitizeArray(
            JsonElement parsed, Class<?> type, Supplier<Type> resolveGenericType)
            throws JsonSchemaSanitizationException {
        Type genericType = resolveGenericType.get();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            if (parameterizedType.getActualTypeArguments().length == 1) {
                Class<?> itemType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                return sanitizeArray(parsed, type, itemType);
            }
        }
        // Fallback to Object array
        return sanitizeArray(parsed, type, Object.class);
    }

    private JsonElement sanitizeArray(JsonElement parsed, Class<?> type, Class<?> itemType)
            throws JsonSchemaSanitizationException {

        JsonArray array = ensureJsonType(JsonArray.class, parsed, type);
        JsonArray sanitizedArray = new JsonArray();
        for (JsonElement item : array) {
            sanitizedArray.add(sanitize(item, itemType));
        }
        return sanitizedArray;
    }

    private JsonElement sanitizeObject(
            JsonElement parsed, Class<?> type, Supplier<Type> resolveGenericType)
            throws JsonSchemaSanitizationException {
        Type genericType = resolveGenericType.get();
        if (genericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) genericType;
            if (parameterizedType.getActualTypeArguments().length == 2) {
                Class<?> keyType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                if (keyType != String.class) {
                    throw new JsonSchemaSanitizationException(
                            String.format(
                                    "%s's key type must be String, but was: %s", type, keyType));
                }

                Class<?> valueType = (Class<?>) parameterizedType.getActualTypeArguments()[1];
                return sanitizeObject(parsed, type, valueType);
            }
        }
        // Fallback to Object map
        return sanitizeObject(parsed, type, Object.class);
    }

    private JsonElement sanitizeObject(JsonElement parsed, Class<?> type, Class<?> valueType)
            throws JsonSchemaSanitizationException {

        JsonObject object = ensureJsonType(JsonObject.class, parsed, type);
        JsonObject sanitizedObject = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            JsonElement key = new JsonPrimitive(entry.getKey());
            JsonElement value = sanitize(entry.getValue(), valueType);
            sanitizedObject.add(key.getAsString(), value);
        }
        return sanitizedObject;
    }

    /**
     * Sanitize a bounded number JSON element.
     *
     * <p>If the parsed JSON element is a valid number string or a number outside bounds, and
     * "strict" is set to "false," the object will be converted to a number.
     */
    private <T extends Number> JsonElement sanitizeBoundedNumber(
            JsonPrimitive parsed, Class<?> type, Function<Number, T> caster, T minValue, T maxValue)
            throws JsonSchemaSanitizationException {

        Number number = parsed.getAsNumber();
        if (parsed.isNumber()) {
            boolean intHasFractionalPart =
                    JsonSchemaUtil.isInteger(type) && hasFractionalPart(number);

            if (!intHasFractionalPart && inBounds(number, minValue, maxValue)) {
                if (type.isInstance(number)) {
                    return parsed;
                }
                // Safe casting as having been checked
                return new JsonPrimitive(caster.apply(number));
            }
        }

        // Fix the number
        return tryFix(
                () -> {
                    double doubleValue = parsed.getAsDouble();
                    if (JsonSchemaUtil.isInteger(type) && hasFractionalPart(number) && strict) {
                        throw JsonSchemaSanitizationExceptions.hasNonIntegralNumber(
                                type, doubleValue);
                    }
                    if (!inBounds(number, minValue, maxValue)) {
                        if (strict) {
                            throw JsonSchemaSanitizationExceptions.numberOutOfBound(
                                    doubleValue, minValue, maxValue, type);
                        }
                        doubleValue = Math.max(doubleValue, minValue.doubleValue());
                        doubleValue = Math.min(doubleValue, maxValue.doubleValue());
                    }
                    return new JsonPrimitive(caster.apply(doubleValue));
                },
                JsonSchemaSanitizationExceptions.mismatchType(type, parsed),
                true);
    }

    private <T> T tryFix(FixSupplier<T> fix, JsonSchemaSanitizationException fallbackError)
            throws JsonSchemaSanitizationException {
        return tryFix(fix, fallbackError, false);
    }

    /**
     * Try to fix the parsed JSON object.
     *
     * <p>If "forceFix" is set to "true," the object will be fixed regardless of the "strict" mode.
     *
     * @param fix the fix to apply.
     * @param fallbackError the fallback error to throw if the fix fails.
     * @param forceFix whether to force the fix.
     * @param <T> the type of the fixed object.
     * @return the fixed object.
     * @throws JsonSchemaSanitizationException if the fix fails.
     */
    private <T> T tryFix(
            FixSupplier<T> fix, JsonSchemaSanitizationException fallbackError, boolean forceFix)
            throws JsonSchemaSanitizationException {
        if (forceFix || !strict) {
            try {
                return fix.get();
            } catch (JsonSchemaSanitizationException e) {
                throw e; // Rethrow
            } catch (Exception ignored) {
                // Fallback
            }
        }
        throw fallbackError;
    }

    private <T> T ensureJsonType(Class<T> jsonType, JsonElement parsed, Class<?> type)
            throws JsonSchemaSanitizationException {
        if (jsonType.isInstance(parsed)) {
            return jsonType.cast(parsed);
        }
        throw JsonSchemaSanitizationExceptions.mismatchType(type, parsed);
    }

    private boolean hasFractionalPart(Number number) {
        double doubleValue = number.doubleValue();
        return doubleValue != Math.floor(doubleValue) || Double.isInfinite(doubleValue);
    }

    private boolean inBounds(Number value, Number minValue, Number maxValue) {
        return value.doubleValue() >= minValue.doubleValue()
                && value.doubleValue() <= maxValue.doubleValue();
    }

    public interface FixSupplier<O> {
        O get() throws JsonSchemaSanitizationException;
    }
}
