package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onError;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onRequest;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onResponse;

import dev.langchain4j.Internal;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreErrorContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreListener;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreRequestContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreResponseContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Internal
final class ListeningEmbeddingStore<Embedded> implements EmbeddingStore<Embedded> {

    private final EmbeddingStore<Embedded> delegate;
    private final List<EmbeddingStoreListener> listeners;

    ListeningEmbeddingStore(EmbeddingStore<Embedded> delegate, List<EmbeddingStoreListener> listeners) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.listeners = copy(listeners);
    }

    EmbeddingStore<Embedded> withAdditionalListeners(List<EmbeddingStoreListener> additionalListeners) {
        if (additionalListeners == null || additionalListeners.isEmpty()) {
            return this;
        }
        List<EmbeddingStoreListener> merged = new ArrayList<>(listeners);
        merged.addAll(additionalListeners);
        return new ListeningEmbeddingStore<>(delegate, merged);
    }

    @Override
    public String add(Embedding embedding) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Add<Embedded> requestContext =
                new EmbeddingStoreRequestContext.Add<>(this, attributes, embedding);
        onRequest(requestContext, listeners);
        try {
            String id = delegate.add(embedding);
            EmbeddingStoreResponseContext.Add<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.Add<>(requestContext, attributes, id);
            onResponse(responseContext, listeners);
            return id;
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void add(String id, Embedding embedding) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Add<Embedded> requestContext =
                new EmbeddingStoreRequestContext.Add<>(this, attributes, id, embedding);
        onRequest(requestContext, listeners);
        try {
            delegate.add(id, embedding);
            EmbeddingStoreResponseContext.Add<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.Add<>(requestContext, attributes, id);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public String add(Embedding embedding, Embedded embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Add<Embedded> requestContext =
                new EmbeddingStoreRequestContext.Add<>(this, attributes, null, embedding, embedded);
        onRequest(requestContext, listeners);
        try {
            String id = delegate.add(embedding, embedded);
            EmbeddingStoreResponseContext.Add<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.Add<>(requestContext, attributes, id);
            onResponse(responseContext, listeners);
            return id;
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.AddAll<Embedded> requestContext =
                new EmbeddingStoreRequestContext.AddAll<>(this, attributes, null, embeddings, null);
        onRequest(requestContext, listeners);
        try {
            List<String> ids = delegate.addAll(embeddings);
            EmbeddingStoreResponseContext.AddAll<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.AddAll<>(requestContext, attributes, ids);
            onResponse(responseContext, listeners);
            return ids;
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.AddAll<Embedded> requestContext =
                new EmbeddingStoreRequestContext.AddAll<>(this, attributes, null, embeddings, embedded);
        onRequest(requestContext, listeners);
        try {
            List<String> ids = delegate.addAll(embeddings, embedded);
            EmbeddingStoreResponseContext.AddAll<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.AddAll<>(requestContext, attributes, ids);
            onResponse(responseContext, listeners);
            return ids;
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.AddAll<Embedded> requestContext =
                new EmbeddingStoreRequestContext.AddAll<>(this, attributes, ids, embeddings, embedded);
        onRequest(requestContext, listeners);
        try {
            delegate.addAll(ids, embeddings, embedded);
            EmbeddingStoreResponseContext.AddAll<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.AddAll<>(requestContext, attributes, ids);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void remove(String id) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Remove<Embedded> requestContext =
                new EmbeddingStoreRequestContext.Remove<>(this, attributes, id);
        onRequest(requestContext, listeners);
        try {
            delegate.remove(id);
            EmbeddingStoreResponseContext.Remove<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.Remove<>(requestContext, attributes);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        List<String> idsAsList = (ids == null) ? null : new ArrayList<>(ids);
        EmbeddingStoreRequestContext.RemoveAllIds<Embedded> requestContext =
                new EmbeddingStoreRequestContext.RemoveAllIds<>(this, attributes, idsAsList);
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll(ids);
            EmbeddingStoreResponseContext.RemoveAllIds<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.RemoveAllIds<>(requestContext, attributes);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void removeAll(Filter filter) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.RemoveAllFilter<Embedded> requestContext =
                new EmbeddingStoreRequestContext.RemoveAllFilter<>(this, attributes, filter);
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll(filter);
            EmbeddingStoreResponseContext.RemoveAllFilter<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.RemoveAllFilter<>(requestContext, attributes);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void removeAll() {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.RemoveAll<Embedded> requestContext =
                new EmbeddingStoreRequestContext.RemoveAll<>(this, attributes);
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll();
            EmbeddingStoreResponseContext.RemoveAll<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.RemoveAll<>(requestContext, attributes);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest request) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Search<Embedded> requestContext =
                new EmbeddingStoreRequestContext.Search<>(this, attributes, request);
        onRequest(requestContext, listeners);
        try {
            EmbeddingSearchResult<Embedded> result = delegate.search(request);
            EmbeddingStoreResponseContext.Search<Embedded> responseContext =
                    new EmbeddingStoreResponseContext.Search<>(requestContext, attributes, result);
            onResponse(responseContext, listeners);
            return result;
        } catch (Exception error) {
            onError(new EmbeddingStoreErrorContext<>(error, requestContext, attributes), listeners);
            throw error;
        }
    }
}
