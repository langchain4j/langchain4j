package dev.langchain4j.model.jina;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.jina.internal.api.JinaMultimodalEmbeddingRequest;
import org.junit.jupiter.api.Test;

class JinaMultimodalEmbeddingModelTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JinaEmbeddingModel model(String modelName) {
        return JinaEmbeddingModel.builder().apiKey("test-key").modelName(modelName).build();
    }

    private static JinaEmbeddingModel model(String modelName, Boolean lateChunking) {
        return JinaEmbeddingModel.builder()
                .apiKey("test-key")
                .modelName(modelName)
                .lateChunking(lateChunking)
                .build();
    }

    private static String multimodalRequestJson(JinaEmbeddingModel model) throws Exception {
        JinaMultimodalEmbeddingRequest request =
                model.buildMultimodalRequest(EmbeddingRequest.builder().input("a caption").build());
        return MAPPER.writeValueAsString(request);
    }

    @Test
    void multimodal_model_supports_text_and_image() {
        assertThat(model("jina-clip-v2").supportedContentTypes())
                .containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
        assertThat(model("jina-embeddings-v4").supportedContentTypes())
                .containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
    }

    @Test
    void text_model_supports_text_only_and_rejects_image() {
        JinaEmbeddingModel model = model("jina-embeddings-v3");
        assertThat(model.supportedContentTypes()).containsExactly(ContentType.TEXT);

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(ImageContent.from("https://example.com/cat.png"))
                        .build()))
                .withMessageContaining("IMAGE");
    }

    @Test
    void builds_multimodal_request_with_text_and_image_items() throws Exception {
        JinaEmbeddingModel model = model("jina-clip-v2");

        JinaMultimodalEmbeddingRequest request = model.buildMultimodalRequest(EmbeddingRequest.builder()
                .input("a caption")
                .input(ImageContent.from("https://example.com/cat.png"))
                .build());
        String json = MAPPER.writeValueAsString(request);

        // batch of two single-modality items (Jina embeds one modality per item)
        assertThat(json).contains("jina-clip-v2");
        assertThat(json).contains("\"text\":\"a caption\"");
        assertThat(json).contains("\"image\":\"https://example.com/cat.png\"");
    }

    @Test
    void builds_base64_image_as_data_url() throws Exception {
        JinaEmbeddingModel model = model("jina-clip-v2");

        JinaMultimodalEmbeddingRequest request = model.buildMultimodalRequest(EmbeddingRequest.builder()
                .input(ImageContent.from("aGVsbG8=", "image/png"))
                .build());
        String json = MAPPER.writeValueAsString(request);

        assertThat(json).contains("data:image/png;base64,aGVsbG8=");
    }

    @Test
    void sends_late_chunking_when_enabled_on_multimodal_model() throws Exception {
        String json = multimodalRequestJson(model("jina-embeddings-v4", true));

        assertThat(json).contains("\"late_chunking\":true");
    }

    @Test
    void sends_late_chunking_false_when_not_configured() throws Exception {
        String json = multimodalRequestJson(model("jina-embeddings-v4"));

        assertThat(json).contains("\"late_chunking\":false");
    }

    @Test
    void sends_late_chunking_for_clip_models_too() throws Exception {
        // the multimodal path does not filter by model name, matching the text path
        String json = multimodalRequestJson(model("jina-clip-v2", true));

        assertThat(json).contains("\"late_chunking\":true");
    }

    @Test
    void rejects_interleaved_text_and_image_in_a_single_input() {
        JinaEmbeddingModel model = model("jina-clip-v2");

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(TextContent.from("a photo of "), ImageContent.from("https://example.com/cat.png"))
                        .build()))
                .withMessageContaining("interleaved");
    }
}
