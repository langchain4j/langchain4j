package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents a store for embeddings with Chroma backend.
 * The cosine distance is always used as the distance method for Chroma implementation
 */
public class ChromaEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final ChromaClient chromaClient;
    private final String collectionId;

    /**
     * Initializes a new instance of ChromaEmbeddingStore with the specified parameters.
     *
     * @param urlBase        The base URL of the Chroma service.
     * @param timeout        The timeout duration for the Chroma client.
     * @param collectionName The name of the collection in the Chroma service.
     */
    public ChromaEmbeddingStore(String urlBase, Duration timeout, String collectionName) {
        this.chromaClient = new ChromaClient(urlBase, timeout);

        Collection response = chromaClient.getCollection(collectionName);

        collectionName = collectionName == null ? "default" : collectionName;

        if (response == null) {
            Collection collection = chromaClient.createCollection(new CollectionCreationRequest(collectionName));
            collectionId = collection.id();
        } else {
            collectionId = response.id();
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = Utils.randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = Utils.randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {

        List<String> ids = embeddings.stream()
                .map(embedding -> Utils.randomUUID())
                .collect(toList());

        addAllInternal(ids, embeddings, null);

        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {

        List<String> ids = embeddings.stream()
                .map(embedding -> Utils.randomUUID())
                .collect(toList());

        addAllInternal(ids, embeddings, textSegments);

        return ids;
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(singletonList(id), singletonList(embedding), textSegment == null ? null : singletonList(textSegment));
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        AddEmbeddingsRequest addEmbeddingsRequest = AddEmbeddingsRequest.builder()
                .embeddings(embeddings.stream()
                        .map(Embedding::vector)
                        .collect(toList()))
                .ids(ids)
                .metadatas(textSegments == null
                        ? null
                        : textSegments.stream()
                        .map(TextSegment::metadata)
                        .map(Metadata::asMap)
                        .collect(toList()))
                .documents(textSegments == null
                        ? null
                        : textSegments.stream()
                        .map(TextSegment::text)
                        .collect(toList()))
                .build();

        chromaClient.addEmbedding(collectionId, addEmbeddingsRequest);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        return findRelevant(referenceEmbedding, maxResults, 0);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        QueryRequest queryRequest = new QueryRequest(referenceEmbedding.vectorAsList(), maxResults);

        QueryResponse nearestNeighbors = chromaClient.getNearestNeighbors(collectionId, queryRequest);

        return toEmbeddingMatches(nearestNeighbors);
    }

    private static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(QueryResponse nearestNeighbors) {
        List<EmbeddingMatch<TextSegment>> embeddingMatches = new ArrayList<>();

        for (int i = 0; i < nearestNeighbors.ids().get(0).size(); i++) {

            double score = distanceToScore(nearestNeighbors.distances().get(0).get(i));
            String embeddingId = nearestNeighbors.ids().get(0).get(i);
            Embedding embedding = Embedding.from(nearestNeighbors.embeddings().get(0).get(i));
            TextSegment embedded = toEmbedded(nearestNeighbors, i);

            EmbeddingMatch<TextSegment> textSegmentEmbeddingMatch = new EmbeddingMatch<>(score, embeddingId, embedding, embedded);

            embeddingMatches.add(textSegmentEmbeddingMatch);
        }
        return embeddingMatches;
    }

    /**
     * By default, cosine distance will be used. For details: <a href="https://docs.trychroma.com/usage-guide"></a>
     * Converts a cosine distance in the range [0, 2] to a score in the range [0, 1].
     *
     * @param distance The distance value.
     * @return The converted score.
     */
    private static double distanceToScore(double distance) {
        return 1 - (distance / 2);
    }

    private static TextSegment toEmbedded(QueryResponse nearestNeighbors, int i) {
        String text = nearestNeighbors.documents().get(0).get(i);
        Map<String, String> metadata = nearestNeighbors.metadatas().get(0).get(i);
        return text == null ? null : TextSegment.from(text, metadata == null ? null : new Metadata(metadata));
    }

}
