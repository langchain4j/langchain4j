package dev.langchain4j.model.embedding.common;

import static dev.langchain4j.internal.Utils.readBytes;
import static dev.langchain4j.model.embedding.request.EmbeddingInputType.DOCUMENT;
import static dev.langchain4j.model.embedding.request.EmbeddingInputType.QUERY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.listener.EmbeddingModelErrorContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelListener;
import dev.langchain4j.model.embedding.listener.EmbeddingModelRequestContext;
import dev.langchain4j.model.embedding.listener.EmbeddingModelResponseContext;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.output.Response;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Common tests that every {@link EmbeddingModel} implementing the request/response API
 * ({@link EmbeddingModel#embed(EmbeddingRequest)}) must pass, ensuring implementations behave identically.
 * <p>
 * It primarily exercises the new {@link EmbeddingRequest}/{@link EmbeddingResponse} API, but also verifies the
 * legacy convenience methods ({@link EmbeddingModel#embed(String)}, {@link EmbeddingModel#embed(TextSegment)},
 * {@link EmbeddingModel#embedAll(List)}), the per-parameter / per-modality fail-fast behavior, and listeners.
 * <p>
 * Optional features are gated by {@code supports*()} methods; override them (and the corresponding helpers) to
 * describe the model under test.
 * <p>
 * Make sure the {@code langchain4j-core} test-jar is on the test classpath of the module where this class is
 * extended:
 * <pre>{@code
 * <dependency>
 *     <groupId>dev.langchain4j</groupId>
 *     <artifactId>langchain4j-core</artifactId>
 *     <classifier>tests</classifier>
 *     <type>test-jar</type>
 *     <scope>test</scope>
 * </dependency>
 * }</pre>
 */
@TestInstance(PER_CLASS)
public abstract class AbstractEmbeddingModelIT {

    // Same image the chat-model ITs use; it is a PNG (matching the mime type below) and reliably fetchable.
    protected static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/e/e9/Felis_silvestris_silvestris_small_gradual_decrease_of_quality.png";

    // ===================== model provisioning =====================

    /** The model(s) under test. Every model in the list must share the {@code supports*()} capabilities. */
    protected abstract List<EmbeddingModel> models();

    /**
     * A model configured to notify the given listener (built with the builder's {@code listeners(...)} method,
     * mirroring {@link dev.langchain4j.model.chat.ChatModel}).
     */
    protected abstract EmbeddingModel modelWith(EmbeddingModelListener listener);

    /**
     * A model that will fail when embedding (e.g., an invalid API key/base URL), configured with the listener.
     * Return {@code null} to skip the {@code onError} listener test.
     */
    protected EmbeddingModel failingModelWith(EmbeddingModelListener listener) {
        return null;
    }

    // ===================== feature gates =====================
    // Defaults are opt-out: every capability is assumed supported, so a new implementation must actively
    // override a gate to false for what it does not support. This nudges new integrations to support as many
    // parameters and modalities as possible, and makes a forgotten capability fail loudly rather than silently.

    protected boolean supportsInputTypeParameter() {
        return true;
    }

    protected boolean supportsDimensionsParameter() {
        return true;
    }

    protected boolean supportsImageInput() {
        return true;
    }

    /** Whether text and image parts can be fused into a single embedding (interleaved input). */
    protected boolean supportsInterleavedInput() {
        return supportsImageInput();
    }

    // ===================== assertion gates =====================

    protected boolean assertModelName() {
        return true;
    }

    protected boolean assertTokenUsage() {
        return true;
    }

    // ===================== helpers =====================

    /** Dimensions value to request when {@link #supportsDimensionsParameter()} is true. */
    protected int dimensionsParameter() {
        return 256;
    }

    protected ImageContent catImage() {
        String base64 = Base64.getEncoder().encodeToString(readBytes(CAT_IMAGE_URL));
        return ImageContent.from(base64, "image/png");
    }

    private static Embedding single(EmbeddingResponse response) {
        assertThat(response.embeddings()).hasSize(1);
        return response.embeddings().get(0);
    }

    // ===================== new API: embed(EmbeddingRequest) =====================

    @ParameterizedTest
    @MethodSource("models")
    protected void should_embed_single_input(EmbeddingModel model) {

        EmbeddingResponse response =
                model.embed(EmbeddingRequest.builder().input("hello world").build());

        Embedding embedding = single(response);
        assertThat(embedding.vector()).isNotEmpty();
        assertThat(embedding.dimension()).isEqualTo(model.dimension());

        if (assertModelName()) {
            assertThat(response.metadata().modelName()).isNotBlank();
        }
        if (assertTokenUsage()) {
            assertThat(response.metadata().tokenUsage()).isNotNull();
            assertThat(response.metadata().tokenUsage().inputTokenCount()).isPositive();
        }
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_embed_batch_of_inputs_in_order(EmbeddingModel model) {

        EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                .inputs("the sky is blue", "grass is green")
                .build());

        assertThat(response.embeddings()).hasSize(2);
        // distinct texts produce distinct vectors, and the order matches the inputs
        assertThat(response.embeddings().get(0).vector())
                .isNotEqualTo(response.embeddings().get(1).vector());
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsInputTypeParameter")
    protected void should_embed_query_and_document_differently(EmbeddingModel model) {

        String text = "Paris is the capital of France";

        Embedding asQuery = single(model.embed(
                EmbeddingRequest.builder().input(text).inputType(QUERY).build()));
        Embedding asDocument = single(model.embed(
                EmbeddingRequest.builder().input(text).inputType(DOCUMENT).build()));

        assertThat(asQuery.dimension()).isEqualTo(asDocument.dimension());
        // the same text embedded as a query vs a document should differ
        assertThat(asQuery.vector()).isNotEqualTo(asDocument.vector());
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsDimensionsParameter")
    protected void should_respect_dimensions_parameter(EmbeddingModel model) {

        int dimensions = dimensionsParameter();

        Embedding embedding = single(model.embed(EmbeddingRequest.builder()
                .input("hello world")
                .dimensions(dimensions)
                .build()));

        assertThat(embedding.vector()).hasSize(dimensions);
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsImageInput")
    protected void should_embed_image(EmbeddingModel model) {

        Embedding embedding =
                single(model.embed(EmbeddingRequest.builder().input(catImage()).build()));

        assertThat(embedding.vector()).isNotEmpty();
        assertThat(embedding.dimension()).isEqualTo(model.dimension());
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsInterleavedInput")
    protected void should_embed_interleaved_text_and_image(EmbeddingModel model) {

        Embedding embedding = single(model.embed(EmbeddingRequest.builder()
                .input(TextContent.from("a photo of a cat: "), catImage())
                .build()));

        assertThat(embedding.vector()).isNotEmpty();
    }

    // ===================== convenience methods (must stay consistent) =====================

    @ParameterizedTest
    @MethodSource("models")
    protected void should_embed_via_convenience_string(EmbeddingModel model) {

        Response<Embedding> response = model.embed("hello world");

        assertThat(response.content().vector()).isNotEmpty();
        assertThat(response.content().dimension()).isEqualTo(model.dimension());
        assertThat(response.finishReason()).isNull();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_embed_via_convenience_text_segment(EmbeddingModel model) {

        Response<Embedding> response = model.embed(TextSegment.from("hello world"));

        assertThat(response.content().vector()).isNotEmpty();
        assertThat(response.content().dimension()).isEqualTo(model.dimension());
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_embed_all_text_segments(EmbeddingModel model) {

        Response<List<Embedding>> response =
                model.embedAll(List.of(TextSegment.from("first"), TextSegment.from("second")));

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).dimension()).isEqualTo(model.dimension());
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_report_positive_dimension(EmbeddingModel model) {
        assertThat(model.dimension()).isPositive();
    }

    // ===================== error scenarios (fail-fast, default-deny) =====================

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsInputTypeParameter")
    protected void should_fail_when_input_type_is_not_supported(EmbeddingModel model) {
        assertThatThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input("hello")
                        .inputType(QUERY)
                        .build()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class);
    }

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsDimensionsParameter")
    protected void should_fail_when_dimensions_is_not_supported(EmbeddingModel model) {
        assertThatThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input("hello")
                        .dimensions(256)
                        .build()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class);
    }

    @ParameterizedTest
    @MethodSource("models")
    @DisabledIf("supportsImageInput")
    protected void should_fail_when_image_input_is_not_supported(EmbeddingModel model) {
        assertThatThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(ImageContent.from(CAT_IMAGE_URL))
                        .build()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("IMAGE");
    }

    // ===================== listeners =====================

    @Test
    protected void should_notify_listener_on_request_and_response() {

        AtomicReference<EmbeddingModelRequestContext> requestContext = new AtomicReference<>();
        AtomicReference<EmbeddingModelResponseContext> responseContext = new AtomicReference<>();
        AtomicInteger onRequest = new AtomicInteger();
        AtomicInteger onResponse = new AtomicInteger();

        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext ctx) {
                requestContext.set(ctx);
                onRequest.incrementAndGet();
                ctx.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(EmbeddingModelResponseContext ctx) {
                responseContext.set(ctx);
                onResponse.incrementAndGet();
                assertThat(ctx.attributes()).containsEntry("id", "12345");
            }

            @Override
            public void onError(EmbeddingModelErrorContext ctx) {
                fail("onError() must not be called. Exception: " + ctx.error().getMessage());
            }
        };

        EmbeddingModel model = modelWith(listener);

        model.embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(onRequest).hasValue(1); // fired exactly once, no double-firing
        assertThat(onResponse).hasValue(1);
        assertThat(requestContext.get().textSegments())
                .extracting(TextSegment::text)
                .containsExactly("hello");
        assertThat(requestContext.get().modelProvider()).isEqualTo(model.provider());
        // listeners can inspect the full request (per-call parameters + multimodal inputs)
        assertThat(requestContext.get().embeddingRequest()).isNotNull();
        assertThat(requestContext.get().embeddingRequest().inputs()).hasSize(1);
        assertThat(responseContext.get().response().content()).hasSize(1);
        assertThat(responseContext.get().embeddingResponse().embeddings()).hasSize(1);
        // request and response share the same attributes instance
        assertThat(responseContext.get().attributes())
                .isSameAs(requestContext.get().attributes());
    }

    @Test
    protected void should_notify_listener_on_error() {

        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicInteger onError = new AtomicInteger();

        EmbeddingModelListener listener = new EmbeddingModelListener() {
            @Override
            public void onRequest(EmbeddingModelRequestContext ctx) {
                ctx.attributes().put("id", "12345");
            }

            @Override
            public void onResponse(EmbeddingModelResponseContext ctx) {
                fail("onResponse() must not be called");
            }

            @Override
            public void onError(EmbeddingModelErrorContext ctx) {
                error.set(ctx.error());
                onError.incrementAndGet();
                assertThat(ctx.attributes()).containsEntry("id", "12345");
            }
        };

        EmbeddingModel model = failingModelWith(listener);
        assumeTrue(model != null, "failingModelWith(...) not provided; skipping onError listener test");

        assertThatThrownBy(() ->
                        model.embed(EmbeddingRequest.builder().input("hello").build()))
                .isInstanceOf(Exception.class);

        assertThat(onError).hasValue(1);
        assertThat(error.get()).isNotNull();
    }
}
