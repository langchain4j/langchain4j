package dev.langchain4j.data.document.splitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.model.Tokenizer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;


public class DocumentByMarkdownHeaderSplitter extends HierarchicalDocumentSplitter {

    private final String joinDelimiter;
    private final Pattern pattern;

    /**
     * Constructor for DocumentByMarkdownHeaderSplitter.
     *
     * @param headerLevels          The levels of headers to split by.
     * @param joinDelimiter         The delimiter to use when joining segments.
     * @param maxSegmentSizeInChars The maximum size of a segment in characters.
     * @param maxOverlapSizeInChars The maximum size of overlap between segments in characters.
     */
    public DocumentByMarkdownHeaderSplitter(
            Set<MarkdownHeaderLevel> headerLevels,
            String joinDelimiter,
            int maxSegmentSizeInChars,
            int maxOverlapSizeInChars) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, null);
        this.joinDelimiter = ensureNotNull(joinDelimiter, "joinDelimiter");
        this.pattern = buildPattern(headerLevels);

    }

    /**
     * Constructor for DocumentByMarkdownHeaderSplitter.
     *
     * @param headerLevels          The levels of headers to split by.
     * @param joinDelimiter         The delimiter to use when joining segments.
     * @param maxSegmentSizeInChars The maximum size of a segment in characters.
     * @param maxOverlapSizeInChars The maximum size of overlap between segments in characters.
     * @param subSplitter           The sub-splitter to use for further splitting the segments.
     */
    public DocumentByMarkdownHeaderSplitter(
            Set<MarkdownHeaderLevel> headerLevels,
            String joinDelimiter,
            int maxSegmentSizeInChars,
            int maxOverlapSizeInChars,
            DocumentSplitter subSplitter) {
        super(maxSegmentSizeInChars, maxOverlapSizeInChars, null, subSplitter);
        this.joinDelimiter = joinDelimiter;
        this.pattern = buildPattern(headerLevels);
    }

    /**
     * Constructor for DocumentByMarkdownHeaderSplitter.
     *
     * @param headerLevels           The levels of headers to split by.
     * @param joinDelimiter          The delimiter to use when joining segments.
     * @param maxSegmentSizeInTokens The maximum size of a segment in tokens.
     * @param maxOverlapSizeInTokens The maximum size of overlap between segments in tokens.
     * @param tokenizer              The tokenizer to use for splitting the text into tokens.
     */
    public DocumentByMarkdownHeaderSplitter(
            Set<MarkdownHeaderLevel> headerLevels,
            String joinDelimiter,
            int maxSegmentSizeInTokens,
            int maxOverlapSizeInTokens,
            Tokenizer tokenizer) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, null);
        this.joinDelimiter = joinDelimiter;
        this.pattern = buildPattern(headerLevels);
    }

    /**
     * Constructor for DocumentByMarkdownHeaderSplitter.
     *
     * @param headerLevels           The levels of headers to split by.
     * @param joinDelimiter          The delimiter to use when joining segments.
     * @param maxSegmentSizeInTokens The maximum size of a segment in tokens.
     * @param maxOverlapSizeInTokens The maximum size of overlap between segments in tokens.
     * @param tokenizer              The tokenizer to use for splitting the text into tokens.
     * @param subSplitter            The sub-splitter to use for further splitting the segments.
     */
    public DocumentByMarkdownHeaderSplitter(
            Set<MarkdownHeaderLevel> headerLevels,
            String joinDelimiter,
            int maxSegmentSizeInTokens,
            int maxOverlapSizeInTokens,
            Tokenizer tokenizer,
            DocumentSplitter subSplitter) {
        super(maxSegmentSizeInTokens, maxOverlapSizeInTokens, tokenizer, subSplitter);
        this.joinDelimiter = joinDelimiter;
        this.pattern = buildPattern(headerLevels);
    }

    /**
     * Splits the given text by the pattern.
     *
     * @param text The text to split.
     * @return An array of strings, each representing a segment of the text.
     */
    @Override
    public String[] split(String text) {
        return splitTextByPattern(text);
    }

    @Override
    public String joinDelimiter() {
        return this.joinDelimiter;
    }

    @Override
    public DocumentSplitter defaultSubSplitter() {
        return null;
    }

    /**
     * Builds a pattern for splitting text based on the given header levels.
     *
     * @param headerLevels The levels of headers to split by.
     * @return A Pattern object representing the pattern.
     */
    private Pattern buildPattern(final Set<MarkdownHeaderLevel> headerLevels) {
        String patternString = headerLevels.stream()
                .map(level -> "^" + String.format("(#{%d})\\s+(.*)", level.getLevel()))
                .collect(Collectors.joining("|"));
        return Pattern.compile(patternString, Pattern.MULTILINE);
    }

    /**
     * Splits the given text by the pattern.
     *
     * @param text The text to split.
     * @return An array of strings, each representing a segment of the text.
     */
    private String[] splitTextByPattern(final String text) {
        Matcher matcher = this.pattern.matcher(text);
        List<String> sections = new ArrayList<>();
        int lastEnd = 0;

        while (matcher.find()) {
            int start = matcher.start();
            if (start != 0) {
                sections.add(text.substring(lastEnd, start));
            }
            lastEnd = start;
        }
        if (lastEnd < text.length()) {
            sections.add(text.substring(lastEnd));
        }
        return sections.toArray(new String[0]);
    }
}
