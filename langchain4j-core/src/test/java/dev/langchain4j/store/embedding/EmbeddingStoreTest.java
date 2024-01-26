package dev.langchain4j.store.embedding;

import dev.langchain4j.data.embedding.Embedding;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

class EmbeddingStoreTest implements WithAssertions {

    public static class MinimalEmbeddingStore implements EmbeddingStore<String> {
        @Override
        public String add(Embedding embedding) {
            return null;
        }

        @Override
        public void add(String id, Embedding embedding) {
        }

        @Override
        public String add(Embedding embedding, String s) {
            return null;
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return null;
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings, List<String> embedded) {
            return null;
        }

        @Override
        public List<EmbeddingMatch<String>> findRelevant(
                Embedding referenceEmbedding, int maxResults, double minScore) {
            return Collections.singletonList(
                    new EmbeddingMatch<>(
                        0.5,
                        "id",
                        referenceEmbedding,
                        "embedded"));
        }
    }

    public static class MemoryIdEmbeddingStore extends MinimalEmbeddingStore {
        @Override
        public List<EmbeddingMatch<String>> findRelevant(
                Object memoryId, Embedding referenceEmbedding, int maxResults, double minScore) {
            return Collections.singletonList(
                    new EmbeddingMatch<>(
                        0.5,
                        "id",
                        referenceEmbedding,
                        "embedded"));
        }
    }

    @Test
    public void test() {
        EmbeddingStore<String> store = new MinimalEmbeddingStore();

        Embedding referenceEmbedding = new Embedding(new float[]{0.5f, 1.5f});

        assertThat(store.findRelevant(referenceEmbedding, 12))
                .contains(
                        new EmbeddingMatch<>(
                            0.5,
                            "id",
                            referenceEmbedding,
                            "embedded"));

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> store.findRelevant("MemoryId", referenceEmbedding, 12))
                .withMessage("Not implemented");
    }

    @Test
    public void test_memoryId() {
        EmbeddingStore<String> store = new MemoryIdEmbeddingStore();

        Embedding referenceEmbedding = new Embedding(new float[]{0.5f, 1.5f});

        assertThat(store.findRelevant("abc", referenceEmbedding, 12))
                .contains(
                        new EmbeddingMatch<>(
                            0.5,
                            "id",
                            referenceEmbedding,
                            "embedded"));
    }
}