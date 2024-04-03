package dev.langchain4j.data.document.splitter.recursive;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.model.embedding.HuggingFaceTokenizer;
import dev.langchain4j.spi.data.document.splitter.DocumentSplitterFactory;

public class RecursiveDocumentSplitterFactory implements DocumentSplitterFactory {

    @Override
    public DocumentSplitter create() {
        return DocumentSplitters.recursive(300, 30, new HuggingFaceTokenizer());
    }
}
