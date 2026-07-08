package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class BedrockTitanMultimodalIT {

    // A 1x1 transparent PNG, base64-encoded.
    private static final String TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    private final BedrockTitanEmbeddingModel model = BedrockTitanEmbeddingModel.builder().model("amazon.titan-embed-image-v1")
            .region(Region.US_EAST_1)
            .dimensions(256)
            .build();

    @Test
    void should_embed_text() {
        Embedding embedding = embedOne(EmbeddingRequest.builder().input("a photo of a cat").build());
        assertThat(embedding.dimension()).isEqualTo(256);
    }

    @Test
    void should_embed_image() {
        Embedding embedding = embedOne(EmbeddingRequest.builder()
                .input(ImageContent.from(TINY_PNG_BASE64, "image/png"))
                .build());
        assertThat(embedding.dimension()).isEqualTo(256);
    }

    @Test
    void should_embed_text_and_image_together() {
        Embedding embedding = embedOne(EmbeddingRequest.builder()
                .input(
                        dev.langchain4j.data.message.TextContent.from("a cat"),
                        ImageContent.from(TINY_PNG_BASE64, "image/png"))
                .build());
        assertThat(embedding.vector()).isNotEmpty();
    }

    private Embedding embedOne(EmbeddingRequest request) {
        EmbeddingResponse response = model.embed(request);
        assertThat(response.embeddings()).hasSize(1);
        return response.embeddings().get(0);
    }
}
