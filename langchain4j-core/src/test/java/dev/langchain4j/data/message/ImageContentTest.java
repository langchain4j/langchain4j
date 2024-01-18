package dev.langchain4j.data.message;

import dev.langchain4j.data.image.Image;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

class ImageContentTest implements WithAssertions {
    @Test
    public void test_methods() {
        Image urlImage = Image.builder()
                .url(URI.create("https://example.com/image.png"))
                .build();
        ImageContent imageContent = new ImageContent(urlImage, ImageContent.DetailLevel.HIGH);

        assertThat(imageContent.image()).isEqualTo(urlImage);
        assertThat(imageContent.detailLevel()).isEqualTo(ImageContent.DetailLevel.HIGH);
        assertThat(imageContent.type()).isEqualTo(ContentType.IMAGE);

        assertThat(imageContent)
                .hasToString(
                "ImageContent { " +
                        "image = Image { " +
                        "url = \"https://example.com/image.png\", " +
                        "base64Data = null, mimeType = null, revisedPrompt = null } " +
                        "detailLevel = HIGH }");
    }

    @Test
    public void test_equals_hashCode() {
        ImageContent i1 = ImageContent.from("https://example.com/image.png");
        ImageContent i2 = ImageContent.from("https://example.com/image.png");

        ImageContent i3 = ImageContent.from("https://example.com/image.jpg");
        ImageContent i4 = ImageContent.from("https://example.com/image.jpg");

        assertThat(i1)
                .isEqualTo(i1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(i2)
                .hasSameHashCodeAs(i2)
                .isNotEqualTo(i3)
                .isNotEqualTo(i4);

        assertThat(i3)
                .isEqualTo(i3)
                .isEqualTo(i4)
                .hasSameHashCodeAs(i4);
    }

    @Test
    public void test_builders() {
        Image urlImage = Image.builder()
                .url(URI.create("https://example.com/image.png"))
                .build();
        assertThat(new ImageContent(urlImage, ImageContent.DetailLevel.LOW))
                .isEqualTo(new ImageContent(urlImage))
                .isEqualTo(ImageContent.from(urlImage, ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(urlImage))
                .isEqualTo(new ImageContent(urlImage.url()))
                .isEqualTo(new ImageContent(urlImage.url().toString()))
                .isEqualTo(new ImageContent(urlImage.url(), ImageContent.DetailLevel.LOW))
                .isEqualTo(new ImageContent(urlImage.url().toString(), ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(urlImage.url()))
                .isEqualTo(ImageContent.from(urlImage.url().toString()))
                .isEqualTo(ImageContent.from(urlImage.url(), ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(urlImage.url().toString(), ImageContent.DetailLevel.LOW));

        Image base64Image = Image.builder()
                .base64Data("ff==")
                .mimeType("mimeType")
                .build();
        assertThat(new ImageContent(base64Image, ImageContent.DetailLevel.LOW))
                .isEqualTo(new ImageContent(base64Image))
                .isEqualTo(ImageContent.from(base64Image, ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(base64Image))
                .isEqualTo(new ImageContent(base64Image.base64Data(), base64Image.mimeType()))
                .isEqualTo(new ImageContent(base64Image.base64Data(), base64Image.mimeType(), ImageContent.DetailLevel.LOW))
                .isEqualTo(ImageContent.from(base64Image.base64Data(), base64Image.mimeType()))
                .isEqualTo(ImageContent.from(base64Image.base64Data(), base64Image.mimeType(), ImageContent.DetailLevel.LOW));
    }
}