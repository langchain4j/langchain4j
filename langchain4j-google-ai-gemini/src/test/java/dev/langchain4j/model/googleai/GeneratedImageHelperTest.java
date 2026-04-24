package dev.langchain4j.model.googleai;

import static dev.langchain4j.data.message.AiMessage.GENERATED_IMAGES_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneratedImageHelperTest {

    @Test
    void should_extract_generated_images_from_ai_message() {
        // given
        Image image1 = Image.builder()
                .base64Data(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==")
                .mimeType("image/png")
                .build();

        Image image2 = Image.builder()
                .base64Data(
                        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAEklEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==")
                .mimeType("image/jpeg")
                .build();

        AiMessage message = AiMessage.builder()
                .text("Here are the generated images")
                .attributes(Map.of(GENERATED_IMAGES_KEY, List.of(image1, image2)))
                .build();

        // when
        List<Image> generatedImages = GeneratedImageHelper.getGeneratedImages(message);
        boolean hasImages = GeneratedImageHelper.hasGeneratedImages(message);

        // then
        assertThat(hasImages).isTrue();
        assertThat(generatedImages).hasSize(2);
        assertThat(generatedImages.get(0).base64Data()).isEqualTo(image1.base64Data());
        assertThat(generatedImages.get(0).mimeType()).isEqualTo("image/png");
        assertThat(generatedImages.get(1).base64Data()).isEqualTo(image2.base64Data());
        assertThat(generatedImages.get(1).mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    void should_return_empty_list_when_no_generated_images() {
        // given
        AiMessage message = AiMessage.builder().text("Just text, no images").build();

        // when
        List<Image> generatedImages = GeneratedImageHelper.getGeneratedImages(message);
        boolean hasImages = GeneratedImageHelper.hasGeneratedImages(message);

        // then
        assertThat(hasImages).isFalse();
        assertThat(generatedImages).isEmpty();
    }

    @Test
    void should_handle_null_message() {
        // when
        List<Image> generatedImages = GeneratedImageHelper.getGeneratedImages(null);
        boolean hasImages = GeneratedImageHelper.hasGeneratedImages(null);

        // then
        assertThat(hasImages).isFalse();
        assertThat(generatedImages).isEmpty();
    }
}
