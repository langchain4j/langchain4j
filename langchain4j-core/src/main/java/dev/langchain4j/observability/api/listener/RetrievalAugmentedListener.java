package dev.langchain4j.observability.api.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;

/**
 * A listener for {@link RetrievalAugmentedEvent}, which occurs once per invocation of an AI Service that uses
 * retrieval-augmented generation (RAG), configured with {@code contentRetriever(...)} or
 * {@code retrievalAugmentor(...)}, after the contents to add to the user message were retrieved. To observe
 * each {@link ContentRetriever} call separately, with the query it received, use a
 * {@link ContentRetrieverListener}.
 *
 * @since 1.23.0
 */
@FunctionalInterface
@Experimental
public interface RetrievalAugmentedListener extends AiServiceListener<RetrievalAugmentedEvent> {
    @Override
    default Class<RetrievalAugmentedEvent> getEventClass() {
        return RetrievalAugmentedEvent.class;
    }
}
