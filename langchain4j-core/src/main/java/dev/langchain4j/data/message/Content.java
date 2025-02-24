package dev.langchain4j.data.message;

/**
 * Abstract base interface for message content.
 *
 * @see TextContent
 * @see ImageContent
 * @see AudioContent
 * @see VideoContent
 * @see PdfFileContent
 * @see TextFileContent
 */
public interface Content {
    /**
     * Returns the type of content.
     *
     * <p>Can be used to cast the content to the correct type.</p>
     *
     * @return The type of content.
     */
    ContentType type();
}
