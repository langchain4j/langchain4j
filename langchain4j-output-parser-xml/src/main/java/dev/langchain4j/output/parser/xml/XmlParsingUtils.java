package dev.langchain4j.output.parser.xml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilities for extracting XML from LLM output that may contain
 * surrounding text or other content.
 */
final class XmlParsingUtils {

    // Pattern to match XML content with paired opening/closing tags
    private static final Pattern XML_PATTERN =
            Pattern.compile("<([a-zA-Z][a-zA-Z0-9_-]*)(?:\\s[^>]*)?>.*?</\\1>", Pattern.DOTALL);

    // Pattern to match self-closing XML tags (e.g., <empty/>, <tag attr="value"/>)
    private static final Pattern SELF_CLOSING_XML_PATTERN =
            Pattern.compile("<([a-zA-Z][a-zA-Z0-9_-]*)(?:\\s[^>]*)?/>", Pattern.DOTALL);

    private XmlParsingUtils() {}

    /**
     * Extracts and parses XML from text that may contain non-XML content.
     *
     * @param text   the text potentially containing XML
     * @param parser function to parse the XML
     * @param <T>    the result type
     * @return parsed result wrapped in ParsedXml
     * @throws Exception if no valid XML can be extracted or parsing fails
     */
    static <T> ParsedXml<T> extractAndParseXml(String text, ThrowingFunction<String, T> parser) throws Exception {
        // 1. Try parsing the entire text as XML
        try {
            String trimmed = text.trim();
            return new ParsedXml<>(parser.apply(trimmed), trimmed);
        } catch (Exception ignored) {
            // Continue with extraction attempts
        }

        Exception lastException = null;

        // 2. Try finding XML tag structure (paired tags)
        Matcher xmlMatcher = XML_PATTERN.matcher(text);
        while (xmlMatcher.find()) {
            String xml = xmlMatcher.group();
            try {
                return new ParsedXml<>(parser.apply(xml), xml);
            } catch (Exception e) {
                lastException = e;
            }
        }

        // 3. Try finding self-closing XML tags
        Matcher selfClosingMatcher = SELF_CLOSING_XML_PATTERN.matcher(text);
        while (selfClosingMatcher.find()) {
            String xml = selfClosingMatcher.group();
            try {
                return new ParsedXml<>(parser.apply(xml), xml);
            } catch (Exception e) {
                lastException = e;
            }
        }

        // Propagate parsing exception if XML was found but couldn't be parsed
        if (lastException != null) {
            throw lastException;
        }

        throw new XmlOutputParsingException("No valid XML found in output");
    }

    /**
     * Result of XML extraction and parsing.
     *
     * @param value the parsed value
     * @param xml   the extracted XML string
     * @param <T>   the value type
     */
    record ParsedXml<T>(T value, String xml) {}

    /**
     * A function that may throw an exception.
     *
     * @param <T> input type
     * @param <R> result type
     */
    @FunctionalInterface
    interface ThrowingFunction<T, R> {
        R apply(T t) throws Exception;
    }
}
