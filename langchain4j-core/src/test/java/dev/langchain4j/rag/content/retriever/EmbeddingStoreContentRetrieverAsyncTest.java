package dev.langchain4j.rag.content.retriever;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EmbeddingStoreContentRetrieverAsyncTest {

    @Test
    void retrieveAsync_should_produce_the_same_contents_as_retrieve() throws Exception {
        // Neither fake overrides the async methods, so retrieveAsync composes the embedAllAsync/searchAsync
        // *defaults* (offloaded to a virtual thread) — the result must match the blocking retrieve().
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new FakeEmbeddingModel())
                .embeddingStore(new FakeEmbeddingStore())
                .build();

        List<Content> sync = retriever.retrieve(Query.from("hello"));
        List<Content> async = retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS);

        assertThat(async).isEqualTo(sync);
        assertThat(async).extracting(c -> c.textSegment().text()).containsExactly("relevant");
    }

    @Test
    void retrieveAsync_should_use_a_stores_native_searchAsync_when_overridden() throws Exception {
        AtomicInteger nativeSearchAsyncCalls = new AtomicInteger();
        FakeEmbeddingStore store = new FakeEmbeddingStore() {
            @Override
            public java.util.concurrent.CompletableFuture<EmbeddingSearchResult<TextSegment>> searchAsync(
                    EmbeddingSearchRequest request) {
                nativeSearchAsyncCalls.incrementAndGet();
                return java.util.concurrent.CompletableFuture.completedFuture(search(request));
            }
        };

        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new FakeEmbeddingModel())
                .embeddingStore(store)
                .build();

        List<Content> async = retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS);

        assertThat(async).extracting(c -> c.textSegment().text()).containsExactly("relevant");
        assertThat(nativeSearchAsyncCalls).hasValue(1);
    }

    static class FakeEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return Response.from(
                    textSegments.stream().map(s -> Embedding.from(new float[] {1f, 0f, 0f})).toList());
        }
    }

    static class FakeEmbeddingStore implements EmbeddingStore<TextSegment> {
        @Override
        public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
            EmbeddingMatch<TextSegment> match =
                    new EmbeddingMatch<>(0.9, "id-1", request.queryEmbedding(), TextSegment.from("relevant"));
            return new EmbeddingSearchResult<>(List.of(match));
        }

        @Override
        public String add(Embedding embedding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(String id, Embedding embedding) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String add(Embedding embedding, TextSegment embedded) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            throw new UnsupportedOperationException();
        }
    }
}
