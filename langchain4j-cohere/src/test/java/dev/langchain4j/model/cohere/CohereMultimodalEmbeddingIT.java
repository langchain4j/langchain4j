package dev.langchain4j.model.cohere;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.store.embedding.CosineSimilarity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "COHERE_API_KEY", matches = ".+")
class CohereMultimodalEmbeddingIT {

    private static final String CAT_IMAGE_URL =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/3/3a/Cat03.jpg/240px-Cat03.jpg";

    private final CohereEmbeddingModel model = CohereEmbeddingModel.builder()
            .apiKey(System.getenv("COHERE_API_KEY"))
            .modelName("embed-v4.0")
            .logRequests(true)
            .logResponses(false)
            .build();

    @Test
    void should_embed_text_and_image_into_the_same_space() {

        Embedding catText = embedOne(EmbeddingRequest.builder()
                .input("a photograph of a cat")
                .inputType(EmbeddingInputType.QUERY)
                .build());

        Embedding financeText = embedOne(EmbeddingRequest.builder()
                .input("quarterly financial earnings report")
                .inputType(EmbeddingInputType.QUERY)
                .build());

        Embedding catImage = embedOne(EmbeddingRequest.builder()
                .input(ImageContent.from(CAT_IMAGE_URL))
                .inputType(EmbeddingInputType.DOCUMENT)
                .build());

        assertThat(catText.dimension()).isEqualTo(catImage.dimension());

        double catSimilarity = CosineSimilarity.between(catText, catImage);
        double financeSimilarity = CosineSimilarity.between(financeText, catImage);
        assertThat(catSimilarity).isGreaterThan(financeSimilarity);
    }

    @Test
    void should_embed_interleaved_text_and_image() {

        Embedding embedding = embedOne(EmbeddingRequest.builder()
                .input(TextContent.from("a photo of "), ImageContent.from(CAT_IMAGE_URL))
                .inputType(EmbeddingInputType.DOCUMENT)
                .build());

        assertThat(embedding.vector()).isNotEmpty();
    }

    private Embedding embedOne(EmbeddingRequest request) {
        EmbeddingResponse response = model.embed(request);
        assertThat(response.embeddings()).hasSize(1);
        return response.embeddings().get(0);
    }
}
