package dev.langchain4j.model.vertexai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.ImageEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GCP_PROJECT_ID", matches = ".+")
class VertexAiMultimodalEmbeddingModelIT {

    private static final String PROJECT_ID = System.getenv("GCP_PROJECT_ID");
    private static final String LOCATION = System.getenv().getOrDefault("GCP_LOCATION", "us-central1");

    // A tiny 1x1 red PNG image encoded in base64 (avoids external HTTP calls)
    private static final String TEST_IMAGE_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

    @Test
    void should_embed_text_via_EmbeddingModel_interface() {
        // given
        EmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(256)
                .build();

        // when
        Response<Embedding> response = model.embed("a photo of a cat");

        // then
        assertThat(response.content().dimension()).isEqualTo(256);
    }

    @Test
    void should_embed_multiple_texts() {
        // given
        EmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(256)
                .build();

        // when
        Response<List<Embedding>> response = model.embedAll(List.of(
                dev.langchain4j.data.segment.TextSegment.from("hello"),
                dev.langchain4j.data.segment.TextSegment.from("world")));

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).dimension()).isEqualTo(256);
        assertThat(response.content().get(1).dimension()).isEqualTo(256);
    }

    @Test
    void should_embed_image_via_ImageEmbeddingModel_interface() {
        // given
        ImageEmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(256)
                .build();

        ImageContent imageContent = ImageContent.from(TEST_IMAGE_BASE64, "image/png");

        // when
        Response<Embedding> response = model.embed(imageContent);

        // then
        assertThat(response.content().dimension()).isEqualTo(256);
    }

    @Test
    void should_embed_multiple_images() {
        // given
        ImageEmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(256)
                .build();

        // when
        Response<List<Embedding>> response = model.embedAllImages(List.of(
                ImageContent.from(TEST_IMAGE_BASE64, "image/png"), ImageContent.from(TEST_IMAGE_BASE64, "image/png")));

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).dimension()).isEqualTo(256);
    }

    @Test
    void should_produce_embeddings_for_text_and_image() {
        // given
        VertexAiMultimodalEmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(256)
                .build();

        // Embed text via EmbeddingModel interface
        Embedding textEmbedding = model.embed("a red pixel").content();

        // Embed image via ImageEmbeddingModel interface (1x1 red PNG)
        ImageContent image = ImageContent.from(TEST_IMAGE_BASE64, "image/png");
        Embedding imageEmbedding = model.embed(image).content();

        // then - both should produce valid embeddings of the same dimension
        assertThat(textEmbedding.dimension()).isEqualTo(256);
        assertThat(imageEmbedding.dimension()).isEqualTo(256);

        // and they should be in the same vector space (cosine similarity is computable)
        double similarity = cosineSimilarity(textEmbedding, imageEmbedding);
        assertThat(similarity).isBetween(-1.0, 1.0);
    }

    @Test
    void should_be_usable_as_both_interfaces() {
        // given
        VertexAiMultimodalEmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(256)
                .build();

        // then - can assign to EmbeddingModel
        EmbeddingModel embeddingModel = model;
        assertThat(embeddingModel.embed("test").content().dimension()).isEqualTo(256);

        // then - can assign to ImageEmbeddingModel
        ImageEmbeddingModel imageModel = model;
        assertThat(imageModel.dimension()).isEqualTo(256);
    }

    @Test
    void should_support_different_dimensions() {
        // Test 128 dimension
        VertexAiMultimodalEmbeddingModel model128 = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(128)
                .build();

        Response<Embedding> response128 = model128.embed("test");
        assertThat(response128.content().dimension()).isEqualTo(128);

        // Test 512 dimension
        VertexAiMultimodalEmbeddingModel model512 = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(512)
                .build();

        Response<Embedding> response512 = model512.embed("test");
        assertThat(response512.content().dimension()).isEqualTo(512);
    }

    @Test
    void should_return_correct_model_name() {
        // given
        VertexAiMultimodalEmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .build();

        // when/then
        assertThat(model.modelName()).isEqualTo("multimodalembedding@001");
    }

    @Test
    void should_return_correct_dimension() {
        // given
        VertexAiMultimodalEmbeddingModel model = VertexAiMultimodalEmbeddingModel.builder()
                .project(PROJECT_ID)
                .location(LOCATION)
                .outputDimension(256)
                .build();

        // when/then - dimension() works on both interfaces
        assertThat(((EmbeddingModel) model).dimension()).isEqualTo(256);
        assertThat(((ImageEmbeddingModel) model).dimension()).isEqualTo(256);
    }

    private double cosineSimilarity(Embedding e1, Embedding e2) {
        float[] v1 = e1.vector();
        float[] v2 = e2.vector();
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;
        for (int i = 0; i < v1.length; i++) {
            dotProduct += v1[i] * v2[i];
            norm1 += v1[i] * v1[i];
            norm2 += v2[i] * v2[i];
        }
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
