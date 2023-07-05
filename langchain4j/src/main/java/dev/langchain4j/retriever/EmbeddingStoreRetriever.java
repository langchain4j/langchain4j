package dev.langchain4j.retriever;

import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class EmbeddingStoreRetriever implements Retriever<DocumentSegment> {

    private final EmbeddingStore<DocumentSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final int maxResults;
    private final Double minSimilarity;

    public EmbeddingStoreRetriever(EmbeddingStore<DocumentSegment> embeddingStore,
                                   EmbeddingModel embeddingModel,
                                   int maxResults,
                                   Double minSimilarity) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.maxResults = maxResults;
        this.minSimilarity = minSimilarity;
    }

    @Override
    public List<DocumentSegment> findRelevant(String text) {

        Embedding embeddedText = embeddingModel.embed(text).get();

        List<EmbeddingMatch<DocumentSegment>> relevant;
        if (minSimilarity == null) {
            relevant = embeddingStore.findRelevant(embeddedText, maxResults);
        } else {
            relevant = embeddingStore.findRelevant(embeddedText, maxResults, minSimilarity);
        }

        return relevant.stream()
                .map(EmbeddingMatch::embedded)
                .collect(toList());
    }

    public static EmbeddingStoreRetriever from(EmbeddingStore<DocumentSegment> embeddingStore, EmbeddingModel embeddingModel) {
        return new EmbeddingStoreRetriever(embeddingStore, embeddingModel, 2, null);
    }

    public static EmbeddingStoreRetriever from(EmbeddingStore<DocumentSegment> embeddingStore, EmbeddingModel embeddingModel, int maxResults) {
        return new EmbeddingStoreRetriever(embeddingStore, embeddingModel, maxResults, null);
    }

    public static EmbeddingStoreRetriever from(EmbeddingStore<DocumentSegment> embeddingStore, EmbeddingModel embeddingModel, int maxResults, double minSimilarity) {
        return new EmbeddingStoreRetriever(embeddingStore, embeddingModel, maxResults, minSimilarity);
    }
}
