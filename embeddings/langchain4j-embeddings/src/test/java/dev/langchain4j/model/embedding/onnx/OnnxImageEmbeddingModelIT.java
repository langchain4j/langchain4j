package dev.langchain4j.model.embedding.onnx;

import static dev.langchain4j.model.embedding.onnx.PoolingMode.CLS;
import static dev.langchain4j.model.embedding.onnx.PoolingMode.MEAN;
import static dev.langchain4j.model.embedding.onnx.internal.VectorUtils.magnitudeOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ContentType;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.store.embedding.CosineSimilarity;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 120, unit = TimeUnit.SECONDS)
class OnnxImageEmbeddingModelIT {

    private static final int EXPECTED_DIMENSION = 512;

    private static final URI CAT_IMAGE_URI = uriOf("/cat.png");

    private static OnnxImageEmbeddingModel model;

    @BeforeAll
    static void loadModel() {
        model = OnnxImageEmbeddingModel.builder()
                .pathToModel(ClipTestModels.visionModel())
                .preprocessorConfig(ImagePreprocessorConfig.CLIP)
                .poolingMode(CLS)
                .build();
    }

    @AfterAll
    static void closeModel() {
        if (model != null) {
            model.close();
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static URI uriOf(String resource) {
        try {
            return OnnxImageEmbeddingModelIT.class.getResource(resource).toURI();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static ImageContent solidColor(int rgb) {
        return solidColor(rgb, 224, 224);
    }

    private static ImageContent solidColor(int rgb, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(rgb));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return ImageContent.from(Base64.getEncoder().encodeToString(bytes.toByteArray()), "image/png");
    }

    private static Embedding embed(ImageContent image) {
        EmbeddingResponse response =
                model.embed(EmbeddingRequest.builder().input(image).build());
        assertThat(response.embeddings()).hasSize(1);
        return response.embeddings().get(0);
    }

    private static void assertValidNormalisedEmbedding(Embedding embedding) {
        assertThat(embedding).isNotNull();
        assertThat(embedding.vector())
                .as("Embedding dimension must match CLIP ViT-B/32 output")
                .hasSize(EXPECTED_DIMENSION);
        assertThat(magnitudeOf(embedding))
                .as("Embedding should be L2-normalised")
                .isCloseTo(1.0f, withPercentage(0.1));
    }

    // ── basic embedding ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Basic embedding")
    class BasicEmbedding {

        @Test
        @DisplayName("should embed an image referenced by URL")
        void should_embed_image_from_url() {
            assertValidNormalisedEmbedding(embed(ImageContent.from(CAT_IMAGE_URI)));
        }

        @Test
        @DisplayName("should embed an image provided as base64 data")
        void should_embed_image_from_base64() {
            assertValidNormalisedEmbedding(embed(solidColor(0x00FF00)));
        }

        @Test
        @DisplayName("should embed a non-square image")
        void should_embed_non_square_image() {
            assertValidNormalisedEmbedding(embed(solidColor(0xFF0000, 320, 180)));
        }

        @Test
        @DisplayName("should embed a batch of images into one embedding each, in order")
        void should_embed_batch() {
            ImageContent red = solidColor(0xFF0000);
            ImageContent green = solidColor(0x00FF00);

            EmbeddingResponse response = model.embed(
                    EmbeddingRequest.builder().input(red).input(green).build());

            assertThat(response.embeddings()).hasSize(2);
            response.embeddings().forEach(OnnxImageEmbeddingModelIT::assertValidNormalisedEmbedding);
            assertThat(response.embeddings().get(0).vector())
                    .as("Batched embeddings should match the ones produced one by one, in the same order")
                    .isEqualTo(embed(red).vector());
            assertThat(response.embeddings().get(1).vector())
                    .isEqualTo(embed(green).vector());
        }

        @Test
        @DisplayName("should report the dimension of the model")
        void should_report_dimension() {
            assertThat(model.dimension()).isEqualTo(EXPECTED_DIMENSION);
        }

        @Test
        @DisplayName("should accept images only")
        void should_support_image_content_only() {
            assertThat(model.supportedContentTypes()).containsExactly(ContentType.IMAGE);
        }
    }

    // ── determinism ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("same image should always produce an identical embedding")
        void same_input_should_produce_identical_embeddings() {
            ImageContent image = solidColor(0x0000FF);

            assertThat(embed(image).vector()).isEqualTo(embed(image).vector());
        }
    }

    // ── similarity ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Cosine similarity")
    class Similarity {

        @Test
        @DisplayName("visually similar images should have cosine similarity > 0.95")
        void similar_images_should_have_high_cosine_similarity() {
            Embedding green = embed(solidColor(0x00AA00));
            Embedding lightGreen = embed(solidColor(0x00BB00));

            assertThat(CosineSimilarity.between(green, lightGreen))
                    .as("Two similar solid-colour images should be very close in embedding space")
                    .isGreaterThan(0.95);
        }

        @Test
        @DisplayName("visually dissimilar images should be less similar than similar ones")
        void dissimilar_images_should_have_lower_similarity_than_similar_ones() {
            Embedding red = embed(solidColor(0xFF0000));
            Embedding green = embed(solidColor(0x00FF00));
            Embedding lightGreen = embed(solidColor(0x00EE00));

            assertThat(CosineSimilarity.between(green, lightGreen))
                    .as("Green vs light-green should be more similar than red vs green")
                    .isGreaterThan(CosineSimilarity.between(red, green));
        }
    }

    // ── pooling ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pooling modes")
    class PoolingModes {

        @Test
        @DisplayName("CLS and MEAN should agree when the model output is already pooled")
        void pooling_mode_should_be_ignored_for_pre_pooled_output() {
            ImageContent image = solidColor(0x808080);

            try (OnnxImageEmbeddingModel meanModel = OnnxImageEmbeddingModel.builder()
                    .pathToModel(ClipTestModels.visionModel())
                    .preprocessorConfig(ImagePreprocessorConfig.CLIP)
                    .poolingMode(MEAN)
                    .build()) {

                EmbeddingResponse meanResponse =
                        meanModel.embed(EmbeddingRequest.builder().input(image).build());

                assertThat(meanResponse.embeddings().get(0).vector())
                        .as("A pre-pooled model output should be identical regardless of PoolingMode")
                        .isEqualTo(embed(image).vector());
            }
        }
    }

    // ── error handling ──────────────────────────────────────────────────

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("should reject text, as it can only embed images")
        void should_reject_text() {
            assertThatThrownBy(() -> model.embed("a cat"))
                    .isInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("TEXT");
        }

        @Test
        @DisplayName("should reject an input mixing text and an image")
        void should_reject_text_mixed_with_image() {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input(TextContent.from("a cat"), solidColor(0x00FF00))
                    .build();

            assertThatThrownBy(() -> model.embed(request))
                    .isInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("TEXT");
        }

        @Test
        @DisplayName("should reject an input holding more than one image")
        void should_reject_multiple_images_in_one_input() {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input(solidColor(0xFF0000), solidColor(0x00FF00))
                    .build();

            assertThatThrownBy(() -> model.embed(request))
                    .isInstanceOf(UnsupportedFeatureException.class)
                    .hasMessageContaining("more than one image");
        }

        @Test
        @DisplayName("should fail on an unreachable image URL")
        void should_throw_on_unreachable_url() {
            EmbeddingRequest request = EmbeddingRequest.builder()
                    .input(ImageContent.from("https://this-domain-does-not-exist-12345.example/img.jpg"))
                    .build();

            assertThatThrownBy(() -> model.embed(request)).isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should reject a model file that does not exist")
        void should_reject_missing_model_file() {
            assertThatThrownBy(() -> OnnxImageEmbeddingModel.builder()
                            .pathToModel(ClipTestModels.visionModel().resolveSibling("does-not-exist.onnx"))
                            .build())
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("should reject a null path to the model")
        void should_reject_null_model_path() {
            assertThatThrownBy(() -> OnnxImageEmbeddingModel.builder().build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pathToModel");
        }
    }

    // ── configuration ───────────────────────────────────────────────────

    @Nested
    @DisplayName("Preprocessor configuration")
    class Configuration {

        @Test
        @DisplayName("different normalisation should produce a different embedding for the same image")
        void normalisation_values_should_affect_the_embedding() {
            ImageContent image = ImageContent.from(CAT_IMAGE_URI);

            try (OnnxImageEmbeddingModel defaultModel = OnnxImageEmbeddingModel.builder()
                    .pathToModel(ClipTestModels.visionModel())
                    .build()) {

                EmbeddingResponse response = defaultModel.embed(
                        EmbeddingRequest.builder().input(image).build());
                List<Embedding> embeddings = response.embeddings();

                assertValidNormalisedEmbedding(embeddings.get(0));
                assertThat(embeddings.get(0).vector())
                        .as("The default preprocessing differs from CLIP's, so the embedding must differ too")
                        .isNotEqualTo(embed(image).vector());
            }
        }
    }
}
