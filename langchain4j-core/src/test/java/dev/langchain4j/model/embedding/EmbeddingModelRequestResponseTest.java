package dev.langchain4j.model.embedding;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.request.DefaultEmbeddingRequestParameters;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingParameter;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class EmbeddingModelRequestResponseTest implements WithAssertions {

    /** A legacy provider: only implements the abstract embedAll, opts into no per-call parameters. */
    static class LegacyModel implements EmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = textSegments.stream()
                    .map(ts -> new Embedding(new float[] {ts.text().length()}))
                    .collect(Collectors.toList());
            return Response.from(embeddings, new TokenUsage(7));
        }

        @Override
        public String modelName() {
            return "legacy";
        }
    }

    /** A provider that natively honors the DIMENSIONS parameter and nothing else. */
    static class DimensionsAwareModel implements EmbeddingModel {

        Integer lastSeenDimensions;

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            EmbeddingResponse response =
                    embed(EmbeddingRequest.builder().textSegments(textSegments).build());
            return Response.from(response.embeddings(), response.metadata().tokenUsage());
        }

        @Override
        public Set<EmbeddingParameter<?>> supportedParameters() {
            return Set.of(EmbeddingRequestParameters.DIMENSIONS);
        }

        @Override
        public EmbeddingResponse doEmbed(EmbeddingRequest request) {
            lastSeenDimensions = request.dimensions();
            List<Embedding> embeddings = request.inputs().stream()
                    .map(input -> new Embedding(new float[] {input.text().length()}))
                    .collect(Collectors.toList());
            return EmbeddingResponse.builder().embeddings(embeddings).build();
        }
    }

    /** A multimodal provider that natively accepts text and image content. */
    static class MultimodalModel implements EmbeddingModel {

        List<EmbeddingInput> lastSeenInputs;

        @Override
        public Set<ContentType> supportedContentTypes() {
            return Set.of(ContentType.TEXT, ContentType.IMAGE);
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            throw new UnsupportedOperationException("multimodal model implements doEmbed");
        }

        @Override
        public EmbeddingResponse doEmbed(EmbeddingRequest request) {
            lastSeenInputs = request.inputs();
            List<Embedding> embeddings = request.inputs().stream()
                    .map(input -> new Embedding(new float[] {input.contents().size()}))
                    .collect(Collectors.toList());
            return EmbeddingResponse.builder().embeddings(embeddings).build();
        }
    }

    /** A model that fires inline listeners (ChatModel style) via listeners(). */
    static class ListenableModel extends LegacyModel {

        private final List<EmbeddingModelListener> listeners;
        private final boolean fail;

        ListenableModel(List<EmbeddingModelListener> listeners, boolean fail) {
            this.listeners = listeners;
            this.fail = fail;
        }

        @Override
        public List<EmbeddingModelListener> listeners() {
            return listeners;
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            if (fail) {
                throw new RuntimeException("boom");
            }
            return super.embedAll(textSegments);
        }
    }

    @Test
    void inline_listeners_fire_on_request_and_response() {
        java.util.concurrent.atomic.AtomicInteger onRequest = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<dev.langchain4j.model.output.Response<List<Embedding>>> seen =
                new java.util.concurrent.atomic.AtomicReference<>();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext ctx) {
                onRequest.incrementAndGet();
                assertThat(ctx.textSegments()).extracting(TextSegment::text).containsExactly("hello");
                assertThat(ctx.modelProvider()).isEqualTo(dev.langchain4j.model.ModelProvider.OTHER);
                // listeners can see the full request (per-call parameters + multimodal inputs)
                assertThat(ctx.embeddingRequest()).isNotNull();
                assertThat(ctx.embeddingRequest().inputs())
                        .extracting(EmbeddingInput::text)
                        .containsExactly("hello");
            }

            @Override
            public void onResponse(dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext ctx) {
                seen.set(ctx.response());
                // the new request/response types are available on the context
                assertThat(ctx.embeddingRequest().inputs())
                        .extracting(EmbeddingInput::text)
                        .containsExactly("hello");
                assertThat(ctx.embeddingResponse().embeddings())
                        .isEqualTo(ctx.response().content());
            }
        };

        EmbeddingModel model = new ListenableModel(List.of(listener), false);
        EmbeddingResponse response =
                model.embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(onRequest).hasValue(1);
        assertThat(seen.get().content()).isEqualTo(response.embeddings());
        assertThat(seen.get().tokenUsage()).isEqualTo(new TokenUsage(7));
    }

    @Test
    void inline_listeners_fire_on_convenience_embed_string() {
        // the legacy convenience method now routes through embed(EmbeddingRequest), so listeners fire here too
        java.util.concurrent.atomic.AtomicInteger onRequest = new java.util.concurrent.atomic.AtomicInteger();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext ctx) {
                onRequest.incrementAndGet();
                assertThat(ctx.textSegments()).extracting(TextSegment::text).containsExactly("hello");
            }
        };

        EmbeddingModel model = new ListenableModel(List.of(listener), false);
        model.embed("hello");

        assertThat(onRequest).hasValue(1); // fires exactly once, no double-firing
    }

    @Test
    void inline_listeners_fire_on_error() {
        java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onError(dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext ctx) {
                error.set(ctx.error());
            }
        };

        EmbeddingModel model = new ListenableModel(List.of(listener), true);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() ->
                        model.embed(EmbeddingRequest.builder().input("hello").build()))
                .withMessage("boom");
        assertThat(error.get()).hasMessage("boom");
    }

    @Test
    void wrapper_listener_still_fires_once_without_double_firing() {
        java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext ctx) {
                count.incrementAndGet();
            }
        };

        // wrapper around a plain provider (no inline listeners()) -> fires exactly once
        EmbeddingModel model = new LegacyModel().addListener(listener);
        model.embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(count).hasValue(1);
    }

    @Test
    void legacy_model_supports_new_api_without_parameters() {
        EmbeddingModel model = new LegacyModel();

        EmbeddingResponse response =
                model.embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(response.embeddings()).hasSize(1);
        assertThat(response.embeddings().get(0).vector()).containsExactly(5f);
        assertThat(response.metadata().modelName()).isEqualTo("legacy");
        assertThat(response.metadata().tokenUsage()).isEqualTo(new TokenUsage(7));
    }

    @Test
    void batch_of_text_inputs_yields_one_embedding_each() {
        EmbeddingModel model = new LegacyModel();

        EmbeddingResponse response =
                model.embed(EmbeddingRequest.builder().inputs("a", "bb", "ccc").build());

        assertThat(response.embeddings()).hasSize(3);
        assertThat(response.embeddings().get(0).vector()).containsExactly(1f);
        assertThat(response.embeddings().get(1).vector()).containsExactly(2f);
        assertThat(response.embeddings().get(2).vector()).containsExactly(3f);
    }

    @Test
    void text_only_model_rejects_image_content_fail_fast() {
        EmbeddingModel model = new LegacyModel(); // supportedContentTypes defaults to {TEXT}

        Content image = ImageContent.from("http://example.com/cat.png");
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() ->
                        model.embed(EmbeddingRequest.builder().input(image).build()))
                .withMessageContaining("IMAGE");
    }

    @Test
    void multimodal_model_accepts_interleaved_text_and_image() {
        MultimodalModel model = new MultimodalModel();

        EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                .input(TextContent.from("a photo of a cat"), ImageContent.from("http://example.com/cat.png"))
                .input("just text")
                .build());

        // batch of 2 inputs -> 2 embeddings; first input fused 2 parts, second 1 part
        assertThat(response.embeddings()).hasSize(2);
        assertThat(model.lastSeenInputs.get(0).contentTypes())
                .containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
        assertThat(response.embeddings().get(0).vector()).containsExactly(2f);
        assertThat(response.embeddings().get(1).vector()).containsExactly(1f);
    }

    @Test
    void legacy_model_rejects_unsupported_parameter() {
        EmbeddingModel model = new LegacyModel();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input("hello")
                        .dimensions(256)
                        .build()))
                .withMessageContaining("dimensions");
    }

    @Test
    void model_honors_supported_parameter_but_rejects_unsupported_one() {
        DimensionsAwareModel model = new DimensionsAwareModel();

        // supported parameter flows through
        model.embed(EmbeddingRequest.builder().input("hello").dimensions(256).build());
        assertThat(model.lastSeenDimensions).isEqualTo(256);

        // a different, unsupported parameter (modelName) is rejected
        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(
                        EmbeddingRequest.builder().input("hello").modelName("x").build()))
                .withMessageContaining("modelName");
    }

    @Test
    void present_parameters_reflect_only_what_was_set() {
        EmbeddingRequestParameters params =
                DefaultEmbeddingRequestParameters.builder().dimensions(128).build();

        assertThat(params.presentParameters()).containsExactly(EmbeddingRequestParameters.DIMENSIONS);
        assertThat(params.dimensions()).isEqualTo(128);
        assertThat(params.modelName()).isNull();
    }

    @Test
    void override_with_merges_and_overrides() {
        EmbeddingRequestParameters defaults = DefaultEmbeddingRequestParameters.builder()
                .modelName("default-model")
                .dimensions(512)
                .build();
        EmbeddingRequestParameters perCall =
                DefaultEmbeddingRequestParameters.builder().dimensions(256).build();

        EmbeddingRequestParameters merged = defaults.overrideWith(perCall);

        assertThat(merged.modelName()).isEqualTo("default-model"); // preserved
        assertThat(merged.dimensions()).isEqualTo(256); // overridden
        assertThat(merged.presentParameters())
                .containsExactlyInAnyOrder(
                        EmbeddingRequestParameters.MODEL_NAME, EmbeddingRequestParameters.DIMENSIONS);
    }

    @Test
    void builder_accepts_text_segments_and_drops_metadata() {
        TextSegment segmentWithMetadata = TextSegment.from("bb", Metadata.from("title", "My Document"));

        EmbeddingRequest request = EmbeddingRequest.builder()
                .textSegment(TextSegment.from("a"))
                .textSegments(List.of(segmentWithMetadata, TextSegment.from("ccc")))
                .build();

        // only the text of each segment is carried into the request
        assertThat(request.inputs().stream().map(EmbeddingInput::text)).containsExactly("a", "bb", "ccc");

        // the segment's metadata (e.g. a document title) is dropped: the input built from a segment WITH
        // metadata equals the input built from a plain segment carrying only the same text, and the input
        // holds nothing but that text content.
        EmbeddingInput inputFromSegmentWithMetadata = request.inputs().get(1);
        assertThat(inputFromSegmentWithMetadata).isEqualTo(EmbeddingInput.from(TextSegment.from("bb")));
        assertThat(inputFromSegmentWithMetadata.contents()).containsExactly(TextContent.from("bb"));
    }

    /**
     * A provider that overrides both embed(TextSegment) and embedAll with dedicated API calls (like the Google
     * and Cohere models, whose overrides carry behavior the request/response API cannot). It keeps those paths
     * observable by wrapping the raw call with {@link EmbeddingModelListenerUtils#withListeners}.
     */
    static class CustomOverrideModel implements EmbeddingModel {

        private final List<EmbeddingModelListener> listeners;
        private final boolean fail;

        CustomOverrideModel(List<EmbeddingModelListener> listeners, boolean fail) {
            this.listeners = listeners;
            this.fail = fail;
        }

        @Override
        public List<EmbeddingModelListener> listeners() {
            return listeners;
        }

        @Override
        public Response<Embedding> embed(TextSegment textSegment) {
            return EmbeddingModelListenerUtils.withListeners(this, textSegment, () -> {
                if (fail) {
                    throw new RuntimeException("boom");
                }
                return Response.from(
                        new Embedding(new float[] {textSegment.text().length()}), new TokenUsage(3));
            });
        }

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            return EmbeddingModelListenerUtils.withListeners(this, textSegments, () -> {
                if (fail) {
                    throw new RuntimeException("boom");
                }
                List<Embedding> embeddings = textSegments.stream()
                        .map(ts -> new Embedding(new float[] {ts.text().length()}))
                        .collect(Collectors.toList());
                return Response.from(embeddings, new TokenUsage(5));
            });
        }
    }

    @Test
    void custom_embedAll_override_fires_listeners_once_via_util() {
        java.util.concurrent.atomic.AtomicInteger onRequest = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Response<List<Embedding>>> seen =
                new java.util.concurrent.atomic.AtomicReference<>();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext ctx) {
                onRequest.incrementAndGet();
                assertThat(ctx.textSegments()).extracting(TextSegment::text).containsExactly("a", "bb");
            }

            @Override
            public void onResponse(dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext ctx) {
                seen.set(ctx.response());
                assertThat(ctx.embeddingResponse().embeddings())
                        .isEqualTo(ctx.response().content());
            }
        };

        EmbeddingModel model = new CustomOverrideModel(List.of(listener), false);
        Response<List<Embedding>> response = model.embedAll(List.of(TextSegment.from("a"), TextSegment.from("bb")));

        assertThat(response.content()).hasSize(2);
        assertThat(onRequest).hasValue(1); // fires exactly once, no double-firing
        assertThat(seen.get().tokenUsage()).isEqualTo(new TokenUsage(5));
    }

    @Test
    void custom_embed_text_segment_override_fires_listeners_once_via_util() {
        java.util.concurrent.atomic.AtomicInteger onRequest = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger onResponse = new java.util.concurrent.atomic.AtomicInteger();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext ctx) {
                onRequest.incrementAndGet();
                assertThat(ctx.textSegments()).extracting(TextSegment::text).containsExactly("hello");
            }

            @Override
            public void onResponse(dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext ctx) {
                onResponse.incrementAndGet();
            }
        };

        EmbeddingModel model = new CustomOverrideModel(List.of(listener), false);

        // embed(String) -> embed(TextSegment) override -> util fires exactly once
        Response<Embedding> response = model.embed("hello");

        assertThat(response.content().vector()).containsExactly(5f);
        assertThat(onRequest).hasValue(1);
        assertThat(onResponse).hasValue(1);
    }

    @Test
    void custom_override_fires_on_error_via_util() {
        java.util.concurrent.atomic.AtomicReference<Throwable> error =
                new java.util.concurrent.atomic.AtomicReference<>();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onError(dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext ctx) {
                error.set(ctx.error());
            }
        };

        EmbeddingModel model = new CustomOverrideModel(List.of(listener), true);

        assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> model.embedAll(List.of(TextSegment.from("a"))))
                .withMessage("boom");
        assertThat(error.get()).hasMessage("boom");
    }

    @Test
    void custom_override_util_skips_dispatch_when_no_listeners() {
        // no listeners configured -> util runs the operation directly without building contexts
        EmbeddingModel model = new CustomOverrideModel(List.of(), false);

        Response<List<Embedding>> response = model.embedAll(List.of(TextSegment.from("a"), TextSegment.from("bb")));

        assertThat(response.content()).hasSize(2);
        assertThat(response.tokenUsage()).isEqualTo(new TokenUsage(5));
    }

    @Test
    void embedAll_fires_inline_listeners_when_model_implements_doEmbed() {
        java.util.concurrent.atomic.AtomicInteger onRequest = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger onResponse = new java.util.concurrent.atomic.AtomicInteger();
        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext ctx) {
                onRequest.incrementAndGet();
            }

            @Override
            public void onResponse(dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext ctx) {
                onResponse.incrementAndGet();
            }
        };

        // A model that implements only doEmbed + listeners() (not embedAll), so embedAll uses the default that
        // routes through embed(EmbeddingRequest) and therefore fires the inline listeners.
        EmbeddingModel model = new EmbeddingModel() {
            @Override
            public List<EmbeddingModelListener> listeners() {
                return List.of(listener);
            }

            @Override
            public EmbeddingResponse doEmbed(EmbeddingRequest request) {
                List<Embedding> embeddings = request.inputs().stream()
                        .map(input -> new Embedding(new float[] {1f}))
                        .collect(Collectors.toList());
                return EmbeddingResponse.builder().embeddings(embeddings).build();
            }
        };

        Response<List<Embedding>> response = model.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b")));

        assertThat(response.content()).hasSize(2);
        assertThat(onRequest.get()).isEqualTo(1);
        assertThat(onResponse.get()).isEqualTo(1);
    }
}
