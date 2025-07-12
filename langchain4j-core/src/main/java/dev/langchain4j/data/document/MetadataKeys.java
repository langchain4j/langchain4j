package dev.langchain4j.data.document;

/**
 * Standard metadata keys used throughout the LangChain4j framework.
 * <p>
 * This class provides a centralized location for commonly used metadata key constants
 * to ensure consistency across the codebase and help to prevent typos when working with
 * document and text segment metadata.
 * <p>
 * These keys are used by various components including document splitters, loaders,
 * and embedding stores to attach standardized metadata to {@link Document}s and
 * {@link dev.langchain4j.data.segment.TextSegment}s.
 */
public final class MetadataKeys {

    /**
     * Metadata key used to store the sequential index/position of a text segment within its parent document.
     * <p>
     * This key is automatically added by document splitters (such as
     * {@link dev.langchain4j.data.document.splitter.HierarchicalDocumentSplitter} and its subclasses)
     * to indicate the order in which text segments were created from the original document.
     * The index starts from 0 for the first segment.
     * <p>
     * <b>Type:</b> String representation of an integer (e.g., "0", "1", "2")<br>
     * <b>Usage:</b> Automatically set by document splitters<br>
     * <b>Purpose:</b> Preserves the original order of text segments, useful for:
     * <ul>
     *     <li>Reconstructing the original document structure</li>
     *     <li>Maintaining semantic relationships between adjacent segments</li>
     *     <li>Debugging and tracing document processing workflows</li>
     * </ul>
     * <p>
     * <b>Example usage:</b>
     * <pre>{@code
     * TextSegment segment = TextSegment.from("Some text",
     *     Metadata.from(MetadataKeys.INDEX, "0"));
     *
     * // Retrieving the index
     * String index = segment.metadata().getString(MetadataKeys.INDEX);
     * }</pre>
     */
    public static final String INDEX = "index";

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private MetadataKeys() {
        // Utility class - no instances allowed
    }
}
