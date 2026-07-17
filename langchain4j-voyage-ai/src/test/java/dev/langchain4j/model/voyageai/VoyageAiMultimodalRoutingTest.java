package dev.langchain4j.model.voyageai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.MockHttpClient;
import dev.langchain4j.http.client.MockHttpClientBuilder;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.junit.jupiter.api.Test;

class VoyageAiMultimodalRoutingTest {

    private static final String RESPONSE =
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[0.1,0.2,0.3],\"index\":0}],"
                    + "\"model\":\"voyage-multimodal-3.5\",\"usage\":{\"total_tokens\":7}}";

    private static MockHttpClient respondingMock() {
        return new MockHttpClient(
                SuccessfulHttpResponse.builder().statusCode(200).body(RESPONSE).build());
    }

    private static VoyageAiEmbeddingModel model(MockHttpClient mock) {
        return model(mock, "voyage-multimodal-3.5");
    }

    private static VoyageAiEmbeddingModel model(MockHttpClient mock, String modelName) {
        return VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(mock))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName(modelName)
                .maxRetries(0)
                .build();
    }

    @Test
    void auto_detects_multimodal_model_from_name_without_manual_flag() {
        // no .multimodal(...) set anywhere — detection is automatic from the model name
        assertThat(model(respondingMock(), "voyage-multimodal-3.5").supportedContentTypes())
                .containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
        assertThat(model(respondingMock(), "voyage-multimodal-3").supportedContentTypes())
                .containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
    }

    @Test
    void auto_detects_text_model_and_routes_to_text_endpoint() {
        MockHttpClient mock = respondingMock();
        VoyageAiEmbeddingModel model = model(mock, "voyage-3");

        assertThat(model.supportedContentTypes()).containsExactly(ContentType.TEXT);

        model.embed(EmbeddingRequest.builder().input("hello").build());
        assertThat(mock.request().url()).endsWith("/embeddings");
        assertThat(mock.request().url()).doesNotContain("multimodalembeddings");
    }

    @Test
    void auto_detected_text_model_rejects_image_fail_fast() {
        VoyageAiEmbeddingModel model = model(new MockHttpClient(), "voyage-3");

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(ImageContent.from("https://example.com/cat.png"))
                        .build()))
                .withMessageContaining("IMAGE");
    }

    @Test
    void does_not_falsely_detect_multilingual_as_multimodal() {
        assertThat(model(respondingMock(), "voyage-multilingual-2").supportedContentTypes())
                .containsExactly(ContentType.TEXT);
    }

    @Test
    void explicit_flag_overrides_auto_detection() {
        VoyageAiEmbeddingModel forced = VoyageAiEmbeddingModel.builder()
                .httpClientBuilder(new MockHttpClientBuilder(respondingMock()))
                .baseUrl("https://api.voyageai.com/v1")
                .apiKey("test-api-key")
                .modelName("some-custom-proxy-name")
                .multimodal(true)
                .maxRetries(0)
                .build();

        assertThat(forced.supportedContentTypes()).containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
    }

    @Test
    void sends_interleaved_text_and_image_to_multimodal_endpoint() {
        MockHttpClient mock = respondingMock();
        VoyageAiEmbeddingModel model = model(mock);

        EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                .input(TextContent.from("a photo of a cat"), ImageContent.from("https://example.com/cat.png"))
                .inputType(EmbeddingInputType.QUERY)
                .build());

        // routed to the multimodal endpoint
        assertThat(mock.request().url()).endsWith("/multimodalembeddings");

        String body = mock.request().body();
        assertThat(body).contains("\"text\"").contains("a photo of a cat");
        assertThat(body).contains("image_url").contains("https://example.com/cat.png");
        assertThat(body).contains("input_type").contains("\"query\"");
        assertThat(body).contains("voyage-multimodal-3.5");

        // response parsed
        assertThat(response.embeddings()).hasSize(1);
        assertThat(response.embeddings().get(0).vector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(response.metadata().tokenUsage().totalTokenCount()).isEqualTo(7);
    }

    @Test
    void sends_base64_image_as_data_url() {
        MockHttpClient mock = respondingMock();
        VoyageAiEmbeddingModel model = model(mock);

        model.embed(EmbeddingRequest.builder()
                .input(ImageContent.from("aGVsbG8=", "image/png"))
                .build());

        String body = mock.request().body();
        assertThat(body).contains("image_base64");
        assertThat(body).contains("data:image/png;base64,aGVsbG8=");
    }

    @Test
    void supports_text_and_image_content_only() {
        VoyageAiEmbeddingModel model = model(respondingMock());
        assertThat(model.supportedContentTypes()).containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
        assertThat(model.supportedParameters()).containsExactly(EmbeddingRequestParameters.INPUT_TYPE);
    }

    @Test
    void rejects_video_content_fail_fast() {
        VoyageAiEmbeddingModel model = model(new MockHttpClient());

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(VideoContent.from("https://example.com/clip.mp4"))
                        .build()))
                .withMessageContaining("VIDEO");
    }
}
