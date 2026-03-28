package dev.langchain4j.model.embedding.onnx;

import static dev.langchain4j.model.embedding.onnx.PoolingMode.CLS;
import static dev.langchain4j.model.embedding.onnx.PoolingMode.MEAN;
import static dev.langchain4j.model.embedding.onnx.internal.VectorUtils.magnitudeOf;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.store.embedding.CosineSimilarity;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(value = 120, unit = TimeUnit.SECONDS)
class OnnxImageEncoderIT {

    private static final int EXPECTED_DIMENSION = 512;
    private static final int CLIP_INPUT_SIZE = 224;

    private static final String VIT_BASE_PATCH32_URL =
            "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model_quantized.onnx?download=true";

    private static final String CAT_IMAGE_URL =
            OnnxImageEncoderIT.class.getResource("/cat.png").toString();

    @TempDir
    static Path tempDir;

    private static Path modelPath;
    private static ImagePreprocessorConfig config;

    @BeforeAll
    static void downloadModelAndConfigure() throws IOException {
        modelPath = tempDir.resolve("clip_vision.onnx");

        // Download with explicit timeout to avoid hanging in CI
        HttpURLConnection conn = (HttpURLConnection) new URL(VIT_BASE_PATCH32_URL).openConnection();
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(120_000);
        try (InputStream is = conn.getInputStream()) {
            Files.copy(is, modelPath, REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }

        assertThat(Files.size(modelPath))
                .as("Downloaded model should be non-trivial in size")
                .isGreaterThan(1_000_000L);

        // Standard CLIP preprocessing: ImageNet-derived normalisation values
        config = ImagePreprocessorConfig.builder()
                .imageMean(new float[] {0.48145466f, 0.4578275f, 0.40821073f})
                .imageStd(new float[] {0.26862954f, 0.26130258f, 0.27577711f})
                .build();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static int[] solidColorPixels(int argb) {
        int[] pixels = new int[CLIP_INPUT_SIZE * CLIP_INPUT_SIZE];
        Arrays.fill(pixels, argb);
        return pixels;
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

    // ── basic embedding tests ───────────────────────────────────────────

    @Nested
    @DisplayName("Basic embedding")
    class BasicEmbedding {

        @Test
        @DisplayName("should produce a normalised 768-d embedding from a remote URL image")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void should_embed_image_from_url() throws Exception {
            try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                Image image = Image.builder().url(CAT_IMAGE_URL).build();

                Embedding embedding = encoder.embed(image);

                assertValidNormalisedEmbedding(embedding);
            }
        }

        @Test
        @DisplayName("should produce a normalised 768-d embedding from raw ARGB pixel data")
        void should_embed_raw_pixels() throws Exception {
            try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                int[] pixels = solidColorPixels(0xFF00FF00); // solid green

                Embedding embedding = encoder.embed(pixels, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);

                assertValidNormalisedEmbedding(embedding);
            }
        }

        @Test
        @DisplayName("should produce a normalised embedding from non-square pixel data")
        void should_embed_non_square_image() throws Exception {
            try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                int width = 320;
                int height = 180;
                int[] pixels = new int[width * height];
                Arrays.fill(pixels, 0xFFFF0000); // solid red

                Embedding embedding = encoder.embed(pixels, width, height);

                assertValidNormalisedEmbedding(embedding);
            }
        }
    }

    // ── determinism ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("same input pixels should always produce identical embeddings")
        void same_input_should_produce_identical_embeddings() throws Exception {
            try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                int[] pixels = solidColorPixels(0xFF0000FF);

                Embedding first = encoder.embed(pixels, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);
                Embedding second = encoder.embed(pixels, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);

                assertThat(first.vector()).isEqualTo(second.vector());
            }
        }

        // ── similarity tests ────────────────────────────────────────────────

        @Nested
        @DisplayName("Cosine similarity")
        class Similarity {

            @Test
            @DisplayName("visually similar images should have cosine similarity > 0.95")
            void similar_images_should_have_high_cosine_similarity() throws Exception {
                try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                    int[] green = solidColorPixels(0xFF00AA00);
                    int[] lightGreen = solidColorPixels(0xFF00BB00);

                    Embedding emb1 = encoder.embed(green, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);
                    Embedding emb2 = encoder.embed(lightGreen, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);

                    double similarity = CosineSimilarity.between(emb1, emb2);
                    assertThat(similarity)
                            .as("Two similar solid-colour images should be very close in embedding space")
                            .isGreaterThan(0.95);
                }
            }

            @Test
            @DisplayName("visually dissimilar images should have lower cosine similarity than similar ones")
            void dissimilar_images_should_have_lower_similarity_than_similar_ones() throws Exception {
                try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                    int[] red = solidColorPixels(0xFFFF0000);
                    int[] green = solidColorPixels(0xFF00FF00);
                    int[] lightGreen = solidColorPixels(0xFF00EE00);

                    Embedding redEmb = encoder.embed(red, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);
                    Embedding greenEmb = encoder.embed(green, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);
                    Embedding lightGreenEmb = encoder.embed(lightGreen, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);

                    double similarPairScore = CosineSimilarity.between(greenEmb, lightGreenEmb);
                    double dissimilarPairScore = CosineSimilarity.between(redEmb, greenEmb);

                    assertThat(similarPairScore)
                            .as("Green vs light-green should be more similar than red vs green")
                            .isGreaterThan(dissimilarPairScore);
                }
            }
        }

        @Nested
        @DisplayName("Pooling modes (pre-pooled model)")
        class PoolingModes {

            @Test
            @DisplayName("CLS and MEAN should produce identical embeddings when model output is already pooled")
            void pooling_mode_should_be_ignored_for_pre_pooled_output() throws Exception {
                try (var clsEncoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                    try (var meanEncoder = new OnnxImageEncoder(modelPath, config, MEAN)) {
                        int[] pixels = solidColorPixels(0xFF808080);

                        Embedding clsEmbedding = clsEncoder.embed(pixels, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);
                        Embedding meanEmbedding = meanEncoder.embed(pixels, CLIP_INPUT_SIZE, CLIP_INPUT_SIZE);

                        assertThat(clsEmbedding.vector())
                                .as("Pre-pooled model output should be identical regardless of PoolingMode")
                                .isEqualTo(meanEmbedding.vector());
                    }
                }
            }
        }

        @Nested
        @DisplayName("Error handling")
        class ErrorHandling {

            @Test
            @DisplayName("should reject null Image")
            void should_reject_null_image() throws Exception {
                try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                    assertThatThrownBy(() -> encoder.embed(null)).isInstanceOf(NullPointerException.class);
                }
            }

            @Test
            @DisplayName("should reject null pixel array")
            void should_reject_null_pixels() throws Exception {
                try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                    assertThatThrownBy(() -> encoder.embed(null, 224, 224)).isInstanceOf(NullPointerException.class);
                }
            }

            @Test
            @DisplayName("should reject zero-dimension image")
            void should_reject_zero_dimensions() throws Exception {
                try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                    assertThatThrownBy(() -> encoder.embed(new int[0], 0, 0))
                            .isInstanceOf(IllegalArgumentException.class);
                }
            }

            @Test
            @DisplayName("should throw on unreachable image URL")
            void should_throw_on_unreachable_url() throws Exception {
                try (var encoder = new OnnxImageEncoder(modelPath, config, CLS)) {
                    Image image = Image.builder()
                            .url("https://this-domain-does-not-exist-12345.example/img.jpg")
                            .build();

                    assertThatThrownBy(() -> encoder.embed(image)).isInstanceOf(RuntimeException.class);
                }
            }
        }
    }
}
