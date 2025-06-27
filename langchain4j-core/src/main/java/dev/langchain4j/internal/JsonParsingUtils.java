package dev.langchain4j.internal;

import java.lang.reflect.Type;

import static dev.langchain4j.internal.Utils.quoted;

public class JsonParsingUtils {

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }

    public record ParsedJson<T>(T value, String json) { }

    public static <T> ParsedJson<T> extractAndParseJson(String text, Class<T> type) {
        return extractAndParseJson(text, type.getName(), s -> Json.fromJson(s, type));
    }

    public static <T> ParsedJson<T> extractAndParseJson(String text, String typeName, ThrowingFunction<String, T> parser) {
        Exception parseException;
        try {
            return new ParsedJson<>(parser.apply(text), text);
        } catch (Exception ignored) {
            parseException = ignored;
        }

        int index = 0;
        while (true) {

            int jsonStart = findJsonStart(text, index);
            if (jsonStart < 0) {
                throw outputParsingException(text, typeName, parseException);
            }

            int jsonEnd = findJsonEnd(text, jsonStart, text.charAt(jsonStart));
            if (jsonEnd < 0) {
                throw outputParsingException(text, typeName, parseException);
            }

            try {
                String tentativeJson = text.substring(jsonStart, jsonEnd + 1);
                return new ParsedJson<>(parser.apply(tentativeJson), tentativeJson);
            } catch (Exception ignored) {
                // If parsing fails, try to extract a JSON block from the text
            }
            index = jsonEnd + 1; // Move to the next character after the found JSON block
        }
    }

    private static int findJsonStart(String text, int fromIndex) {
        int jsonMapStart = text.indexOf('{', fromIndex);
        int jsonListStart = text.indexOf('[', fromIndex);
        if (jsonMapStart < 0) {
            return jsonListStart;
        }
        if (jsonListStart < 0) {
            return jsonMapStart;
        }
        return Math.min(jsonMapStart, jsonListStart);
    }

    private static int findJsonEnd(String text, int jsonStart, char openingBrace) {
        char closingBrace = openingBrace == '{' ? '}' : ']';
        int braceCount = 0;

        for (int i = jsonStart; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == openingBrace) {
                braceCount++;
            } else if (c == closingBrace) {
                braceCount--;
                if (braceCount == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static OutputParsingException outputParsingException(String text, Type type) {
        return outputParsingException(text, type.getTypeName(), null);
    }

    public static OutputParsingException outputParsingException(String text, String type, Throwable cause) {
        return new OutputParsingException("Failed to parse %s into %s".formatted(quoted(text), type), cause);
    }
}
