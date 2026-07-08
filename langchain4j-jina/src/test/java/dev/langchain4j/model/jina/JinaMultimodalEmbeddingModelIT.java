package dev.langchain4j.model.jina;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "JINA_API_KEY", matches = ".+")
class JinaMultimodalEmbeddingModelIT {

    private static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/240px-Cat03.jpg";

    private final JinaEmbeddingModel model = JinaEmbeddingModel.builder()
            .apiKey(System.getenv("JINA_API_KEY"))
            .modelName("jina-clip-v2")
            .build();

    @Test
    void should_embed_text_and_image_into_the_same_space() {
        Embedding catText = embedOne(EmbeddingRequest.builder().input("a photograph of a cat").build());
        Embedding financeText =
                embedOne(EmbeddingRequest.builder().input("quarterly financial earnings report").build());
        Embedding catImage = embedOne(EmbeddingRequest.builder()
                .input(ImageContent.from(CAT_IMAGE_URL))
                .build());

        assertThat(catText.dimension()).isEqualTo(catImage.dimension());
        assertThat(CosineSimilarity.between(catText, catImage))
                .isGreaterThan(CosineSimilarity.between(financeText, catImage));
    }

    @Test
    void should_batch_text_and_image_items() {
        EmbeddingResponse response = model.embed(EmbeddingRequest.builder()
                .input("a caption")
                .input(ImageContent.from(CAT_IMAGE_URL))
                .build());

        // one embedding per input item (Jina embeds one modality per item)
        assertThat(response.embeddings()).hasSize(2);
    }

    private Embedding embedOne(EmbeddingRequest request) {
        EmbeddingResponse response = model.embed(request);
        assertThat(response.embeddings()).hasSize(1);
        return response.embeddings().get(0);
    }
}
