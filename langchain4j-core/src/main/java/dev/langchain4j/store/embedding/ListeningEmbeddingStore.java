package dev.langchain4j.store.embedding;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onError;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onRequest;
import static dev.langchain4j.store.embedding.EmbeddingStoreListenerUtils.onResponse;

import dev.langchain4j.Internal;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.internal.CurrentInvocation;
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
                EmbeddingStoreRequestContext.Add.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .embedding(embedding)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            String id = delegate.add(embedding);
            EmbeddingStoreResponseContext.Add<Embedded> responseContext =
                    EmbeddingStoreResponseContext.Add.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .returnedId(id)
                            .build();
            onResponse(responseContext, listeners);
            return id;
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void add(String id, Embedding embedding) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Add<Embedded> requestContext =
                EmbeddingStoreRequestContext.Add.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .id(id)
                        .embedding(embedding)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            delegate.add(id, embedding);
            EmbeddingStoreResponseContext.Add<Embedded> responseContext =
                    EmbeddingStoreResponseContext.Add.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .returnedId(id)
                            .build();
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public String add(Embedding embedding, Embedded embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Add<Embedded> requestContext =
                EmbeddingStoreRequestContext.Add.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .embedding(embedding)
                        .embedded(embedded)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            String id = delegate.add(embedding, embedded);
            EmbeddingStoreResponseContext.Add<Embedded> responseContext =
                    EmbeddingStoreResponseContext.Add.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .returnedId(id)
                            .build();
            onResponse(responseContext, listeners);
            return id;
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.AddAll<Embedded> requestContext =
                EmbeddingStoreRequestContext.AddAll.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .embeddings(embeddings)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            List<String> ids = delegate.addAll(embeddings);
            EmbeddingStoreResponseContext.AddAll<Embedded> responseContext =
                    EmbeddingStoreResponseContext.AddAll.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .returnedIds(ids)
                            .build();
            onResponse(responseContext, listeners);
            return ids;
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<Embedded> embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.AddAll<Embedded> requestContext =
                EmbeddingStoreRequestContext.AddAll.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .embeddings(embeddings)
                        .embeddedList(embedded)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            List<String> ids = delegate.addAll(embeddings, embedded);
            EmbeddingStoreResponseContext.AddAll<Embedded> responseContext =
                    EmbeddingStoreResponseContext.AddAll.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .returnedIds(ids)
                            .build();
            onResponse(responseContext, listeners);
            return ids;
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.AddAll<Embedded> requestContext =
                EmbeddingStoreRequestContext.AddAll.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .ids(ids)
                        .embeddings(embeddings)
                        .embeddedList(embedded)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            delegate.addAll(ids, embeddings, embedded);
            EmbeddingStoreResponseContext.AddAll<Embedded> responseContext =
                    EmbeddingStoreResponseContext.AddAll.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .returnedIds(ids)
                            .build();
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void remove(String id) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Remove<Embedded> requestContext =
                EmbeddingStoreRequestContext.Remove.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .id(id)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            delegate.remove(id);
            EmbeddingStoreResponseContext.Remove<Embedded> responseContext =
                    EmbeddingStoreResponseContext.Remove.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .build();
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        List<String> idsAsList = (ids == null) ? null : new ArrayList<>(ids);
        EmbeddingStoreRequestContext.RemoveAllIds<Embedded> requestContext =
                EmbeddingStoreRequestContext.RemoveAllIds.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .ids(idsAsList)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll(ids);
            EmbeddingStoreResponseContext.RemoveAllIds<Embedded> responseContext =
                    EmbeddingStoreResponseContext.RemoveAllIds.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .build();
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void removeAll(Filter filter) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.RemoveAllFilter<Embedded> requestContext =
                EmbeddingStoreRequestContext.RemoveAllFilter.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .filter(filter)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll(filter);
            EmbeddingStoreResponseContext.RemoveAllFilter<Embedded> responseContext =
                    EmbeddingStoreResponseContext.RemoveAllFilter.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .build();
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public void removeAll() {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.RemoveAll<Embedded> requestContext =
                EmbeddingStoreRequestContext.RemoveAll.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            delegate.removeAll();
            EmbeddingStoreResponseContext.RemoveAll<Embedded> responseContext =
                    EmbeddingStoreResponseContext.RemoveAll.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .build();
            onResponse(responseContext, listeners);
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest request) {
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        EmbeddingStoreRequestContext.Search<Embedded> requestContext =
                EmbeddingStoreRequestContext.Search.<Embedded>builder()
                        .embeddingStore(this)
                        .attributes(attributes)
                        .searchRequest(request)
                        .invocationId(CurrentInvocation.invocationId())
                        .build();
        onRequest(requestContext, listeners);
        try {
            EmbeddingSearchResult<Embedded> result = delegate.search(request);
            EmbeddingStoreResponseContext.Search<Embedded> responseContext =
                    EmbeddingStoreResponseContext.Search.<Embedded>builder()
                            .requestContext(requestContext)
                            .attributes(attributes)
                            .searchResult(result)
                            .build();
            onResponse(responseContext, listeners);
            return result;
        } catch (Exception error) {
            onError(errorContext(error, requestContext, attributes), listeners);
            throw error;
        }
    }

    private static <E> EmbeddingStoreErrorContext<E> errorContext(
            Exception error, EmbeddingStoreRequestContext<E> requestContext, Map<Object, Object> attributes) {
        return EmbeddingStoreErrorContext.<E>builder()
                .error(error)
                .requestContext(requestContext)
                .attributes(attributes)
                .build();
    }
}
