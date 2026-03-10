package dev.langchain4j.model.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.MapEntry.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class EmbeddingModelListenerTest {

    static class TestEmbeddingModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return Response.from(List.of(Embedding.from(List.of(1f, 2f, 3f))));
        }
    }

    static class SuccessfulListener implements EmbeddingModelListener {

        @Override
        public void onRequest(EmbeddingModelRequestContext requestContext) {}

        @Override
        public void onResponse(EmbeddingModelResponseContext responseContext) {}

        @Override
        public void onError(EmbeddingModelErrorContext errorContext) {}
    }

    static class FailingListener implements EmbeddingModelListener {

        @Override
        public void onRequest(EmbeddingModelRequestContext requestContext) {
            throw new RuntimeException("something went wrong in onRequest()");
        }

        @Override
        public void onResponse(EmbeddingModelResponseContext responseContext) {
            throw new RuntimeException("something went wrong in onResponse()");
        }

        @Override
        public void onError(EmbeddingModelErrorContext errorContext) {
            throw new RuntimeException("something went wrong in onError()");
        }
    }

    @Test
    void should_call_listeners_in_order_of_declaration() {
        // given
        EmbeddingModelListener listener1 = spy(new SuccessfulListener());
        EmbeddingModelListener listener2 = spy(new SuccessfulListener());
        EmbeddingModel model = new TestEmbeddingModel().addListeners(List.of(listener1, listener2));

        // when
        model.embedAll(List.of(TextSegment.from("hi")));

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
    void should_observe_embed_textSegment_even_if_delegate_overrides_it() {
        // given
        EmbeddingModelListener listener = spy(new SuccessfulListener());
        EmbeddingModel delegate = new TestEmbeddingModel() {
            @Override
            public Response<Embedding> embed(TextSegment textSegment) {
                return Response.from(Embedding.from(List.of(9f)));
            }

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                return Response.from(List.of(Embedding.from(List.of(1f))));
            }
        };
        EmbeddingModel model = delegate.addListener(listener);

        // when
        Response<Embedding> response = model.embed(TextSegment.from("hi"));

        // then - make sure we delegated to embed(TextSegment), not embedAll(...)
        assertThat(response.content().vector()).containsExactly(9f);
        verify(listener).onRequest(any());
        verify(listener).onResponse(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void should_observe_embed_string_even_if_delegate_overrides_it() {
        // given
        EmbeddingModelListener listener = spy(new SuccessfulListener());
        EmbeddingModel delegate = new TestEmbeddingModel() {
            @Override
            public Response<Embedding> embed(String text) {
                return Response.from(Embedding.from(List.of(7f)));
            }

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                return Response.from(List.of(Embedding.from(List.of(1f))));
            }
        };
        EmbeddingModel model = delegate.addListener(listener);

        // when
        Response<Embedding> response = model.embed("hi");

        // then - make sure we delegated to embed(String), not embedAll(...)
        assertThat(response.content().vector()).containsExactly(7f);
        verify(listener).onRequest(any());
        verify(listener).onResponse(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void should_ignore_exceptions_thrown_by_listeners() {
        // given
        EmbeddingModelListener failingListener = spy(new FailingListener());
        EmbeddingModelListener successfulListener = spy(new SuccessfulListener());
        EmbeddingModel model = new TestEmbeddingModel().addListeners(List.of(failingListener, successfulListener));

        // when - then
        assertThatNoException().isThrownBy(() -> model.embedAll(List.of(TextSegment.from("hi"))));

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
        EmbeddingModelListener listener1 = spy(new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext requestContext) {
                requestContext.attributes().put("my-attribute", "my-value");
            }
        });
        EmbeddingModelListener listener2 = spy(new EmbeddingModelListener() {
            @Override
            public void onResponse(EmbeddingModelResponseContext responseContext) {
                assertThat(responseContext.attributes()).containsExactly(entry("my-attribute", "my-value"));
            }
        });
        EmbeddingModel model = new TestEmbeddingModel().addListeners(List.of(listener1, listener2));

        // when
        model.embedAll(List.of(TextSegment.from("hi")));

        // then
        verify(listener2).onResponse(any());
    }

    @Test
    void should_call_onError_when_delegate_throws_exception() {
        // given
        EmbeddingModelListener listener = spy(new SuccessfulListener());
        EmbeddingModel failingModel = new TestEmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                throw new RuntimeException("Embedding model failed");
            }
        };
        EmbeddingModel model = failingModel.addListener(listener);

        // when
        assertThatThrownBy(() -> model.embedAll(List.of(TextSegment.from("hi"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Embedding model failed");

        // then
        verify(listener).onRequest(any());
        verify(listener).onError(any());
        verifyNoMoreInteractions(listener);
    }

    @Test
    void should_handle_empty_listeners_list() {
        // given
        EmbeddingModel model = new TestEmbeddingModel().addListeners(List.of());

        // when/then
        assertThatNoException().isThrownBy(() -> model.embedAll(List.of(TextSegment.from("hi"))));
    }

    @Test
    void should_handle_null_listeners_list() {
        // given
        EmbeddingModel model = new TestEmbeddingModel().addListeners(null);

        // when/then
        assertThatNoException().isThrownBy(() -> model.embedAll(List.of(TextSegment.from("hi"))));
    }

    @Test
    void should_continue_calling_listeners_even_when_some_fail_in_onError() {
        // given
        EmbeddingModelListener failingListener = spy(new FailingListener());
        EmbeddingModelListener successfulListener = spy(new SuccessfulListener());
        EmbeddingModel failingModel = new TestEmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                throw new RuntimeException("Embedding model failed");
            }
        };
        EmbeddingModel model = failingModel.addListeners(List.of(failingListener, successfulListener));

        // when
        assertThatThrownBy(() -> model.embedAll(List.of(TextSegment.from("hi"))))
                .hasMessage("Embedding model failed");

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
        EmbeddingModelListener listener1 = spy(new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext requestContext) {
                requestContext.attributes().put("test-key", "test-value");
            }
        });
        EmbeddingModelListener listener2 = spy(new EmbeddingModelListener() {
            @Override
            public void onError(EmbeddingModelErrorContext errorContext) {
                assertThat(errorContext.attributes()).containsExactly(entry("test-key", "test-value"));
            }
        });
        EmbeddingModel failingModel = new TestEmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                throw new RuntimeException("Test error");
            }
        };
        EmbeddingModel model = failingModel.addListeners(List.of(listener1, listener2));

        // when
        assertThatThrownBy(() -> model.embedAll(List.of(TextSegment.from("hi"))))
                .hasMessage("Test error");

        // then
        verify(listener2).onError(any());
    }
}
