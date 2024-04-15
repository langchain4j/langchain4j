package dev.langchain4j.jsonschema;

import com.google.gson.JsonElement;

import dev.langchain4j.exception.JsonSchemaSanitizationException;

import java.util.List;
import java.util.Map;

/**
 * Exceptions for JsonSchemaSanitization.
 */
public class JsonSchemaSanitizationExceptions {

    static MismatchTypeException mismatchType(Class<?> type, JsonElement argument) {
        return new MismatchTypeException(type, argument);
    }

    static JsonSchemaNumberOutOfBoundException numberOutOfBound(
            Number number, Number minimum, Number maximum, Class<?> type) {
        return new JsonSchemaNumberOutOfBoundException(number, minimum, maximum, type);
    }

    static NonIntegralNumberException hasNonIntegralNumber(Class<?> type, Number number) {
        return new NonIntegralNumberException(type, number);
    }

    static InvalidEnumValueException invalidEnumValue(Class<?> type, JsonElement argument) {
        return new InvalidEnumValueException(type, argument);
    }

    /** Get the type name of the argument. */
    private static String getTypeName(JsonElement argument) {
        if (argument.isJsonNull()) {
            return "null";
        } else if (argument.isJsonArray()) {
            return List.class.getName();
        } else if (argument.isJsonObject()) {
            return Map.class.getName();
        } else if (argument.isJsonPrimitive()) {
            if (argument.getAsJsonPrimitive().isBoolean()) {
                return Boolean.class.getName();
            } else if (argument.getAsJsonPrimitive().isNumber()) {
                return Number.class.getName();
            } else if (argument.getAsJsonPrimitive().isString()) {
                return String.class.getName();
            }
        }
        return argument.getClass().getName();
    }

    static class MismatchTypeException extends JsonSchemaSanitizationException {

        MismatchTypeException(Class<?> type, JsonElement argument) {
            super(
                    String.format(
                            " is not convertable to %s, got %s: %s",
                            type.getName(), getTypeName(argument), argument));
        }
    }

    static class JsonSchemaNumberOutOfBoundException extends JsonSchemaSanitizationException {

        JsonSchemaNumberOutOfBoundException(
                Number number, Number minimum, Number maximum, Class<?> type) {
            super(
                    String.format(
                            " is out of range for %s: [%s, %s], got: %s",
                            type.getName(), minimum, maximum, number));
        }
    }

    static class NonIntegralNumberException extends JsonSchemaSanitizationException {

        NonIntegralNumberException(Class<?> type, Number number) {
            super(String.format(" has non-integer value for %s, got: %s", type, number));
        }
    }

    static class InvalidEnumValueException extends JsonSchemaSanitizationException {

        InvalidEnumValueException(Class<?> type, JsonElement argument) {
            super(
                    String.format(
                            " has invalid enum value for %s, got %s: %s",
                            type, getTypeName(argument), argument));
        }
    }
}
