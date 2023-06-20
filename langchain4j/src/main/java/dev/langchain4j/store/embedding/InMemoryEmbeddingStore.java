package dev.langchain4j.store.embedding;

import dev.embeddings4j.DefaultEmbedding;
import dev.embeddings4j.InMemoryVectorDatabase;
import dev.embeddings4j.SearchNearestQuery;
import dev.embeddings4j.SearchNearestResult;
import dev.langchain4j.data.document.DocumentSegment;
import dev.langchain4j.data.embedding.Embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

public class InMemoryEmbeddingStore implements EmbeddingStore<DocumentSegment> {

    private final InMemoryVectorDatabase db;

    public InMemoryEmbeddingStore(int dimensions, int maxSize) {
        this.db = new InMemoryVectorDatabase(dimensions, maxSize);
    }

    @Override
    public String add(Embedding embedding) {
        String id = generateRandomId();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        db.insert(map(id, embedding));
    }

    @Override
    public String add(Embedding embedding, DocumentSegment documentSegment) {
        String id = generateRandomId();
        db.insert(map(id, embedding, documentSegment));
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<dev.embeddings4j.Embedding<String, String, Float>> embeddingList = embeddings.stream()
                .map(embedding -> map(generateRandomId(), embedding))
                .collect(toList());

        try {
            db.insertAll(embeddingList);

            return embeddingList.stream()
                    .map(dev.embeddings4j.Embedding::id)
                    .collect(toList());

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<DocumentSegment> documentSegments) {
        List<dev.embeddings4j.Embedding<String, String, Float>> embeddingList = new ArrayList<>();

        for (int i = 0; i < embeddings.size(); i++) {
            embeddingList.add(map(generateRandomId(), embeddings.get(i), documentSegments.get(i)));
        }

        try {
            db.insertAll(embeddingList);

            return embeddingList.stream()
                    .map(dev.embeddings4j.Embedding::id)
                    .collect(toList());

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<EmbeddingMatch<DocumentSegment>> findRelevant(Embedding referenceEmbedding, int maxResults) {
        SearchNearestQuery<String, String, Float> searchNearestQuery =
                new SearchNearestQuery<>(map(generateRandomId(), referenceEmbedding), maxResults);

        List<SearchNearestResult<String, String, Float>> relevant = db.execute(searchNearestQuery);

        return relevant.stream()
                .map(SearchNearestResult::embedding)
                .map(embedding -> new EmbeddingMatch<>(
                        embedding.id(),
                        Embedding.from(embedding.vector()),
                        DocumentSegment.from(embedding.contents()))
                )
                .collect(toList());
    }

    private static DefaultEmbedding map(String id, Embedding embedding) {
        return new DefaultEmbedding(id, embedding.vectorAsList());
    }

    private static DefaultEmbedding map(String id, Embedding embedding, DocumentSegment documentSegment) {
        return new DefaultEmbedding(id, documentSegment.text(), embedding.vectorAsList());
    }

    private static String generateRandomId() {
        return UUID.randomUUID().toString();
    }
}
