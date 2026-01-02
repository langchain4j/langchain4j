package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onError;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onRequest;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onResponse;

import dev.langchain4j.Internal;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreErrorContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreListener;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreOperation;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreRequestContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreResponseContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Internal
final class ObservingEmbeddingStore<Embedded> implements EmbeddingStore<Embedded> {

    private final EmbeddingStore<Embedded> delegate;
    private final List<EmbeddingStoreListener> listeners;

    ObservingEmbeddingStore(EmbeddingStore<Embedded> delegate, List<EmbeddingStoreListener> listeners) {
        this.delegate = ensureNotNull(delegate, "delegate");
        this.listeners = ensureNotNull(listeners, "listeners");
    }

    EmbeddingStore<Embedded> withAdditionalListeners(List<EmbeddingStoreListener> additionalListeners) {
        if (additionalListeners == null || additionalListeners.isEmpty()) {
            return this;
        }
        List<EmbeddingStoreListener> merged = new ArrayList<>(listeners);
        merged.addAll(additionalListeners);
        return new ObservingEmbeddingStore<>(delegate, merged);
    }

    @Override
    public String add(Embedding embedding) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.ADD, this, attributes, null, null, embedding, null, null, null, null, null);
        onRequest(requestContext, listeners);
        try {
            String id = delegate.add(embedding);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.ADD, this, attributes, requestContext, id, null, null);
            onResponse(responseContext, listeners);
            return id;
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.ADD, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public void add(String id, Embedding embedding) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.ADD, this, attributes, id, null, embedding, null, null, null, null, null);
        onRequest(requestContext, listeners);
        try {
            delegate.add(id, embedding);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.ADD, this, attributes, requestContext, id, null, null);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.ADD, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public String add(Embedding embedding, Embedded embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.ADD, this, attributes, null, null, embedding, null, embedded, null, null, null);
        onRequest(requestContext, listeners);
        try {
            String id = delegate.add(embedding, embedded);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.ADD, this, attributes, requestContext, id, null, null);
            onResponse(responseContext, listeners);
            return id;
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.ADD, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.ADD_ALL,
                this,
                attributes,
                null,
                null,
                null,
                embeddings,
                null,
                null,
                null,
                null);
        onRequest(requestContext, listeners);
        try {
            List<String> ids = delegate.addAll(embeddings);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.ADD_ALL, this, attributes, requestContext, null, ids, null);
            onResponse(responseContext, listeners);
            return ids;
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.ADD_ALL, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.ADD_ALL,
                this,
                attributes,
                null,
                null,
                null,
                embeddings,
                null,
                embedded,
                null,
                null);
        onRequest(requestContext, listeners);
        try {
            List<String> ids = delegate.addAll(embeddings, embedded);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.ADD_ALL, this, attributes, requestContext, null, ids, null);
            onResponse(responseContext, listeners);
            return ids;
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.ADD_ALL, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.ADD_ALL,
                this,
                attributes,
                null,
                ids,
                null,
                embeddings,
                null,
                embedded,
                null,
                null);
        onRequest(requestContext, listeners);
        try {
            delegate.addAll(ids, embeddings, embedded);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.ADD_ALL, this, attributes, requestContext, null, ids, null);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.ADD_ALL, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public void remove(String id) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.REMOVE, this, attributes, id, null, null, null, null, null, null, null);
        onRequest(requestContext, listeners);
        try {
            delegate.remove(id);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.REMOVE, this, attributes, requestContext, null, null, null);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.REMOVE, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        List<String> idsAsList = (ids == null) ? null : new ArrayList<>(ids);
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.REMOVE_ALL_IDS,
                this,
                attributes,
                null,
                idsAsList,
                null,
                null,
                null,
                null,
                null,
                null);
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll(ids);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.REMOVE_ALL_IDS, this, attributes, requestContext, null, idsAsList, null);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.REMOVE_ALL_IDS, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public void removeAll(Filter filter) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.REMOVE_ALL_FILTER,
                this,
                attributes,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                filter);
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll(filter);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.REMOVE_ALL_FILTER, this, attributes, requestContext, null, null, null);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.REMOVE_ALL_FILTER, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public void removeAll() {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.REMOVE_ALL, this, attributes, null, null, null, null, null, null, null, null);
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll();
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.REMOVE_ALL, this, attributes, requestContext, null, null, null);
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.REMOVE_ALL, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest request) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext<Embedded> requestContext = new EmbeddingStoreRequestContext<>(
                EmbeddingStoreOperation.SEARCH, this, attributes, null, null, null, null, null, null, request, null);
        onRequest(requestContext, listeners);
        try {
            EmbeddingSearchResult<Embedded> result = delegate.search(request);
            EmbeddingStoreResponseContext<Embedded> responseContext = new EmbeddingStoreResponseContext<>(
                    EmbeddingStoreOperation.SEARCH, this, attributes, requestContext, null, null, result);
            onResponse(responseContext, listeners);
            return result;
        } catch (Exception error) {
            onError(
                    new EmbeddingStoreErrorContext<>(
                            error, EmbeddingStoreOperation.SEARCH, this, attributes, requestContext),
                    listeners);
            throw error;
        }
    }
}
