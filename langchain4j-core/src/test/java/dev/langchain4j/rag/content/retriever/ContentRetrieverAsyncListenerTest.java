package dev.langchain4j.rag.content.retriever;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverErrorContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverRequestContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverResponseContext;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ContentRetrieverAsyncListenerTest {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_preserve_async_retrieval_and_listener_contexts(boolean alreadyCompleted) throws Exception {
        Query query = Query.from("query");
        List<Content> contents = List.of(Content.from("content"));
        CompletableFuture<List<Content>> source = new CompletableFuture<>();
        if (alreadyCompleted) {
            source.complete(contents);
        }
        ContentRetrieverListener first = new ContentRetrieverListener() {
            @Override
            public void onRequest(ContentRetrieverRequestContext context) {
                context.attributes().put("key", "value");
            }
        };
        ContentRetrieverListener second = mock(ContentRetrieverListener.class);
        ContentRetriever retriever = asyncRetriever(source).addListener(first).addListener(second);

        CompletableFuture<List<Content>> result = retriever.retrieveAsync(query);

        ArgumentCaptor<ContentRetrieverRequestContext> request =
                ArgumentCaptor.forClass(ContentRetrieverRequestContext.class);
        verify(second).onRequest(request.capture());
        assertThat(request.getValue().query()).isSameAs(query);
        assertThat(request.getValue().contentRetriever()).isSameAs(retriever);
        assertThat(request.getValue().attributes()).containsEntry("key", "value");
        if (!alreadyCompleted) {
            assertThat(result).isNotDone();
            verifyNoMoreInteractions(second);
            source.complete(contents);
        }

        assertThat(result.get(1, SECONDS)).isSameAs(contents);
        ArgumentCaptor<ContentRetrieverResponseContext> response =
                ArgumentCaptor.forClass(ContentRetrieverResponseContext.class);
        verify(second).onResponse(response.capture());
        assertThat(response.getValue().contents()).isEqualTo(contents);
        assertThat(response.getValue().query()).isSameAs(query);
        assertThat(response.getValue().contentRetriever()).isSameAs(retriever);
        assertThat(response.getValue().attributes()).isSameAs(request.getValue().attributes());
        verifyNoMoreInteractions(second);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_report_original_async_error(boolean wrapped) {
        Query query = Query.from("query");
        RuntimeException failure = new RuntimeException("Retrieval failed");
        CompletableFuture<List<Content>> source = new CompletableFuture<>();
        ContentRetrieverListener listener = mock(ContentRetrieverListener.class);
        ContentRetriever retriever = asyncRetriever(source).addListener(listener);

        CompletableFuture<List<Content>> result = retriever.retrieveAsync(query);
        source.completeExceptionally(wrapped ? new CompletionException(failure) : failure);

        assertThatThrownBy(() -> result.get(1, SECONDS)).cause().isSameAs(failure);
        ArgumentCaptor<ContentRetrieverRequestContext> request =
                ArgumentCaptor.forClass(ContentRetrieverRequestContext.class);
        ArgumentCaptor<ContentRetrieverErrorContext> error =
                ArgumentCaptor.forClass(ContentRetrieverErrorContext.class);
        InOrder order = inOrder(listener);
        order.verify(listener).onRequest(request.capture());
        order.verify(listener).onError(error.capture());
        assertThat(error.getValue().error()).isSameAs(failure);
        assertThat(error.getValue().query()).isSameAs(query);
        assertThat(error.getValue().contentRetriever()).isSameAs(retriever);
        assertThat(error.getValue().attributes()).isSameAs(request.getValue().attributes());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void should_report_synchronously_thrown_async_error() {
        RuntimeException failure = new RuntimeException("Retrieval failed");
        ContentRetriever delegate = new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                throw new AssertionError("Synchronous retrieval must not be called");
            }

            @Override
            public CompletableFuture<List<Content>> retrieveAsync(Query query) {
                throw failure;
            }
        };
        ContentRetrieverListener listener = mock(ContentRetrieverListener.class);

        CompletableFuture<List<Content>> result = delegate.addListener(listener).retrieveAsync(Query.from("query"));

        assertThatThrownBy(() -> result.get(1, SECONDS)).cause().isSameAs(failure);
        verify(listener).onRequest(any());
        ArgumentCaptor<ContentRetrieverErrorContext> error =
                ArgumentCaptor.forClass(ContentRetrieverErrorContext.class);
        verify(listener).onError(error.capture());
        assertThat(error.getValue().error()).isSameAs(failure);
        verifyNoMoreInteractions(listener);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_preserve_cancellation(boolean cancelResult) {
        CompletableFuture<List<Content>> source = new CompletableFuture<>();
        ContentRetrieverListener listener = mock(ContentRetrieverListener.class);
        CompletableFuture<List<Content>> result =
                asyncRetriever(source).addListener(listener).retrieveAsync(Query.from("query"));

        if (cancelResult) {
            result.cancel(true);
            assertThat(result).isCancelled();
        } else {
            source.cancel(true);
            assertThatThrownBy(result::join).hasCauseInstanceOf(CancellationException.class);
        }

        assertThat(source).isCancelled();
        assertThat(result).isCompletedExceptionally();
        verify(listener).onRequest(any());
        verifyNoMoreInteractions(listener);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void should_ignore_listener_exceptions(boolean succeed) throws Exception {
        CompletableFuture<List<Content>> source = new CompletableFuture<>();
        ContentRetrieverListener failing = mock(ContentRetrieverListener.class);
        doThrow(new RuntimeException("Listener failed")).when(failing).onRequest(any());
        doThrow(new RuntimeException("Listener failed")).when(failing).onResponse(any());
        doThrow(new RuntimeException("Listener failed")).when(failing).onError(any());
        ContentRetrieverListener successful = mock(ContentRetrieverListener.class);
        CompletableFuture<List<Content>> result = asyncRetriever(source)
                .addListeners(List.of(failing, successful))
                .retrieveAsync(Query.from("query"));

        List<Content> contents = List.of(Content.from("content"));
        RuntimeException failure = new RuntimeException("Retrieval failed");
        if (succeed) {
            source.complete(contents);
            assertThat(result.get(1, SECONDS)).isSameAs(contents);
        } else {
            source.completeExceptionally(failure);
            assertThatThrownBy(() -> result.get(1, SECONDS)).cause().isSameAs(failure);
        }

        InOrder order = inOrder(failing, successful);
        order.verify(failing).onRequest(any());
        order.verify(successful).onRequest(any());
        if (succeed) {
            order.verify(failing).onResponse(any());
            order.verify(successful).onResponse(any());
        } else {
            order.verify(failing).onError(any());
            order.verify(successful).onError(any());
        }
        verifyNoMoreInteractions(failing, successful);
    }

    @Test
    void should_preserve_unsupported_async_default_without_calling_sync_retrieval() {
        ContentRetriever delegate = query -> {
            throw new AssertionError("Synchronous retrieval must not be called");
        };
        ContentRetriever retriever = delegate.addListener(mock(ContentRetrieverListener.class));

        CompletableFuture<List<Content>> result = retriever.retrieveAsync(Query.from("query"));

        assertThatThrownBy(() -> result.get(1, SECONDS)).hasCauseInstanceOf(AsyncNotSupportedException.class);
    }

    private static ContentRetriever asyncRetriever(CompletableFuture<List<Content>> source) {
        return new ContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                throw new AssertionError("Synchronous retrieval must not be called");
            }

            @Override
            public CompletableFuture<List<Content>> retrieveAsync(Query query) {
                return source;
            }
        };
    }
}
