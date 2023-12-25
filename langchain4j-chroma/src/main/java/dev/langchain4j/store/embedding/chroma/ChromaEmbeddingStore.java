package dev.langchain4j.store.embedding.chroma;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.randomUUID;
import static java.time.Duration.ofSeconds;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

/**
 * Represents a store for embeddings using the Chroma backend.
 * Always uses cosine distance as the distance metric.
 */
public class ChromaEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final ChromaClient chromaClient;
    private final String collectionId;

    /**
     * Initializes a new instance of ChromaEmbeddingStore with the specified parameters.
     *
     * @param baseUrl        The base URL of the Chroma service.
     * @param collectionName The name of the collection in the Chroma service. If not specified, "default" will be used.
     * @param timeout        The timeout duration for the Chroma client. If not specified, 5 seconds will be used.
     */
    public ChromaEmbeddingStore(String baseUrl, String collectionName, Duration timeout) {
        collectionName = getOrDefault(collectionName, "default");

        this.chromaClient = new ChromaClient(baseUrl, getOrDefault(timeout, ofSeconds(5)));

        Collection collection = chromaClient.collection(collectionName);
        if (collection == null) {
            Collection createdCollection = chromaClient.createCollection(new CreateCollectionRequest(collectionName));
            collectionId = createdCollection.id();
        } else {
            collectionId = collection.id();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String collectionName;
        private Duration timeout;

        /**
         * @param baseUrl The base URL of the Chroma service.
         * @return builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param collectionName The name of the collection in the Chroma service. If not specified, "default" will be used.
         * @return builder
         */
        public Builder collectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        /**
         * @param timeout The timeout duration for the Chroma client. If not specified, 5 seconds will be used.
         * @return builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public ChromaEmbeddingStore build() {
            return new ChromaEmbeddingStore(this.baseUrl, this.collectionName, this.timeout);
        }
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
                .map(embedding -> randomUUID())
                .collect(toList());

        addAllInternal(ids, embeddings, null);

        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {

        List<String> ids = embeddings.stream()
                .map(embedding -> randomUUID())
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

        chromaClient.addEmbeddings(collectionId, addEmbeddingsRequest);
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding referenceEmbedding, int maxResults, double minScore) {
        QueryRequest queryRequest = new QueryRequest(referenceEmbedding.vectorAsList(), maxResults);

        QueryResponse queryResponse = chromaClient.queryCollection(collectionId, queryRequest);

        List<EmbeddingMatch<TextSegment>> matches = toEmbeddingMatches(queryResponse);

        return matches.stream()
                .filter(match -> match.score() >= minScore)
                .collect(toList());
    }

    private static List<EmbeddingMatch<TextSegment>> toEmbeddingMatches(QueryResponse queryResponse) {
        List<EmbeddingMatch<TextSegment>> embeddingMatches = new ArrayList<>();

        for (int i = 0; i < queryResponse.ids().get(0).size(); i++) {

            double score = distanceToScore(queryResponse.distances().get(0).get(i));
            String embeddingId = queryResponse.ids().get(0).get(i);
            Embedding embedding = Embedding.from(queryResponse.embeddings().get(0).get(i));
            TextSegment textSegment = toTextSegment(queryResponse, i);

            embeddingMatches.add(new EmbeddingMatch<>(score, embeddingId, embedding, textSegment));
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

    private static TextSegment toTextSegment(QueryResponse queryResponse, int i) {
        String text = queryResponse.documents().get(0).get(i);
        Map<String, String> metadata = queryResponse.metadatas().get(0).get(i);
        return text == null ? null : TextSegment.from(text, metadata == null ? new Metadata() : new Metadata(metadata));
    }
}
