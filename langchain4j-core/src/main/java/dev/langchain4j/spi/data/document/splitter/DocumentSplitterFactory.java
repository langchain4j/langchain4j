package dev.langchain4j.spi.data.document.splitter;

import dev.langchain4j.data.document.DocumentSplitter;

/**
 * A factory for creating {@link DocumentSplitter} instances through SPI.
 * <br>
 * Available implementations: {@code RecursiveDocumentSplitterFactory}
 * in the {@code langchain4j-easy-rag} module.
 */
public interface DocumentSplitterFactory {

    DocumentSplitter create();
}
