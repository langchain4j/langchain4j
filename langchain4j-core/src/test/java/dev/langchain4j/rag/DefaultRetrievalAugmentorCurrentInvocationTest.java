package dev.langchain4j.rag;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.internal.CurrentInvocation;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Metadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultRetrievalAugmentorCurrentInvocationTest {

    private final InvocationContext invocationContext =
            InvocationContext.builder().invocationId(UUID.randomUUID()).build();

    @Test
    void should_run_the_content_retriever_as_work_of_the_invocation() {
        AtomicReference<UUID> seen = new AtomicReference<>();
        ContentRetriever retriever = query -> {
            seen.set(CurrentInvocation.invocationId());
            return List.of(Content.from("source"));
        };
        DefaultRetrievalAugmentor augmentor =
                DefaultRetrievalAugmentor.builder().contentRetriever(retriever).build();

        augmentor.augment(augmentationRequest());

        assertThat(seen.get()).isEqualTo(invocationContext.invocationId());
        assertThat(CurrentInvocation.invocationId()).isNull();
    }

    @Test
    void should_run_the_content_injector_of_async_augmentation_as_work_of_the_invocation() throws Exception {
        AtomicReference<UUID> seen = new AtomicReference<>();
        ContentInjector injector = (contents, chatMessage) -> {
            seen.set(CurrentInvocation.invocationId());
            return chatMessage;
        };
        DefaultRetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(completingOnAnotherThread())
                .contentRetriever(new ContentRetriever() {

                    @Override
                    public List<Content> retrieve(Query query) {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public CompletableFuture<List<Content>> retrieveAsync(Query query) {
                        return CompletableFuture.completedFuture(List.of(Content.from("source")));
                    }
                })
                .contentInjector(injector)
                .build();

        augmentor.augmentAsync(augmentationRequest()).get(5, SECONDS);

        assertThat(seen.get()).isEqualTo(invocationContext.invocationId());
    }

    private AugmentationRequest augmentationRequest() {
        UserMessage userMessage = UserMessage.from("Hello");
        Metadata metadata = Metadata.builder()
                .chatMessage(userMessage)
                .invocationContext(invocationContext)
                .build();
        return new AugmentationRequest(userMessage, metadata);
    }

    private static QueryTransformer completingOnAnotherThread() {
        return new QueryTransformer() {

            @Override
            public Collection<Query> transform(Query query) {
                throw new UnsupportedOperationException();
            }

            @Override
            public CompletableFuture<Collection<Query>> transformAsync(Query query) {
                // completes on another thread, so that the next stages run there
                return CompletableFuture.supplyAsync(
                        () -> List.of(query), CompletableFuture.delayedExecutor(50, MILLISECONDS));
            }
        };
    }
}
