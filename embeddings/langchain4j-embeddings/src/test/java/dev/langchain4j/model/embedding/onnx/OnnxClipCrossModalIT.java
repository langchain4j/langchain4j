package dev.langchain4j.model.embedding.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.store.embedding.CosineSimilarity;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Verifies that images embedded by {@link OnnxImageEmbeddingModel} can be searched with a text query embedded by
 * {@link OnnxEmbeddingModel}, using the two halves of the same CLIP model. Both halves project into one shared
 * vector space, which is what makes searching images by describing them in words possible.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class OnnxClipCrossModalIT {

    private static OnnxImageEmbeddingModel imageModel;
    private static OnnxEmbeddingModel textModel;

    @BeforeAll
    static void loadModels() {
        imageModel = OnnxImageEmbeddingModel.builder()
                .pathToModel(ClipTestModels.visionModel())
                .preprocessorConfig(ImagePreprocessorConfig.CLIP)
                .build();
        textModel = new OnnxEmbeddingModel(ClipTestModels.textModel(), ClipTestModels.tokenizer(), PoolingMode.CLS);
    }

    @AfterAll
    static void closeModels() {
        if (imageModel != null) {
            imageModel.close();
        }
    }

    private static Embedding embedImage(String resource) throws Exception {
        return imageModel
                .embed(EmbeddingRequest.builder()
                        .input(ImageContent.from(
                                OnnxClipCrossModalIT.class.getResource(resource).toURI()))
                        .build())
                .embeddings()
                .get(0);
    }

    @Test
    @DisplayName("the text and image towers of CLIP should produce embeddings of the same dimension")
    void towers_should_share_the_same_vector_space() {
        assertThat(textModel.dimension()).isEqualTo(imageModel.dimension());
    }

    @Test
    @DisplayName("a photo of a cat should be closer to 'a cat' than to an unrelated description")
    void image_should_be_closer_to_matching_text() throws Exception {
        Embedding catImage = embedImage("/cat.png");

        Embedding matchingText = textModel.embed("a photo of a cat").content();
        Embedding unrelatedText = textModel.embed("a photo of a red sports car").content();

        double matchingScore = CosineSimilarity.between(catImage, matchingText);
        double unrelatedScore = CosineSimilarity.between(catImage, unrelatedText);

        assertThat(matchingScore)
                .as(
                        "A photo of a cat should be more similar to 'a cat' (%s) than to 'a car' (%s)",
                        matchingScore, unrelatedScore)
                .isGreaterThan(unrelatedScore);
    }

    @Test
    @DisplayName("the same photo in a different format should rank the same way against a text query")
    void ranking_should_be_stable_across_image_formats() throws Exception {
        Embedding text = textModel.embed("a photo of a cat").content();

        double pngScore = CosineSimilarity.between(embedImage("/cat.png"), text);
        double jpgScore = CosineSimilarity.between(embedImage("/cat.jpg"), text);

        assertThat(pngScore).isCloseTo(jpgScore, org.assertj.core.data.Offset.offset(0.1));
    }
}
