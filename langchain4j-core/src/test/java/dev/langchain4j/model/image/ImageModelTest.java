package dev.langchain4j.model.image;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.output.Response;
import java.net.URI;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ImageModelTest implements WithAssertions {

    public static class FixedImageModel implements ImageModel {
        private final Image image;

        public FixedImageModel(Image image) {
            this.image = image;
        }

        @Override
        public Response<Image> generate(String prompt) {
            return Response.from(image);
        }
    }

    public static final Image PLACEHOLDER_IMAGE;

    static {
        try {
            PLACEHOLDER_IMAGE = Image.builder().url(new URI("https://foo.bar")).build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void not_supported() {
        ImageModel model = new FixedImageModel(PLACEHOLDER_IMAGE);

        assertThatThrownBy(() -> model.generate("prompt", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operation is not supported");

        assertThatThrownBy(() -> model.edit(null, "prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operation is not supported");

        assertThatThrownBy(() -> model.edit(null, null, "prompt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Operation is not supported");
    }

    @Test
    void trivial() {
        ImageModel model = new FixedImageModel(PLACEHOLDER_IMAGE);
        Response<Image> response = model.generate("prompt");

        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo(PLACEHOLDER_IMAGE);
    }
}
