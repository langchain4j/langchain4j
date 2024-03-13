package dev.langchain4j.rag.easy;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.embedding.BertTokenizer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.extern.slf4j.Slf4j;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;
import static java.util.Collections.singletonList;

/**
 * TODO
 */
@Slf4j
public class EasyRAG {

    // TODO thread safe? lazy?
    private static final EmbeddingModel EMBEDDING_MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    // TODO document what is happening under the hood

    /**
     * TODO
     *
     * @param filePath
     * @return
     */
    public static InMemoryEmbeddingStore<TextSegment> ingestFile(String filePath) {
        return ingestFiles(singletonList(filePath));
    }


    /**
     * TODO
     *
     * @param filePath
     * @return
     */
    public static InMemoryEmbeddingStore<TextSegment> ingestFile(String filePath, IngestionConfig config) {
        // TODO use config
        return ingestFiles(singletonList(filePath));
    }

    // TODO overload every method with config

    /**
     * TODO
     *
     * @param filePaths
     * @return
     */
    public static InMemoryEmbeddingStore<TextSegment> ingestFiles(Iterable<String> filePaths) {
        return ingestFiles(filePaths, new IngestionConfig(300, 30));
    }

    /**
     * TODO
     *
     * @param filePaths
     * @return
     */
    public static InMemoryEmbeddingStore<TextSegment> ingestFiles(Iterable<String> filePaths, IngestionConfig config) {

        InMemoryEmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

        // TODO measure perf with different models and number of docs
        EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // TODO use chars instead of bert tokens?
        DocumentSplitter splitter = recursive(config.segmentSize(), config.segmentOverlap(), new BertTokenizer());

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        for (String filePath : filePaths) {
            try {
                Document document = FileSystemDocumentLoader.loadDocument(filePath, new ApacheTikaDocumentParser());
                ingestor.ingest(document);
            } catch (Exception e) {
                log.warn("Failed to load '{}', skipping it", filePath, e);
            }
        }

        return embeddingStore;
    }

    /**
     * TODO
     *
     * @param directoryPath
     * @return
     */
    public static InMemoryEmbeddingStore<TextSegment> ingestDirectory(String directoryPath) {
        return null;
    }

    /**
     * TODO
     *
     * @param directoryPath
     * @return
     */
    public static InMemoryEmbeddingStore<TextSegment> ingestDirectoryRecursively(String directoryPath) {
        return null;
    }

    /**
     * TODO
     *
     * @param directoryPath
     * @param glob
     * @return
     */
    public static InMemoryEmbeddingStore<TextSegment> ingestDirectory(String directoryPath, String glob) {
        return null;
    }

    /**
     * TODO
     *
     * @param directoryPath
     * @param glob
     * @return
     */
    public static InMemoryEmbeddingStore<TextSegment> ingestDirectoryRecursively(String directoryPath, String glob) {
        return null;
    }

    /**
     * TODO
     * TODO max results, min score
     *
     * @param embeddingStore
     * @return
     */
    public static ContentRetriever createContentRetriever(InMemoryEmbeddingStore<TextSegment> embeddingStore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(EMBEDDING_MODEL)
                .build();
    }

    /**
     * TODO
     * TODO max results, min score
     *
     * @param embeddingStore
     * @return
     */
    // TODO name
    public static ContentRetriever createContentRetriever(InMemoryEmbeddingStore<TextSegment> embeddingStore,
                                                          RetrievalConfig config) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(EMBEDDING_MODEL)
                .maxResults(config.maxResults())
                .minScore(config.minScore())
                .build();
    }
}
