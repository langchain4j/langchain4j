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

    public static <T> ParsedJson<T> extractAndParseJson(String text, Class<T> type) throws Exception {
        return extractAndParseJson(text, s -> Json.fromJson(s, type));
    }

    public static <T> ParsedJson<T> extractAndParseJson(String text, ThrowingFunction<String, T> parser) throws Exception {
        Exception parseException = null;
        try {
            return new ParsedJson<>(parser.apply(text), text);
        } catch (Exception ex) {
            // Temporarily ignore parsing errors and try to find a JSON block in the text
            parseException = ex;
        }

        int index = text.length();
        while (true) {

            int jsonEnd = findJsonEnd(text, index);
            if (jsonEnd < 0) {
                throw parseException;
            }

            int jsonStart = findJsonStart(text, jsonEnd, text.charAt(jsonEnd));
            if (jsonStart < 0) {
                throw parseException;
            }

            try {
                String tentativeJson = text.substring(jsonStart, jsonEnd + 1);
                return new ParsedJson<>(parser.apply(tentativeJson), tentativeJson);
            } catch (Exception ignored) {
                // If parsing fails, try to extract a JSON block from the text
            }
            index = jsonStart; // Move to the next character after the found JSON block
        }
    }

    /**
     * Finds the last closing brace ('}' or ']') that is at nesting level 0,
     * meaning it is not inside a string. This handles streaming truncation
     * cases where the JSON might be cut off mid-value, and the last brace
     * in the raw text might actually be inside a string value.
     *
     * <p>For example, in streaming mode a truncated JSON like:
     * {@code {"response": "The sky appears blue due to Rayleigh scattering."}}
     * might appear as:
     * {@code {"response": "The sky appears blue due to Rayleigh scattering."}
     * where the final '}' is missing, or worse, might have the final brace
     * inside the string value itself.
     */
    private static int findJsonEnd(String text, int fromIndex) {
        int jsonMapEnd = findLastNestingLevelZeroBrace(text, '}', fromIndex);
        int jsonListEnd = findLastNestingLevelZeroBrace(text, ']', fromIndex);
        return Math.max(jsonMapEnd, jsonListEnd);
    }

    /**
     * Finds the last occurrence of the given brace that is at nesting level 0
     * (not inside a string and not nested inside another object/array).
     * Properly handles escape sequences in strings.
     */
    private static int findLastNestingLevelZeroBrace(String text, char brace, int fromIndex) {
        int inStringIndex = text.lastIndexOf('"', fromIndex);
        int braceIndex = text.lastIndexOf(brace, fromIndex);

        if (braceIndex < 0) return -1;

        // Walk backward from braceIndex, tracking string state
        int i = braceIndex - 1;
        boolean inString = false;

        while (i >= 0) {
            char c = text.charAt(i);

            if (c == '\\' && inString) {
                // Skip escaped character
                i--;
            } else if (c == '"') {
                // Toggle string state
                inString = !inString;
            }

            if (!inString && c == brace) {
                // Found another brace at nesting level 0, this one is later
                return i;
            }
            i--;
        }

        // No earlier brace at nesting level 0 found; return original if it's at level 0
        // Re-check if our original brace is inside a string
        if (isBraceAtNestingLevelZero(text, braceIndex, brace)) {
            return braceIndex;
        }
        return -1;
    }

    /**
     * Checks if the brace at the given index is at nesting level 0
     * (not inside a string).
     */
    private static boolean isBraceAtNestingLevelZero(String text, int braceIndex, char brace) {
        boolean inString = false;
        for (int i = 0; i < braceIndex; i++) {
            char c = text.charAt(i);
            if (c == '\\' && inString) {
                i++; // Skip escaped character
            } else if (c == '"') {
                inString = !inString;
            }
        }
        return !inString;
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
