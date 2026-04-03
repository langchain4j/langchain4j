package dev.langchain4j.rag.content.retriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverErrorContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverListener;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverRequestContext;
import dev.langchain4j.rag.content.retriever.listener.ContentRetrieverResponseContext;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ContentRetrieverListenerTest {

    static class TestContentRetriever implements ContentRetriever {
        @Override
        public List<Content> retrieve(Query query) {
            return List.of();
        }
    }

    static class SuccessfulListener implements ContentRetrieverListener {

        @Override
        public void onRequest(ContentRetrieverRequestContext requestContext) {}

        @Override
        public void onResponse(ContentRetrieverResponseContext responseContext) {}

        @Override
        public void onError(ContentRetrieverErrorContext errorContext) {}
    }

    static class FailingListener implements ContentRetrieverListener {

        @Override
        public void onRequest(ContentRetrieverRequestContext requestContext) {
            throw new RuntimeException("something went wrong in onRequest()");
        }

        @Override
        public void onResponse(ContentRetrieverResponseContext responseContext) {
            throw new RuntimeException("something went wrong in onResponse()");
        }

        @Override
        public void onError(ContentRetrieverErrorContext errorContext) {
            throw new RuntimeException("something went wrong in onError()");
        }
    }

    @Test
    void should_call_listeners_in_order_of_declaration() {
        // given
        ContentRetrieverListener listener1 = spy(new SuccessfulListener());
        ContentRetrieverListener listener2 = spy(new SuccessfulListener());
        ContentRetriever retriever = new TestContentRetriever().addListeners(List.of(listener1, listener2));

        // when
        retriever.retrieve(Query.from("q"));

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
        ContentRetrieverListener failingListener = spy(new FailingListener());
        ContentRetrieverListener successfulListener = spy(new SuccessfulListener());
        ContentRetriever retriever =
                new TestContentRetriever().addListeners(List.of(failingListener, successfulListener));

        // when - then
        assertThatNoException().isThrownBy(() -> retriever.retrieve(Query.from("q")));

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
        ContentRetrieverListener listener1 = spy(new ContentRetrieverListener() {
            @Override
            public void onRequest(ContentRetrieverRequestContext requestContext) {
                requestContext.attributes().put("my-attribute", "my-value");
            }
        });
        ContentRetrieverListener listener2 = spy(new ContentRetrieverListener() {
            @Override
            public void onResponse(ContentRetrieverResponseContext responseContext) {
                assertThat(responseContext.attributes()).containsExactly(entry("my-attribute", "my-value"));
            }
        });
        ContentRetriever retriever = new TestContentRetriever().addListeners(List.of(listener1, listener2));

        // when
        retriever.retrieve(Query.from("q"));

        // then
        verify(listener2).onResponse(any());
    }

    @Test
    void should_call_onError_when_delegate_throws_exception() {
        // given
        ContentRetrieverListener listener = spy(new SuccessfulListener());
        ContentRetriever failingRetriever = new TestContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                throw new RuntimeException("Retriever failed");
            }
        };
        ContentRetriever retriever = failingRetriever.addListener(listener);

        // when
        assertThatThrownBy(() -> retriever.retrieve(Query.from("q")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Retriever failed");

        // then
        verify(listener).onRequest(any());
        verify(listener).onError(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void should_handle_empty_listeners_list() {
        // given
        ContentRetriever retriever = new TestContentRetriever().addListeners(List.of());

        // when/then
        assertThatNoException().isThrownBy(() -> retriever.retrieve(Query.from("q")));
    }

    @Test
    void should_handle_null_listeners_list() {
        // given
        ContentRetriever retriever = new TestContentRetriever().addListeners(null);

        // when/then
        assertThatNoException().isThrownBy(() -> retriever.retrieve(Query.from("q")));
    }

    @Test
    void should_continue_calling_listeners_even_when_some_fail_in_onError() {
        // given
        ContentRetrieverListener failingListener = spy(new FailingListener());
        ContentRetrieverListener successfulListener = spy(new SuccessfulListener());
        ContentRetriever failingRetriever = new TestContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                throw new RuntimeException("Retriever failed");
            }
        };
        ContentRetriever retriever = failingRetriever.addListeners(List.of(failingListener, successfulListener));

        // when
        assertThatThrownBy(() -> retriever.retrieve(Query.from("q"))).hasMessage("Retriever failed");

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
        ContentRetrieverListener listener1 = spy(new ContentRetrieverListener() {
            @Override
            public void onRequest(ContentRetrieverRequestContext requestContext) {
                requestContext.attributes().put("test-key", "test-value");
            }
        });
        ContentRetrieverListener listener2 = spy(new ContentRetrieverListener() {
            @Override
            public void onError(ContentRetrieverErrorContext errorContext) {
                assertThat(errorContext.attributes()).containsExactly(entry("test-key", "test-value"));
            }
        });
        ContentRetriever failingRetriever = new TestContentRetriever() {
            @Override
            public List<Content> retrieve(Query query) {
                throw new RuntimeException("Test error");
            }
        };
        ContentRetriever retriever = failingRetriever.addListeners(List.of(listener1, listener2));

        // when
        assertThatThrownBy(() -> retriever.retrieve(Query.from("q"))).hasMessage("Test error");

        // then
        verify(listener2).onError(any());
    }
}
