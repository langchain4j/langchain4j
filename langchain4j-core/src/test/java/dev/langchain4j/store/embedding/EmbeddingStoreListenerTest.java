package dev.langchain4j.store.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreErrorContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreListener;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreRequestContext;
import dev.langchain4j.store.embedding.listener.EmbeddingStoreResponseContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class EmbeddingStoreListenerTest {

    static class TestEmbeddingStore implements EmbeddingStore<String> {

        @Override
        public String add(Embedding embedding) {
            return "id";
        }

        @Override
        public void add(String id, Embedding embedding) {}

        @Override
        public String add(Embedding embedding, String embedded) {
            return "id";
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            return List.of("id-1", "id-2");
        }

        @Override
        public void addAll(List<String> ids, List<Embedding> embeddings, List<String> embedded) {}

        @Override
        public void removeAll(Filter filter) {}

        @Override
        public void removeAll() {}

        @Override
        public void removeAll(java.util.Collection<String> ids) {}

        @Override
        public EmbeddingSearchResult<String> search(EmbeddingSearchRequest request) {
            return new EmbeddingSearchResult<>(List.of());
        }
    }

    static class SuccessfulListener implements EmbeddingStoreListener {

        @Override
        public void onRequest(EmbeddingStoreRequestContext<?> requestContext) {}

        @Override
        public void onResponse(EmbeddingStoreResponseContext<?> responseContext) {}

        @Override
        public void onError(EmbeddingStoreErrorContext<?> errorContext) {}
    }

    static class FailingListener implements EmbeddingStoreListener {

        @Override
        public void onRequest(EmbeddingStoreRequestContext<?> requestContext) {
            throw new RuntimeException("something went wrong in onRequest()");
        }

        @Override
        public void onResponse(EmbeddingStoreResponseContext<?> responseContext) {
            throw new RuntimeException("something went wrong in onResponse()");
        }

        @Override
        public void onError(EmbeddingStoreErrorContext<?> errorContext) {
            throw new RuntimeException("something went wrong in onError()");
        }
    }

    @Test
    void should_call_listeners_in_order_of_declaration() {
        // given
        EmbeddingStoreListener listener1 = spy(new SuccessfulListener());
        EmbeddingStoreListener listener2 = spy(new SuccessfulListener());

        EmbeddingStore<String> store = new TestEmbeddingStore().addListeners(List.of(listener1, listener2));

        // when
        store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                .build());

        // then
        InOrder inOrder = inOrder(listener1, listener2);
        inOrder.verify(listener1).onRequest(any());
        inOrder.verify(listener2).onRequest(any());
        inOrder.verify(listener1).onResponse(any());
        inOrder.verify(listener2).onResponse(any());
        inOrder.verifyNoMoreInteractions();
        verifyNoMoreInteractions(listener1, listener2);
    }

    @Test
    void should_ignore_exceptions_thrown_by_listeners() {
        // given
        EmbeddingStoreListener failingListener = spy(new FailingListener());
        EmbeddingStoreListener successfulListener = spy(new SuccessfulListener());
        EmbeddingStore<String> store =
                new TestEmbeddingStore().addListeners(List.of(failingListener, successfulListener));

        // when - then
        assertThatNoException()
                .isThrownBy(() -> store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                        .build()));

        verify(failingListener).onRequest(any());
        verify(failingListener).onResponse(any());
        verifyNoMoreInteractions(failingListener);

        verify(successfulListener).onRequest(any());
        verify(successfulListener).onResponse(any());
        verifyNoMoreInteractions(successfulListener);
    }

    @Test
    void should_pass_attributes_from_one_listener_to_another() {
        // given
        EmbeddingStoreListener listener1 = spy(new EmbeddingStoreListener() {
            @Override
            public void onRequest(EmbeddingStoreRequestContext<?> requestContext) {
                requestContext.attributes().put("my-attribute", "my-value");
            }
        });
        EmbeddingStoreListener listener2 = spy(new EmbeddingStoreListener() {
            @Override
            public void onResponse(EmbeddingStoreResponseContext<?> responseContext) {
                assertThat(responseContext.attributes()).containsExactly(entry("my-attribute", "my-value"));
            }
        });

        EmbeddingStore<String> store = new TestEmbeddingStore().addListeners(List.of(listener1, listener2));

        // when
        store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                .build());

        // then
        verify(listener2).onResponse(any());
    }

    @Test
    void should_call_onError_when_delegate_throws_exception() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> failingStore = new TestEmbeddingStore() {
            @Override
            public EmbeddingSearchResult<String> search(EmbeddingSearchRequest request) {
                throw new RuntimeException("Embedding store failed");
            }
        };
        EmbeddingStore<String> store = failingStore.addListener(listener);

        // when
        assertThatThrownBy(() -> store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                        .build()))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Embedding store failed");

        // then
        verify(listener).onRequest(any());
        verify(listener).onError(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void should_handle_empty_listeners_list() {
        // given
        EmbeddingStore<String> store = new TestEmbeddingStore().addListeners(List.of());

        // when/then
        assertThatNoException()
                .isThrownBy(() -> store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                        .build()));
    }

    @Test
    void should_handle_null_listeners_list() {
        // given
        EmbeddingStore<String> store = new TestEmbeddingStore().addListeners(null);

        // when/then
        assertThatNoException()
                .isThrownBy(() -> store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                        .build()));
    }

    @Test
    void should_continue_calling_listeners_even_when_some_fail_in_onError() {
        // given
        EmbeddingStoreListener failingListener = spy(new FailingListener());
        EmbeddingStoreListener successfulListener = spy(new SuccessfulListener());

        EmbeddingStore<String> failingStore = new TestEmbeddingStore() {
            @Override
            public EmbeddingSearchResult<String> search(EmbeddingSearchRequest request) {
                throw new RuntimeException("Embedding store failed");
            }
        };
        EmbeddingStore<String> store = failingStore.addListeners(List.of(failingListener, successfulListener));

        // when
        assertThatThrownBy(() -> store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                        .build()))
                .hasMessage("Embedding store failed");

        // then
        verify(failingListener).onRequest(any());
        verify(failingListener).onError(any());
        verify(successfulListener).onRequest(any());
        verify(successfulListener).onError(any());
        verifyNoMoreInteractions(failingListener, successfulListener);
    }

    @Test
    void should_maintain_attributes_across_request_and_error() {
        // given
        EmbeddingStoreListener listener1 = spy(new EmbeddingStoreListener() {
            @Override
            public void onRequest(EmbeddingStoreRequestContext<?> requestContext) {
                requestContext.attributes().put("test-key", "test-value");
            }
        });
        EmbeddingStoreListener listener2 = spy(new EmbeddingStoreListener() {
            @Override
            public void onError(EmbeddingStoreErrorContext<?> errorContext) {
                assertThat(errorContext.attributes()).containsExactly(entry("test-key", "test-value"));
            }
        });

        EmbeddingStore<String> failingStore = new TestEmbeddingStore() {
            @Override
            public EmbeddingSearchResult<String> search(EmbeddingSearchRequest request) {
                throw new RuntimeException("Test error");
            }
        };
        EmbeddingStore<String> store = failingStore.addListeners(List.of(listener1, listener2));

        // when
        assertThatThrownBy(() -> store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                        .build()))
                .hasMessage("Test error");

        // then
        verify(listener2).onError(any());
    }

    @Test
    void should_provide_operation_specific_context_types_for_search() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                .build();

        // when
        store.search(request);

        // then
        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.Search.class);
        EmbeddingStoreRequestContext.Search<?> searchRequestContext =
                (EmbeddingStoreRequestContext.Search<?>) requestCaptor.getValue();
        assertThat(searchRequestContext.searchRequest()).isSameAs(request);

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.Search.class);
        EmbeddingStoreResponseContext.Search<?> searchResponseContext =
                (EmbeddingStoreResponseContext.Search<?>) responseCaptor.getValue();
        assertThat(searchResponseContext.searchResult()).isNotNull();
    }

    @Test
    void should_provide_operation_specific_context_types_for_remove() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);

        // when
        store.remove("id-123");

        // then
        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.Remove.class);
        EmbeddingStoreRequestContext.Remove<?> removeRequestContext =
                (EmbeddingStoreRequestContext.Remove<?>) requestCaptor.getValue();
        assertThat(removeRequestContext.id()).isEqualTo("id-123");

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.Remove.class);
    }

    @Test
    void error_context_should_reference_the_corresponding_request_context() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> failingStore = new TestEmbeddingStore() {
            @Override
            public EmbeddingSearchResult<String> search(EmbeddingSearchRequest request) {
                throw new RuntimeException("Embedding store failed");
            }
        };
        EmbeddingStore<String> store = failingStore.addListener(listener);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(Embedding.from(List.of(1f, 2f, 3f)))
                .build();

        // when
        assertThatThrownBy(() -> store.search(request)).hasMessage("Embedding store failed");

        // then
        ArgumentCaptor<EmbeddingStoreErrorContext> errorCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreErrorContext.class);
        verify(listener).onError(errorCaptor.capture());
        assertThat(errorCaptor.getValue().requestContext()).isInstanceOf(EmbeddingStoreRequestContext.Search.class);
        EmbeddingStoreRequestContext.Search<?> searchRequestContext =
                (EmbeddingStoreRequestContext.Search<?>) errorCaptor.getValue().requestContext();
        assertThat(searchRequestContext.searchRequest()).isSameAs(request);
    }

    @Test
    void should_provide_operation_specific_context_types_for_add() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);
        Embedding embedding = Embedding.from(List.of(1f, 2f, 3f));

        // when
        String returnedId = store.add(embedding);

        // then
        assertThat(returnedId).isEqualTo("id");

        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.Add.class);
        EmbeddingStoreRequestContext.Add<?> addRequestContext =
                (EmbeddingStoreRequestContext.Add<?>) requestCaptor.getValue();
        assertThat(addRequestContext.embedding()).isSameAs(embedding);
        assertThat(addRequestContext.id()).isNull();
        assertThat(addRequestContext.embedded()).isNull();

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.Add.class);
        EmbeddingStoreResponseContext.Add<?> addResponseContext =
                (EmbeddingStoreResponseContext.Add<?>) responseCaptor.getValue();
        assertThat(addResponseContext.returnedId()).isEqualTo("id");
    }

    @Test
    void should_provide_operation_specific_context_types_for_add_with_id() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);
        Embedding embedding = Embedding.from(List.of(1f, 2f, 3f));

        // when
        store.add("id-123", embedding);

        // then
        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.Add.class);
        EmbeddingStoreRequestContext.Add<?> addRequestContext =
                (EmbeddingStoreRequestContext.Add<?>) requestCaptor.getValue();
        assertThat(addRequestContext.embedding()).isSameAs(embedding);
        assertThat(addRequestContext.id()).isEqualTo("id-123");

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.Add.class);
        EmbeddingStoreResponseContext.Add<?> addResponseContext =
                (EmbeddingStoreResponseContext.Add<?>) responseCaptor.getValue();
        assertThat(addResponseContext.returnedId()).isEqualTo("id-123");
    }

    @Test
    void should_provide_operation_specific_context_types_for_add_with_embedded() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);
        Embedding embedding = Embedding.from(List.of(1f, 2f, 3f));

        // when
        String returnedId = store.add(embedding, "embedded");

        // then
        assertThat(returnedId).isEqualTo("id");

        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.Add.class);
        EmbeddingStoreRequestContext.Add<?> addRequestContext =
                (EmbeddingStoreRequestContext.Add<?>) requestCaptor.getValue();
        assertThat(addRequestContext.embedding()).isSameAs(embedding);
        assertThat(addRequestContext.embedded()).isEqualTo("embedded");
    }

    @Test
    void should_provide_operation_specific_context_types_for_addAll() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);
        List<Embedding> embeddings = List.of(Embedding.from(List.of(1f)), Embedding.from(List.of(2f)));

        // when
        List<String> ids = store.addAll(embeddings);

        // then
        assertThat(ids).containsExactly("id-1", "id-2");

        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.AddAll.class);
        EmbeddingStoreRequestContext.AddAll<?> addAllRequestContext =
                (EmbeddingStoreRequestContext.AddAll<?>) requestCaptor.getValue();
        assertThat(addAllRequestContext.embeddings()).isEqualTo(embeddings);
        assertThat(addAllRequestContext.ids()).isEmpty();

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.AddAll.class);
        EmbeddingStoreResponseContext.AddAll<?> addAllResponseContext =
                (EmbeddingStoreResponseContext.AddAll<?>) responseCaptor.getValue();
        assertThat(addAllResponseContext.returnedIds()).containsExactly("id-1", "id-2");
    }

    @Test
    void should_provide_operation_specific_context_types_for_addAll_with_ids_and_embedded_list() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);
        List<String> ids = List.of("a", "b");
        List<Embedding> embeddings = List.of(Embedding.from(List.of(1f)), Embedding.from(List.of(2f)));
        List<String> embedded = List.of("e1", "e2");

        // when
        store.addAll(ids, embeddings, embedded);

        // then
        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.AddAll.class);
        EmbeddingStoreRequestContext.AddAll<?> addAllRequestContext =
                (EmbeddingStoreRequestContext.AddAll<?>) requestCaptor.getValue();
        assertThat(addAllRequestContext.ids()).isEqualTo(ids);
        assertThat(addAllRequestContext.embeddings()).isEqualTo(embeddings);
        assertThat(addAllRequestContext.embeddedList()).isEqualTo(embedded);

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.AddAll.class);
        EmbeddingStoreResponseContext.AddAll<?> addAllResponseContext =
                (EmbeddingStoreResponseContext.AddAll<?>) responseCaptor.getValue();
        assertThat(addAllResponseContext.returnedIds()).isEqualTo(ids);
    }

    @Test
    void should_provide_operation_specific_context_types_for_removeAll_ids() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);

        // when
        store.removeAll(List.of("x", "y"));

        // then
        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.RemoveAllIds.class);
        EmbeddingStoreRequestContext.RemoveAllIds<?> removeAllIdsRequestContext =
                (EmbeddingStoreRequestContext.RemoveAllIds<?>) requestCaptor.getValue();
        assertThat(removeAllIdsRequestContext.ids()).containsExactly("x", "y");

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.RemoveAllIds.class);
    }

    @Test
    void should_provide_operation_specific_context_types_for_removeAll_filter() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);
        Filter filter = mock(Filter.class);

        // when
        store.removeAll(filter);

        // then
        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.RemoveAllFilter.class);
        EmbeddingStoreRequestContext.RemoveAllFilter<?> removeAllFilterRequestContext =
                (EmbeddingStoreRequestContext.RemoveAllFilter<?>) requestCaptor.getValue();
        assertThat(removeAllFilterRequestContext.filter()).isSameAs(filter);

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.RemoveAllFilter.class);
    }

    @Test
    void should_provide_operation_specific_context_types_for_removeAll() {
        // given
        EmbeddingStoreListener listener = spy(new SuccessfulListener());
        EmbeddingStore<String> store = new TestEmbeddingStore().addListener(listener);

        // when
        store.removeAll();

        // then
        ArgumentCaptor<EmbeddingStoreRequestContext> requestCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreRequestContext.class);
        verify(listener).onRequest(requestCaptor.capture());
        assertThat(requestCaptor.getValue()).isInstanceOf(EmbeddingStoreRequestContext.RemoveAll.class);

        ArgumentCaptor<EmbeddingStoreResponseContext> responseCaptor =
                ArgumentCaptor.forClass(EmbeddingStoreResponseContext.class);
        verify(listener).onResponse(responseCaptor.capture());
        assertThat(responseCaptor.getValue()).isInstanceOf(EmbeddingStoreResponseContext.RemoveAll.class);
    }
}
