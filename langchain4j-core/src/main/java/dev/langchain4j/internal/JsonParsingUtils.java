package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import java.util.Optional;

@Internal
public class JsonParsingUtils {

    @FunctionalInterface
    public interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }

    public record ParsedJson<T>(T value, String json) {}

    public static <T> Either<Exception, ParsedJson<T>> extractAndParseJson(String text, Class<T> type) {
        return extractAndParseJson(text, s -> Json.fromJson(s, type));
    }

    public static <T> Either<Exception, ParsedJson<T>> extractAndParseJson(String text, ThrowingFunction<String, T> parser) {
        Exception parsingException = null;
        try {
            return Either.right(new ParsedJson<>(parser.apply(text), text));
        } catch (Exception e) {
            // Temporarily ignore parsing errors and try to find a JSON block in the text
            parsingException = e;
        }

        int index = text.length();
        while (true) {

            int jsonEnd = findJsonEnd(text, index);
            if (jsonEnd < 0) {
                return Either.left(parsingException);
            }

            int jsonStart = findJsonStart(text, jsonEnd, text.charAt(jsonEnd));
            if (jsonStart < 0) {
                return Either.left(parsingException);
            }

            try {
                String tentativeJson = text.substring(jsonStart, jsonEnd + 1);
                return Either.right(new ParsedJson<>(parser.apply(tentativeJson), tentativeJson));
            } catch (Exception ignored) {
                // If parsing fails, try to extract a JSON block from the text
            }
            index = jsonStart; // Move to the next character after the found JSON block
        }
    }

    private static int findJsonEnd(String text, int fromIndex) {
        int jsonMapEnd = text.lastIndexOf('}', fromIndex);
        int jsonListEnd = text.lastIndexOf(']', fromIndex);
        return Math.max(jsonMapEnd, jsonListEnd);
    }

    private static int findJsonStart(String text, int jsonEnd, char closingBrace) {
        char openingBrace = closingBrace == '}' ? '{' : '[';
        int braceCount = 0;

        for (int i = jsonEnd; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == openingBrace) {
                braceCount++;
                if (braceCount == 0) {
                    return i == 0 || text.charAt(i - 1) != openingBrace ? i : -1;
                }
            } else if (c == closingBrace) {
                braceCount--;
            }
        }
        return -1;
    }
}
