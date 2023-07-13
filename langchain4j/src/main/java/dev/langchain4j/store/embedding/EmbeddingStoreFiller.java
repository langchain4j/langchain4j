package dev.langchain4j.store.embedding;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.ParagraphSplitter;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;

import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

// TODO document
public class EmbeddingStoreFiller {

    private List<Document> documents;
    private DocumentSplitter splitter = new ParagraphSplitter();
    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();

    public static EmbeddingStoreFiller builder() {
        return new EmbeddingStoreFiller();
    }

    public EmbeddingStoreFiller document(Document document) {
        this.documents = singletonList(document);
        return this;
    }

    public EmbeddingStoreFiller documents(Document... documents) {
        return documents(asList(documents));
    }

    public EmbeddingStoreFiller documents(List<Document> documents) {
        this.documents = documents;
        return this;
    }

    public EmbeddingStoreFiller splitter(DocumentSplitter splitter) {
        this.splitter = splitter;
        return this;
    }

    public EmbeddingStoreFiller embeddingModel(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        return this;
    }

    public EmbeddingStoreFiller embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
        this.embeddingStore = embeddingStore;
        return this;
    }

    public EmbeddingStore<TextSegment> fill() {
        List<TextSegment> segments = splitter.split(documents);
        List<Embedding> embeddings = embeddingModel.embedAll(segments).get();
        embeddingStore.addAll(embeddings, segments);
        return embeddingStore;
    }
}
