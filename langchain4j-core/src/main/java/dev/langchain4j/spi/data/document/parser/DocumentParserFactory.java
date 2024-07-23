package dev.langchain4j.spi.data.document.parser;

import dev.langchain4j.data.document.DocumentParser;

/**
 * A factory for creating {@link DocumentParser} instances through SPI.
 * <br>
 * Available implementations: {@code ApacheTikaDocumentParserFactory}
 * in the {@code langchain4j-document-parser-apache-tika} module.
 * For the "Easy RAG", import {@code langchain4j-easy-rag} module.
 */
public interface DocumentParserFactory {

    DocumentParser create();
}
