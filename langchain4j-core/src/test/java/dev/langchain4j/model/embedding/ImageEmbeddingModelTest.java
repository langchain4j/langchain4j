package dev.langchain4j.model.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageEmbeddingModelTest {

    @Test
    void should_embed_image_via_default_method() {
        // given
        ImageEmbeddingModel model = new TestImageEmbeddingModel();
        ImageContent image = ImageContent.from("base64data", "image/png");

        // when
        Response<Embedding> response = model.embed(image);

        // then
        assertThat(response.content().dimension()).isEqualTo(256);
    }

    @Test
    void should_embed_image_from_base64() {
        // given
        ImageEmbeddingModel model = new TestImageEmbeddingModel();

        // when
        Response<Embedding> response = model.embed("base64data", "image/jpeg");

        // then
        assertThat(response.content().dimension()).isEqualTo(256);
    }

    @Test
    void should_embed_image_from_uri() {
        // given
        ImageEmbeddingModel model = new TestImageEmbeddingModel();

        // when
        Response<Embedding> response = model.embed(URI.create("gs://bucket/image.png"));

        // then
        assertThat(response.content().dimension()).isEqualTo(256);
    }

    @Test
    void should_embed_multiple_images() {
        // given
        ImageEmbeddingModel model = new TestImageEmbeddingModel();
        List<ImageContent> images = List.of(
                ImageContent.from("base64data1", "image/png"),
                ImageContent.from("base64data2", "image/jpeg")
        );

        // when
        Response<List<Embedding>> response = model.embedAllImages(images);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).dimension()).isEqualTo(256);
        assertThat(response.content().get(1).dimension()).isEqualTo(256);
    }

    @Test
    void should_return_unknown_for_default_model_name() {
        // given
        ImageEmbeddingModel model = new TestImageEmbeddingModel();

        // when
        String name = model.modelName();

        // then
        assertThat(name).isEqualTo("unknown");
    }

    @Test
    void should_return_overridden_model_name() {
        // given
        final String expected = "test-image-embedding-model";
        ImageEmbeddingModel model = new ImageEmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAllImages(List<ImageContent> images) {
                return Response.from(List.of());
            }

            @Override
            public String modelName() {
                return expected;
            }
        };

        // when
        String name = model.modelName();

        // then
        assertThat(name).isEqualTo(expected);
    }

    @Test
    void should_throw_exception_for_default_dimension() {
        // given
        ImageEmbeddingModel model = new ImageEmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAllImages(List<ImageContent> images) {
                return Response.from(List.of());
            }
        };

        // when/then
        assertThatThrownBy(model::dimension)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("dimension() not implemented");
    }

    // Test implementation
    static class TestImageEmbeddingModel implements ImageEmbeddingModel {
        @Override
        public Response<List<Embedding>> embedAllImages(List<ImageContent> images) {
            List<Embedding> embeddings = images.stream()
                    .map(img -> Embedding.from(new float[256]))
                    .toList();
            return Response.from(embeddings);
        }

        @Override
        public int dimension() {
            return 256;
        }
    }
}
