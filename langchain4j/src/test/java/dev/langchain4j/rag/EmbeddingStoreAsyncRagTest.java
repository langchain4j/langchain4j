package dev.langchain4j.rag;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * End-to-end async RAG through the real {@link InMemoryEmbeddingStore} (native {@code searchAsync}) and
 * {@link EmbeddingStoreContentRetriever#retrieveAsync}, driven by {@link RetrievalAugmentor#augmentAsync}.
 */
class EmbeddingStoreAsyncRagTest {

    // Maps every text to the same vector, so the query matches any stored segment (score 1.0). Genuinely async
    // (overrides doEmbedAsync) so, together with InMemoryEmbeddingStore's native searchAsync, retrieveAsync uses
    // the native async path rather than offloading.
    private static final EmbeddingModel EMBEDDING_MODEL = new EmbeddingModel() {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return Response.from(
                    textSegments.stream().map(s -> Embedding.from(new float[] {1f, 0f, 0f})).toList());
        }

        @Override
        public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
            return CompletableFuture.completedFuture(doEmbed(request));
        }
    };

    @Test
    void inMemoryStore_searchAsync_completes_synchronously() throws Exception {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        Embedding embedding = Embedding.from(new float[] {1f, 0f, 0f});
        store.add(embedding, TextSegment.from("doc"));

        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding)
                .maxResults(1)
                .minScore(0.0)
                .build();

        CompletableFuture<EmbeddingSearchResult<TextSegment>> future = store.searchAsync(request);

        // in-memory search is CPU-only, so the native override completes on the calling thread
        assertThat(future).isCompleted();
        assertThat(future.get().matches()).extracting(m -> m.embedded().text()).containsExactly("doc");
    }

    @Test
    void augmentAsync_retrieves_from_inMemory_store_via_native_retrieveAsync() throws Exception {
        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        TextSegment document = TextSegment.from("relevant document");
        store.add(EMBEDDING_MODEL.embed(document.text()).content(), document);

        EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(EMBEDDING_MODEL)
                .build();

        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new DefaultQueryRouter(retriever))
                .executor(Runnable::run)
                .build();

        UserMessage userMessage = UserMessage.from("query");
        Metadata metadata = Metadata.from(userMessage, SystemMessage.from("Be polite"), null, null);

        AugmentationResult result =
                augmentor.augmentAsync(new AugmentationRequest(userMessage, metadata)).get(5, SECONDS);

        assertThat(result.contents()).extracting(c -> c.textSegment().text()).contains("relevant document");
        assertThat(((UserMessage) result.chatMessage()).singleText()).contains("relevant document");
    }
}
