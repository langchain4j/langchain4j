package dev.langchain4j.model.cohere;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.VideoContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.request.EmbeddingInput;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.request.EmbeddingRequestParameters;
import java.util.List;
import org.junit.jupiter.api.Test;

class CohereEmbeddingModelV2Test {

    // Mirrors the (de)serialization config used by CohereClient.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private static CohereEmbeddingModel model() {
        return CohereEmbeddingModel.builder()
                .apiKey("test-api-key")
                .modelName("embed-v4.0")
                .build();
    }

    @Test
    void maps_input_type_to_cohere_vocabulary() {
        assertThat(CohereEmbeddingModel.toCohereInputType(EmbeddingInputType.QUERY))
                .isEqualTo("search_query");
        assertThat(CohereEmbeddingModel.toCohereInputType(EmbeddingInputType.DOCUMENT))
                .isEqualTo("search_document");
        assertThat(CohereEmbeddingModel.toCohereInputType(null)).isNull();
    }

    @Test
    void builds_v2_request_with_interleaved_text_and_image() throws Exception {
        CohereEmbeddingModel model = model();

        EmbeddingInput input = EmbeddingInput.from(
                TextContent.from("a photo of a cat"), ImageContent.from("https://example.com/cat.png"));

        EmbedV2Request request = model.buildV2Request(List.of(input), "search_query");
        String json = MAPPER.writeValueAsString(request);

        assertThat(json).contains("\"model\":\"embed-v4.0\"");
        assertThat(json).contains("\"input_type\":\"search_query\"");
        assertThat(json).contains("\"embedding_types\":[\"float\"]");
        assertThat(json).contains("\"type\":\"text\"").contains("a photo of a cat");
        // image_url must be a nested object with a "url" field
        assertThat(json).contains("\"type\":\"image_url\"");
        assertThat(json).contains("\"image_url\":{\"url\":\"https://example.com/cat.png\"}");
    }

    @Test
    void builds_base64_image_as_data_url() throws Exception {
        EmbeddingInput input = EmbeddingInput.from(ImageContent.from("aGVsbG8=", "image/png"));

        EmbedV2Request request = model().buildV2Request(List.of(input), "search_document");
        String json = MAPPER.writeValueAsString(request);

        assertThat(json).contains("\"image_url\":{\"url\":\"data:image/png;base64,aGVsbG8=\"}");
    }

    @Test
    void supports_text_and_image_content_only() {
        CohereEmbeddingModel model = model();
        assertThat(model.supportedContentTypes()).containsExactlyInAnyOrder(ContentType.TEXT, ContentType.IMAGE);
        assertThat(model.supportedParameters()).containsExactly(EmbeddingRequestParameters.INPUT_TYPE);
    }

    @Test
    void rejects_video_content_fail_fast() {
        CohereEmbeddingModel model = model();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> model.embed(EmbeddingRequest.builder()
                        .input(VideoContent.from("https://example.com/clip.mp4"))
                        .build()))
                .withMessageContaining("VIDEO");
    }
}
