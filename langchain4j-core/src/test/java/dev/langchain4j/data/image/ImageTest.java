package dev.langchain4j.data.image;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

class ImageTest implements WithAssertions {
    @Test
    public void testBuilder() throws Exception{
        {
            Image image = Image.builder()
                    .url(new URI("https://example.com/image.png"))
                    .base64Data("base64Data")
                    .revisedPrompt("revisedPrompt")
                    .build();

            assertThat(image.url().toString()).isEqualTo("https://example.com/image.png");
            assertThat(image.base64Data()).isEqualTo("base64Data");
            assertThat(image.revisedPrompt()).isEqualTo("revisedPrompt");
        }
        {
            Image image = Image.builder().build();
            assertThat(image.url()).isNull();
            assertThat(image.base64Data()).isNull();
            assertThat(image.revisedPrompt()).isNull();
        }
    }

    @Test
    public void test_toString() {
        Image image = Image.builder()
                .url(URI.create("https://example.com/image.png"))
                .base64Data("base64Data")
                .revisedPrompt("revisedPrompt")
                .build();

        assertThat(image)
                .hasToString(
                        "Image { url = \"https://example.com/image.png\", base64Data = \"base64Data\", mimeType = null, revisedPrompt = \"revisedPrompt\" }");
    }

    @Test
    public void test_equals_hash() {
        Image image1 = Image.builder()
                .url(URI.create("https://example.com/image.png"))
                .base64Data("base64Data")
                .revisedPrompt("revisedPrompt")
                .build();
        Image image2 = Image.builder()
                .url(URI.create("https://example.com/image.png"))
                .base64Data("base64Data")
                .revisedPrompt("revisedPrompt")
                .build();

        assertThat(image1)
                .isEqualTo(image1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(image2)
                .hasSameHashCodeAs(image2);

        assertThat(
                Image.builder()
                        .url(URI.create("https://change"))
                        .base64Data("base64Data")
                        .revisedPrompt("revisedPrompt")
                        .build())
                .isNotEqualTo(image1)
                .doesNotHaveSameHashCodeAs(image1);

        assertThat(
                Image.builder()
                        .url(URI.create("https://example.com/image.png"))
                        .base64Data("changed")
                        .revisedPrompt("revisedPrompt")
                        .build())
                .isNotEqualTo(image1)
                .doesNotHaveSameHashCodeAs(image1);

        assertThat(
                Image.builder()
                        .url(URI.create("https://example.com/image.png"))
                        .base64Data("base64Data")
                        .revisedPrompt("changed")
                        .build())
                .isNotEqualTo(image1)
                .doesNotHaveSameHashCodeAs(image1);
    }

}