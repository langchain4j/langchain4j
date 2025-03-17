package dev.langchain4j.data.image;

import java.net.URI;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ImageTest implements WithAssertions {
    @Test
    void builder() throws Exception {
        {
            Image image = Image.builder()
                    .url(new URI("https://example.com/image.png"))
                    .base64Data("base64Data")
                    .mimeType("image/png")
                    .revisedPrompt("revisedPrompt")
                    .build();

            assertThat(image.url().toString()).isEqualTo("https://example.com/image.png");
            assertThat(image.base64Data()).isEqualTo("base64Data");
            assertThat(image.mimeType()).isEqualTo("image/png");
            assertThat(image.revisedPrompt()).isEqualTo("revisedPrompt");
        }
        {
            Image image = Image.builder().build();
            assertThat(image.url()).isNull();
            assertThat(image.base64Data()).isNull();
            assertThat(image.mimeType()).isNull();
            assertThat(image.revisedPrompt()).isNull();
        }
        {
            Image image = Image.builder().url("https://example.com/image.png").build();
            assertThat(image.url()).isEqualTo(new URI("https://example.com/image.png"));
        }
    }

    @Test
    void to_string() {
        Image image = Image.builder()
                .url(URI.create("https://example.com/image.png"))
                .base64Data("base64Data")
                .mimeType("image/png")
                .revisedPrompt("revisedPrompt")
                .build();

        assertThat(image)
                .hasToString(
                        "Image { url = \"https://example.com/image.png\", base64Data = \"base64Data\", mimeType = \"image/png\", revisedPrompt = \"revisedPrompt\" }");
    }

    @Test
    void equals_hash() {
        Image image1 = Image.builder()
                .url(URI.create("https://example.com/image.png"))
                .base64Data("base64Data")
                .mimeType("image/png")
                .revisedPrompt("revisedPrompt")
                .build();
        Image image2 = Image.builder()
                .url(URI.create("https://example.com/image.png"))
                .base64Data("base64Data")
                .mimeType("image/png")
                .revisedPrompt("revisedPrompt")
                .build();

        assertThat(image1)
                .isEqualTo(image1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(image2)
                .hasSameHashCodeAs(image2);

        assertThat(Image.builder()
                        .url(URI.create("https://change"))
                        .base64Data("base64Data")
                        .mimeType("image/png")
                        .revisedPrompt("revisedPrompt")
                        .build())
                .isNotEqualTo(image1)
                .doesNotHaveSameHashCodeAs(image1);

        assertThat(Image.builder()
                        .url(URI.create("https://example.com/image.png"))
                        .base64Data("changed")
                        .mimeType("image/png")
                        .revisedPrompt("revisedPrompt")
                        .build())
                .isNotEqualTo(image1)
                .doesNotHaveSameHashCodeAs(image1);

        assertThat(Image.builder()
                        .url(URI.create("https://example.com/image.png"))
                        .base64Data("base64Data")
                        .mimeType("changed")
                        .revisedPrompt("revisedPrompt")
                        .build())
                .isNotEqualTo(image1)
                .doesNotHaveSameHashCodeAs(image1);

        assertThat(Image.builder()
                        .url(URI.create("https://example.com/image.png"))
                        .base64Data("base64Data")
                        .mimeType("image/png")
                        .revisedPrompt("changed")
                        .build())
                .isNotEqualTo(image1)
                .doesNotHaveSameHashCodeAs(image1);
    }
}
