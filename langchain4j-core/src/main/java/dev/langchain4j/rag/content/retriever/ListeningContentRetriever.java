package dev.langchain4j.rag.content.retriever;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.rag.content.retriever.ContentRetrieverListenerUtils.onError;
import static dev.langchain4j.rag.content.retriever.ContentRetrieverListenerUtils.onRequest;
import static dev.langchain4j.rag.content.retriever.ContentRetrieverListenerUtils.onResponse;

import dev.langchain4j.Internal;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverErrorContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverRequestContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverResponseContext;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Internal
final class ListeningContentRetriever implements ContentRetriever {

    private final ContentRetriever delegate;
    private final List<ContentRetrieverListener> listeners;

    ListeningContentRetriever(ContentRetriever delegate, List<ContentRetrieverListener> listeners) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.listeners = copy(listeners);
    }

    ContentRetriever withAdditionalListeners(Collection<ContentRetrieverListener> additionalListeners) {
        if (additionalListeners == null || additionalListeners.isEmpty()) {
            return this;
        }
        List<ContentRetrieverListener> merged = new ArrayList<>(listeners);
        merged.addAll(additionalListeners);
        return new ListeningContentRetriever(delegate, merged);
    }

    @Override
    public List<Content> retrieve(Query query) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ContentRetrieverRequestContext requestContext = ContentRetrieverRequestContext.builder()
                .query(query)
                .contentRetriever(this)
                .attributes(attributes)
                .build();
        onRequest(requestContext, listeners);
        try {
            List<Content> contents = delegate.retrieve(query);
            onResponse(
                    ContentRetrieverResponseContext.builder()
                            .contents(contents)
                            .query(query)
                            .contentRetriever(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            return contents;
        } catch (Exception error) {
            onError(
                    ContentRetrieverErrorContext.builder()
                            .error(error)
                            .query(query)
                            .contentRetriever(this)
                            .attributes(attributes)
                            .build(),
                    listeners);
            throw error;
        }
    }
}
