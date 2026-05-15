package dev.langchain4j.model.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultiInterfaceEmbeddingModelTest {

    @Test
    void should_support_both_text_and_image_embedding() {
        // given
        MultimodalModel model = new MultimodalModel();

        // when - use as EmbeddingModel
        Response<Embedding> textResponse = model.embed("hello world");

        // then
        assertThat(textResponse.content().dimension()).isEqualTo(128);

        // when - use as ImageEmbeddingModel
        ImageContent image = ImageContent.from("base64data", "image/png");
        Response<Embedding> imageResponse = model.embed(image);

        // then
        assertThat(imageResponse.content().dimension()).isEqualTo(128);
    }

    @Test
    void should_be_assignable_to_both_interfaces() {
        // given
        MultimodalModel model = new MultimodalModel();

        // then - can be used as EmbeddingModel
        EmbeddingModel embeddingModel = model;
        assertThat(embeddingModel.embed("test").content()).isNotNull();

        // then - can be used as ImageEmbeddingModel
        ImageEmbeddingModel imageModel = model;
        assertThat(imageModel.embed(ImageContent.from("data", "image/png")).content())
                .isNotNull();
    }

    @Test
    void should_have_separate_embedAll_methods() {
        // given
        MultimodalModel model = new MultimodalModel();
        List<TextSegment> textSegments = List.of(TextSegment.from("text1"), TextSegment.from("text2"));
        List<ImageContent> images =
                List.of(ImageContent.from("data1", "image/png"), ImageContent.from("data2", "image/jpeg"));

        // when
        Response<List<Embedding>> textResponse = model.embedAll(textSegments);
        Response<List<Embedding>> imageResponse = model.embedAllImages(images);

        // then
        assertThat(textResponse.content()).hasSize(2);
        assertThat(imageResponse.content()).hasSize(2);
    }

    @Test
    void should_share_dimension_across_modalities() {
        // given
        MultimodalModel model = new MultimodalModel();

        // when - get dimension from EmbeddingModel
        EmbeddingModel embeddingModel = model;
        int textDimension = embeddingModel.dimension();

        // when - get dimension from ImageEmbeddingModel
        ImageEmbeddingModel imageModel = model;
        int imageDimension = imageModel.dimension();

        // then - both should return the same dimension
        assertThat(textDimension).isEqualTo(128);
        assertThat(imageDimension).isEqualTo(128);
    }

    // Example multi-interface implementation
    static class MultimodalModel implements EmbeddingModel, ImageEmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
            List<Embedding> embeddings = textSegments.stream()
                    .map(s -> Embedding.from(new float[128]))
                    .toList();
            return Response.from(embeddings);
        }

        @Override
        public Response<List<Embedding>> embedAllImages(List<ImageContent> images) {
            List<Embedding> embeddings =
                    images.stream().map(img -> Embedding.from(new float[128])).toList();
            return Response.from(embeddings);
        }

        @Override
        public int dimension() {
            return 128;
        }

        // Must override modelName() to resolve conflict between EmbeddingModel and ImageEmbeddingModel defaults
        @Override
        public String modelName() {
            return "test-multimodal-model";
        }
    }
}
