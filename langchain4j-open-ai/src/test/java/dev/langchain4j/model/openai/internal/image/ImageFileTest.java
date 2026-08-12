package dev.langchain4j.model.openai.internal.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.image.Image;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ImageFileTest {

    @Test
    void should_decode_base64_content() {
        byte[] bytes = {1, 2, 3, 4};
        Image image = Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(bytes))
                .mimeType("image/png")
                .build();

        ImageFile imageFile = ImageFile.from(image);

        assertThat(imageFile.content()).containsExactly(bytes);
        assertThat(imageFile.mimeType()).isEqualTo("image/png");
        assertThat(imageFile.fileName()).isEqualTo("image.png");
    }

    @Test
    void should_default_mime_type_and_extension_when_not_provided() {
        Image image = Image.builder()
                .base64Data(Base64.getEncoder().encodeToString(new byte[] {1}))
                .build();

        ImageFile imageFile = ImageFile.from(image);

        assertThat(imageFile.mimeType()).isEqualTo("image/png");
        assertThat(imageFile.fileName()).isEqualTo("image.png");
    }

    @Test
    void should_map_jpeg_and_webp_extensions() {
        String data = Base64.getEncoder().encodeToString(new byte[] {1});

        assertThat(ImageFile.from(Image.builder()
                                .base64Data(data)
                                .mimeType("image/jpeg")
                                .build())
                        .fileName())
                .isEqualTo("image.jpg");
        assertThat(ImageFile.from(Image.builder()
                                .base64Data(data)
                                .mimeType("image/webp")
                                .build())
                        .fileName())
                .isEqualTo("image.webp");
    }

    @Test
    void should_throw_for_url_based_image() {
        Image image = Image.builder().url("https://example.com/image.png").build();

        assertThatThrownBy(() -> ImageFile.from(image).content())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("URL-based image is not supported");
    }

    @Test
    void should_throw_when_no_data() {
        Image image = Image.builder().build();

        assertThatThrownBy(() -> ImageFile.from(image).content())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No image data found");
    }

    @Test
    void should_throw_for_invalid_base64() {
        Image image = Image.builder().base64Data("not valid base64!!!").build();

        assertThatThrownBy(() -> ImageFile.from(image).content())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid base64 image data");
    }
}
