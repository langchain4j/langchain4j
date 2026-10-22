package dev.langchain4j.observability.api.listener;

import dev.langchain4j.Experimental;
import dev.langchain4j.observability.api.event.RetrievalAugmentedEvent;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;

/**
 * A listener for {@link RetrievalAugmentedEvent}, which occurs after the retrieval augmentor of an AI Service has
 * retrieved the contents that are added to the user message. It is called once per AI Service invocation. To observe
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
