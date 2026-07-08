package dev.langchain4j.model.googleai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the common {@link EmbeddingInputType} is mapped to Google's {@code taskType} on the per-call
 * {@code embed(EmbeddingRequest)} path.
 */
class GoogleAiEmbeddingModelInputTypeTest {

    private static final String BATCH_EMBED_RESPONSE = "{\"embeddings\":[{\"values\":[0.1,0.2,0.3]}]}";

    private static MockHttpClient respondingMock() {
        return new MockHttpClient(SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(BATCH_EMBED_RESPONSE)
                .build());
    }

    private static GoogleAiEmbeddingModel model(MockHttpClient mock, GoogleAiEmbeddingModel.TaskType configuredTaskType) {
        return GoogleAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mock))
                .baseUrl("http://localhost")
                .apiKey("dummy")
                .modelName("text-embedding-004")
                .taskType(configuredTaskType)
                .maxRetries(0)
                .build();
    }

    @Test
    void query_input_type_maps_to_retrieval_query() {
        MockHttpClient mock = respondingMock();
        GoogleAiEmbeddingModel model = model(mock, null);

        EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                .input("hello")
                .inputType(EmbeddingInputType.QUERY)
                .build());

        assertThat(mock.request().body()).contains("RETRIEVAL_QUERY");
        assertThat(response.embeddings()).hasSize(1);
        assertThat(response.metadata().modelName()).isEqualTo("text-embedding-004");
    }

    @Test
    void document_input_type_maps_to_retrieval_document() {
        MockHttpClient mock = respondingMock();
        GoogleAiEmbeddingModel model = model(mock, null);

        model.embed(EmbeddingRequest.builder()
                .input("hello")
                .inputType(EmbeddingInputType.DOCUMENT)
                .build());

        assertThat(mock.request().body()).contains("RETRIEVAL_DOCUMENT");
    }

    @Test
    void no_input_type_falls_back_to_configured_task_type() {
        MockHttpClient mock = respondingMock();
        GoogleAiEmbeddingModel model = model(mock, GoogleAiEmbeddingModel.TaskType.SEMANTIC_SIMILARITY);

        model.embed(EmbeddingRequest.builder().input("hello").build());

        assertThat(mock.request().body()).contains("SEMANTIC_SIMILARITY");
    }

    @Test
    void supports_only_input_type_parameter() {
        GoogleAiEmbeddingModel model = model(respondingMock(), null);
        assertThat(model.supportedParameters()).containsExactly(EmbeddingRequestParameters.INPUT_TYPE);
    }

    @Test
    void rejects_unsupported_dimensions_parameter_before_any_call() {
        // no responding mock needed: validation happens before the HTTP call
        GoogleAiEmbeddingModel model = model(new MockHttpClient(), null);

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input("hello")
                        .dimensions(256)
                        .build()))
                .withMessageContaining("dimensions");
    }

    @Test
    void supports_only_text_content() {
        GoogleAiEmbeddingModel model = model(respondingMock(), null);
        assertThat(model.supportedContentTypes()).containsExactly(ContentType.TEXT);
    }

    @Test
    void rejects_image_content_fail_fast() {
        // gemini-embedding-001 is text-only; an image input must fail fast (before any HTTP call)
        GoogleAiEmbeddingModel model = model(new MockHttpClient(), null);

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(ImageContent.from("http://example.com/cat.png"))
                        .build()))
                .withMessageContaining("IMAGE");
    }

    // ---------- Gemini Embedding 2 (natively multimodal) ----------

    private static GoogleAiEmbeddingModel multimodalModel(MockHttpClient mock) {
        return GoogleAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mock))
                .baseUrl("http://localhost")
                .apiKey("dummy")
                .modelName("gemini-embedding-2-preview")
                .maxRetries(0)
                .build();
    }

    @Test
    void gemini_embedding_2_auto_detected_as_multimodal() {
        assertThat(multimodalModel(respondingMock()).supportedContentTypes())
                .containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
    }

    @Test
    void gemini_embedding_2_sends_interleaved_text_and_image_inline() {
        MockHttpClient mock = respondingMock();
        GoogleAiEmbeddingModel model = multimodalModel(mock);

        model.embed(EmbeddingRequest.builder()
                .input(TextContent.from("a photo of a cat"), ImageContent.from("aGVsbG8=", "image/png"))
                .build());

        String body = mock.request().body();
        assertThat(body).contains("a photo of a cat");
        assertThat(body).contains("inlineData");
        assertThat(body).contains("aGVsbG8=");
        assertThat(body).contains("image/png");
    }

    @Test
    void gemini_embedding_2_requires_base64_image() {
        GoogleAiEmbeddingModel model = multimodalModel(new MockHttpClient());

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(ImageContent.from("https://example.com/cat.png"))
                        .build()))
                .withMessageContaining("base64");
    }
}
