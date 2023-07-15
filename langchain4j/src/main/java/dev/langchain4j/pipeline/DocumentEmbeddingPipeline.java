package dev.langchain4j.pipeline;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;

/**
 * This pipeline is intended to streamline the process of loading documents, splitting them into segments,
 * embedding these segments, and finally storing them in an embedding store.
 */
public class DocumentEmbeddingPipeline {

    private final List<Document> documents = new ArrayList<>();
    private DocumentSplitter splitter;
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    public static DocumentEmbeddingPipeline builder() {
        return new DocumentEmbeddingPipeline();
    }

    public DocumentEmbeddingPipeline addDocument(Document document) {
        this.documents.add(document);
        return this;
    }

    public DocumentEmbeddingPipeline addDocuments(Document... documents) {
        return addDocuments(asList(documents));
    }

    public DocumentEmbeddingPipeline addDocuments(List<Document> documents) {
        this.documents.addAll(documents);
        return this;
    }

    public DocumentEmbeddingPipeline splitter(DocumentSplitter splitter) {
        this.splitter = splitter;
        return this;
    }

    public DocumentEmbeddingPipeline embeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        return this;
    }

    public DocumentEmbeddingPipeline embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
        return this;
    }

    public void run() {
        List<TextSegment> segments = splitter.split(documents);
        List<Embedding> embeddings = embeddingModel.embedAll(segments).get();
        embeddingStore.addAll(embeddings, segments);
    }
}
