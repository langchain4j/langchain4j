package dev.langchain4j.model.anthropic.internal.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnthropicPdfContentTest {

    @Test
    void should_create_from_base64() {
        AnthropicPdfContent content = AnthropicPdfContent.fromBase64("application/pdf", "base64data");

        assertThat(content.type).isEqualTo("document");
        assertThat(content.source.type).isEqualTo("base64");
        assertThat(content.source.mediaType).isEqualTo("application/pdf");
        assertThat(content.source.data).isEqualTo("base64data");
        assertThat(content.source.url).isNull();
    }

    @Test
    void should_create_from_url() {
        AnthropicPdfContent content = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");

        assertThat(content.type).isEqualTo("document");
        assertThat(content.source.type).isEqualTo("url");
        assertThat(content.source.url).isEqualTo("https://example.com/doc.pdf");
        assertThat(content.source.mediaType).isNull();
        assertThat(content.source.data).isNull();
    }

    @Test
    void should_create_with_source_constructor() {
        AnthropicPdfContentSource source = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");

        AnthropicPdfContent content = new AnthropicPdfContent(source);

        assertThat(content.type).isEqualTo("document");
        assertThat(content.source).isEqualTo(source);
    }

    @Test
    void should_maintain_backward_compatibility_with_two_parameter_constructor() {
        AnthropicPdfContent content = new AnthropicPdfContent("application/pdf", "base64data");

        assertThat(content.type).isEqualTo("document");
        assertThat(content.source.type).isEqualTo("base64");
        assertThat(content.source.mediaType).isEqualTo("application/pdf");
        assertThat(content.source.data).isEqualTo("base64data");
    }

    @Test
    void should_maintain_backward_compatibility_with_single_parameter_constructor() {
        AnthropicPdfContent content = new AnthropicPdfContent("base64data");

        assertThat(content.type).isEqualTo("document");
        assertThat(content.source.type).isEqualTo("base64");
        assertThat(content.source.mediaType).isEqualTo("application/pdf");
        assertThat(content.source.data).isEqualTo("base64data");
    }

    @Test
    void should_have_correct_equals_for_url_content() {
        AnthropicPdfContent content1 = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");
        AnthropicPdfContent content2 = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");
        AnthropicPdfContent content3 = AnthropicPdfContent.fromUrl("https://example.com/other.pdf");

        assertThat(content1).isEqualTo(content2);
        assertThat(content1).isNotEqualTo(content3);
    }

    @Test
    void should_have_correct_equals_for_base64_content() {
        AnthropicPdfContent content1 = AnthropicPdfContent.fromBase64("application/pdf", "data");
        AnthropicPdfContent content2 = AnthropicPdfContent.fromBase64("application/pdf", "data");
        AnthropicPdfContent content3 = AnthropicPdfContent.fromBase64("application/pdf", "other");

        assertThat(content1).isEqualTo(content2);
        assertThat(content1).isNotEqualTo(content3);
    }

    @Test
    void should_have_correct_equals_for_different_types() {
        AnthropicPdfContent urlContent = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");
        AnthropicPdfContent base64Content = AnthropicPdfContent.fromBase64("application/pdf", "data");

        assertThat(urlContent).isNotEqualTo(base64Content);
    }

    @Test
    void should_have_correct_hashCode() {
        AnthropicPdfContent content1 = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");
        AnthropicPdfContent content2 = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");

        assertThat(content1.hashCode()).isEqualTo(content2.hashCode());
    }

    @Test
    void should_have_toString() {
        AnthropicPdfContent content = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");

        assertThat(content.toString()).contains("source");
    }

    @Test
    void should_not_equal_null() {
        AnthropicPdfContent content = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");

        assertThat(content).isNotEqualTo(null);
    }

    @Test
    void should_not_equal_different_class() {
        AnthropicPdfContent content = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");

        assertThat(content).isNotEqualTo("not a content");
    }

    @Test
    void should_equal_itself() {
        AnthropicPdfContent content = AnthropicPdfContent.fromUrl("https://example.com/doc.pdf");

        assertThat(content).isEqualTo(content);
    }
}
