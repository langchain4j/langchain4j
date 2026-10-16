package dev.langchain4j.rag.content.retriever;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EmbeddingStoreContentRetrieverAsyncTest {

    @Test
    void retrieveAsync_should_offload_both_blocking_components_when_opted_in() throws Exception {
        // Neither fake overrides the async methods, so with offloadBlocking(true) retrieveAsync offloads both the
        // embedding and the search to virtual threads — the result must match the blocking retrieve().
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new FakeEmbeddingModel())
                .embeddingStore(new FakeEmbeddingStore())
                .offloadBlocking(true)
                .build();

        List<Content> sync = retriever.retrieve(Query.from("hello"));
        List<Content> async = retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS);

        assertThat(async).isEqualTo(sync);
        assertThat(async).extracting(c -> c.textSegment().text()).containsExactly("relevant");
    }

    @Test
    void retrieveAsync_should_keep_async_model_native_and_offload_only_the_blocking_store() throws Exception {
        // The realistic production shape: an async embedding model (e.g. OpenAI) with a blocking vector store.
        // The model must run on its native async path; only the store's blocking search is offloaded.
        AtomicInteger modelAsyncCalls = new AtomicInteger();
        AtomicInteger storeSearchCalls = new AtomicInteger();

        EmbeddingModel asyncModel = new FakeEmbeddingModel() {
            @Override
            public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
                modelAsyncCalls.incrementAndGet();
                return java.util.concurrent.CompletableFuture.completedFuture(doEmbed(request));
            }
        };
        FakeEmbeddingStore blockingStore = new FakeEmbeddingStore() {
            @Override
            public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
                storeSearchCalls.incrementAndGet();
                return super.search(request);
            }
        };

        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(asyncModel)
                .embeddingStore(blockingStore)
                .offloadBlocking(true)
                .build();

        List<Content> contents = retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS);

        assertThat(contents).extracting(c -> c.textSegment().text()).containsExactly("relevant");
        assertThat(modelAsyncCalls).hasValue(1); // model used its native async path
        assertThat(storeSearchCalls).hasValue(1); // store's blocking search was offloaded
    }

    @Test
    void retrieveAsync_error_should_name_only_the_blocking_component() {
        // One blocking component (the store) among otherwise-async ones, not opted in: the error names only the
        // store, not the (async) model.
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new NativeAsyncEmbeddingModel())
                .embeddingStore(new FakeEmbeddingStore())
                .build();

        assertThatThrownBy(() -> retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("searchAsync")
                .hasMessageNotContaining("doEmbedAsync");
    }

    @Test
    void retrieveAsync_should_fail_loudly_when_not_async_and_offload_not_opted_in() {
        // Default: the model is blocking and offloadBlocking is off, so retrieveAsync fails at runtime (not silently
        // offload) with the component's own async-unsupported exception and how to fix it.
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new FakeEmbeddingModel())
                .embeddingStore(new FakeEmbeddingStore())
                .build();

        assertThatThrownBy(() -> retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("doEmbedAsync")
                .hasMessageContaining("offloadBlocking(true)");
    }

    @Test
    void retrieveAsync_should_use_native_searchAsync_when_model_and_store_support_async() throws Exception {
        // Both leaves are genuinely async (the model overrides doEmbedAsync, the store overrides searchAsync), so
        // retrieveAsync uses the native async path — it must call searchAsync, not the blocking search().
        AtomicInteger nativeSearchAsyncCalls = new AtomicInteger();
        FakeEmbeddingStore store = new FakeEmbeddingStore() {
            @Override
            public CompletableFuture<EmbeddingSearchResult<TextSegment>> searchAsync(EmbeddingSearchRequest request) {
                nativeSearchAsyncCalls.incrementAndGet();
                return java.util.concurrent.CompletableFuture.completedFuture(search(request));
            }
        };

        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new NativeAsyncEmbeddingModel())
                .embeddingStore(store)
                .build();

        List<Content> async = retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS);

        assertThat(async).extracting(c -> c.textSegment().text()).containsExactly("relevant");
        assertThat(nativeSearchAsyncCalls).hasValue(1);
    }

    @Test
    void retrieveAsync_should_ignore_vector_only_matches_on_the_native_async_path() throws Exception {
        // A native-async model and store where every hit has no text segment: the null guard lives in the shared
        // toContents, so it must hold on the async path too, leaving nothing to retrieve.
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new NativeAsyncEmbeddingModel())
                .embeddingStore(matchesStore(new EmbeddingMatch<TextSegment>(0.9, "vector-only", null, null)))
                .build();

        List<Content> contents = retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS);

        assertThat(contents).isEmpty();
    }

    @Test
    void retrieveAsync_should_keep_the_score_and_embedding_id_of_retained_matches() throws Exception {
        // Content.equals ignores metadata, so the retained match's score and embedding ID are asserted explicitly
        // while its vector-only neighbour is dropped.
        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new NativeAsyncEmbeddingModel())
                .embeddingStore(matchesStore(
                        new EmbeddingMatch<TextSegment>(0.9, "vector-only", null, null),
                        new EmbeddingMatch<>(0.8, "with-text", null, TextSegment.from("relevant"))))
                .build();

        List<Content> contents = retriever.retrieveAsync(Query.from("hello")).get(5, SECONDS);

        assertThat(contents).extracting(c -> c.textSegment().text()).containsExactly("relevant");
        assertThat(contents.get(0).metadata())
                .containsEntry(ContentMetadata.SCORE, 0.8)
                .containsEntry(ContentMetadata.EMBEDDING_ID, "with-text");
    }

    @Test
    void retrieveAsync_cancellation_aborts_the_in_flight_search() {
        // async model (embed completes instantly) + a store whose search never completes, exposed to assert cancel
        CompletableFuture<EmbeddingSearchResult<TextSegment>> pendingSearch =
                new java.util.concurrent.CompletableFuture<>();
        FakeEmbeddingStore store = new FakeEmbeddingStore() {
            @Override
            public CompletableFuture<EmbeddingSearchResult<TextSegment>> searchAsync(EmbeddingSearchRequest request) {
                return pendingSearch;
            }
        };

        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingModel(new NativeAsyncEmbeddingModel())
                .embeddingStore(store)
                .build();

        java.util.concurrent.CompletableFuture<List<Content>> future = retriever.retrieveAsync(Query.from("hello"));

        assertThat(future).isNotDone();
        assertThat(pendingSearch).isNotDone();

        future.cancel(true);

        // cancelling the retriever aborts the in-flight vector-store search
        assertThat(pendingSearch).isCancelled();
    }

    // A genuinely async store that returns exactly the given matches.
    @SafeVarargs
    private static FakeEmbeddingStore matchesStore(EmbeddingMatch<TextSegment>... matches) {
        return new FakeEmbeddingStore() {
            @Override
            public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
                return new EmbeddingSearchResult<>(List.of(matches));
            }

            @Override
            public CompletableFuture<EmbeddingSearchResult<TextSegment>> searchAsync(EmbeddingSearchRequest request) {
                return CompletableFuture.completedFuture(search(request));
            }
        };
    }

    static class FakeEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return Response.from(textSegments.stream()
                    .map(s -> Embedding.from(new float[] {1f, 0f, 0f}))
                    .toList());
        }
    }

    // Genuinely async: overrides doEmbedAsync, so EmbeddingStoreContentRetriever treats the model as async-capable.
    static class NativeAsyncEmbeddingModel extends FakeEmbeddingModel {
        @Override
        public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
            return java.util.concurrent.CompletableFuture.completedFuture(doEmbed(request));
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
