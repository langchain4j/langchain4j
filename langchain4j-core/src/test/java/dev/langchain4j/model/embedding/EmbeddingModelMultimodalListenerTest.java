package dev.langchain4j.model.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class EmbeddingModelMultimodalListenerTest {

    enum Mode {
        INLINE_SYNC,
        INLINE_ASYNC,
        WRAPPED_SYNC;

        EmbeddingResponse embed(EmbeddingModel model, EmbeddingRequest request) {
            return this == INLINE_ASYNC ? model.embedAsync(request).join() : model.embed(request);
        }
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_embed_image_only_input_with_listeners(Mode mode) {
        Fixture fixture = new Fixture(mode);
        EmbeddingRequest request = EmbeddingRequest.builder().input(image()).build();

        EmbeddingResponse response = mode.embed(fixture.model, request);

        assertThat(response.embeddings()).hasSize(1);
        fixture.assertSuccess(request, List.of());
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_preserve_the_full_mixed_batch_and_expose_its_text_to_listeners(Mode mode) {
        Fixture fixture = new Fixture(mode);
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(image())
                .input("caption")
                .input(image())
                .input(TextContent.from("before"), image(), TextContent.from("after"))
                .input(TextContent.from(" \t"), image())
                .build();

        EmbeddingResponse response = mode.embed(fixture.model, request);

        assertThat(response.embeddings()).hasSize(5);
        fixture.assertSuccess(request, List.of("caption", "before\nafter"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_preserve_interleaved_text_and_image_input(Mode mode) {
        Fixture fixture = new Fixture(mode);
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(TextContent.from("before"), image(), TextContent.from("after"))
                .build();

        assertThat(mode.embed(fixture.model, request).embeddings()).hasSize(1);

        fixture.assertSuccess(request, List.of("before\nafter"));
    }

    @ParameterizedTest
    @EnumSource(Mode.class)
    void should_report_the_provider_error_for_image_only_input(Mode mode) {
        Fixture fixture = new Fixture(mode);
        IllegalStateException failure = new IllegalStateException("provider failed");
        fixture.provider.failure = failure;
        EmbeddingRequest request = EmbeddingRequest.builder().input(image()).build();

        Throwable thrown = catchThrowable(() -> mode.embed(fixture.model, request));

        if (mode == Mode.INLINE_ASYNC) {
            assertThat(thrown).isInstanceOf(CompletionException.class).hasCause(failure);
        } else {
            assertThat(thrown).isSameAs(failure);
        }
        assertThat(fixture.provider.seenRequest.inputs()).isEqualTo(request.inputs());
        assertThat(fixture.listener.request).isNotNull();
        assertThat(fixture.listener.response).isNull();
        assertThat(fixture.listener.error.error()).isSameAs(failure);
        assertThat(fixture.listener.error.embeddingRequest().inputs()).isEqualTo(request.inputs());
        assertThat(fixture.listener.error.textSegments()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_embed_image_only_input_without_listeners(boolean async) {
        MultimodalModel model = new MultimodalModel();
        EmbeddingRequest request = EmbeddingRequest.builder().input(image()).build();

        EmbeddingResponse response = async ? model.embedAsync(request).join() : model.embed(request);

        assertThat(response.embeddings()).hasSize(1);
        assertThat(model.seenRequest.inputs()).isEqualTo(request.inputs());
    }

    private static ImageContent image() {
        // The stub never decodes the image or performs network I/O.
        return ImageContent.from("AA==", "image/png");
    }

    private static class Fixture {
        final RecordingListener listener = new RecordingListener();
        final MultimodalModel provider = new MultimodalModel();
        final EmbeddingModel model;

        Fixture(Mode mode) {
            if (mode == Mode.WRAPPED_SYNC) {
                model = provider.addListener(listener);
            } else {
                provider.listeners = List.of(listener);
                model = provider;
            }
        }

        void assertSuccess(EmbeddingRequest request, List<String> expectedTexts) {
            assertThat(provider.seenRequest.inputs()).isEqualTo(request.inputs());
            assertThat(listener.request.embeddingRequest().inputs()).isEqualTo(request.inputs());
            assertThat(listener.response.embeddingRequest().inputs()).isEqualTo(request.inputs());
            assertThat(listener.request.textSegments())
                    .extracting(TextSegment::text)
                    .isEqualTo(expectedTexts);
            assertThat(listener.response.textSegments())
                    .extracting(TextSegment::text)
                    .isEqualTo(expectedTexts);
            assertThat(listener.response.attributes()).isSameAs(listener.request.attributes());
            assertThat(listener.error).isNull();
        }
    }

    private static class MultimodalModel implements EmbeddingModel {
        List<EmbeddingModelListener> listeners = List.of();
        EmbeddingRequest seenRequest;
        RuntimeException failure;

        @Override
        public Set<ContentType> supportedContentTypes() {
            return Set.of(ContentType.TEXT, ContentType.IMAGE);
        }

        @Override
        public List<EmbeddingModelListener> listeners() {
            return listeners;
        }

        @Override
        public EmbeddingResponse doEmbed(EmbeddingRequest request) {
            seenRequest = request;
            if (failure != null) {
                throw failure;
            }
            return EmbeddingResponse.builder()
                    .embeddings(request.inputs().stream()
                            .map(input ->
                                    Embedding.from(new float[] {input.contents().size()}))
                            .toList())
                    .build();
        }

        @Override
        public CompletableFuture<EmbeddingResponse> doEmbedAsync(EmbeddingRequest request) {
            try {
                return CompletableFuture.completedFuture(doEmbed(request));
            } catch (RuntimeException error) {
                return CompletableFuture.failedFuture(error);
            }
        }
    }

    private static class RecordingListener implements EmbeddingModelListener {
        EmbeddingModelRequestContext request;
        EmbeddingModelResponseContext response;
        EmbeddingModelErrorContext error;

        @Override
        public void onRequest(EmbeddingModelRequestContext context) {
            request = context;
        }

        @Override
        public void onResponse(EmbeddingModelResponseContext context) {
            response = context;
        }

        @Override
        public void onError(EmbeddingModelErrorContext context) {
            error = context;
        }
    }
}
