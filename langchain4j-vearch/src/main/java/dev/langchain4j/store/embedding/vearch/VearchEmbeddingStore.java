package dev.langchain4j.store.embedding.vearch;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.time.Duration;
import java.util.List;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * TODO
 */
public class VearchEmbeddingStore implements EmbeddingStore<TextSegment> {

    private VearchClient vearchClient;

    public VearchEmbeddingStore(String baseUrl,
                                Duration timeout,
                                String databaseName,
                                String spaceName) {
        // TODO: constructor

        // Step 0: initialize some attribute

        // Step 1: check whether db exist, if not, create it

        // Step 2: check whether space exist, if not, create it
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        /**
         * TODO: Builder
         */
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        List<String> ids = embeddings.stream()
                .map(ignored -> randomUUID())
                .collect(toList());
        addAllInternal(ids, embeddings, embedded);
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        // TODO
        return null;
    }

    private void addInternal(String id, Embedding embedding, TextSegment embedded) {
        addAllInternal(singletonList(id), singletonList(embedding), embedded == null ? null : singletonList(embedded));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        ids = ensureNotEmpty(ids, "ids");
        embeddings = ensureNotEmpty(embeddings, "embeddings");
        ensureTrue(ids.size() == embeddings.size(), "ids size is not equal to embeddings size");
        ensureTrue(embedded == null || embeddings.size() == embedded.size(), "embeddings size is not equal to embedded size");

        // TODO: add embeddings

    }
}
