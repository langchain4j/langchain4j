package dev.langchain4j.rag.content.retriever.easy;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.BertTokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.util.Collection;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;
import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

/**
 * TODO
 */
public class EasyContentRetriever { // TODO name ContentRetrievers?

    // TODO builder for customization?

    public static ContentRetriever fromFile(String filePath) {

        Document document = loadDocument(filePath, new ApacheTikaDocumentParser());

        DocumentSplitter splitter = recursive(300, 30, new BertTokenizer());

        // TODO use a better model?
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build()
                .ingest(document);

        // TODO can be added later:
        // TODO calculate hash of all files, persist in the file with hash in the name
        // TODO if store was already persisted and hash is the same, skip embedding again
        // TODO user should be aware of this
        // TODO also use model name and version in the file name

        return EmbeddingStoreContentRetriever.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    public static ContentRetriever fromFiles(String... filePaths) {
        throw new RuntimeException("Not implemented");
    }

    public static ContentRetriever fromFiles(Collection<String> filePaths) {
        throw new RuntimeException("Not implemented");
    }

    public static ContentRetriever fromDirectory(String directory) {
        throw new RuntimeException("Not implemented");
    }

    public static ContentRetriever fromDirectory(String directory, String glob) {
        throw new RuntimeException("Not implemented");
    }

    public static ContentRetriever fromDirectoryRecursively(String directory) {
        throw new RuntimeException("Not implemented");
    }

    public static ContentRetriever fromDirectoryRecursively(String directory, String glob) {
        throw new RuntimeException("Not implemented");
    }
}
