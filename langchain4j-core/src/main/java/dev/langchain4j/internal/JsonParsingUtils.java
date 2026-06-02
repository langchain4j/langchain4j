package dev.langchain4j.internal;

import dev.langchain4j.Internal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

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

    public static <T> ParsedJson<T> extractAndParseJson(String text, ThrowingFunction<String, T> parser)
            throws Exception {
        Exception parseException = null;
        try {
            return new ParsedJson<>(parser.apply(text), text);
        } catch (Exception ex) {
            // Temporarily ignore parsing errors and try to find a JSON block in the text
            parseException = ex;
        }

        List<String> jsonCandidates = findJsonCandidates(text);
        for (int i = jsonCandidates.size() - 1; i >= 0; i--) {
            try {
                String tentativeJson = jsonCandidates.get(i);
                return new ParsedJson<>(parser.apply(tentativeJson), tentativeJson);
            } catch (Exception ignored) {
                // If parsing fails, try to extract a JSON block from the text
            }
        }

        throw parseException;
    }

    private static List<String> findJsonCandidates(String text) {
        List<String> jsonCandidates = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if ((c == '{' || c == '[') && isNotPartOfSameOpeningBraceSequence(text, i)) {
                int jsonEnd = findMatchingJsonEnd(text, i);
                if (jsonEnd >= 0) {
                    jsonCandidates.add(text.substring(i, jsonEnd + 1));
                    i = jsonEnd;
                }
            }
        }

        return jsonCandidates;
    }

    private static int findMatchingJsonEnd(String text, int jsonStart) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;

        for (int i = jsonStart; i < text.length(); i++) {
            char c = text.charAt(i);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                stack.push(c);
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty() || !bracesMatch(stack.peek(), c)) {
                    return -1;
                }

                stack.pop();
                if (stack.isEmpty()) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static boolean isNotPartOfSameOpeningBraceSequence(String text, int index) {
        return index == 0 || text.charAt(index - 1) != text.charAt(index);
    }

    private static boolean bracesMatch(char openingBrace, char closingBrace) {
        return openingBrace == '{' && closingBrace == '}' || openingBrace == '[' && closingBrace == ']';
    }
}
