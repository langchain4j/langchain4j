package dev.langchain4j.model.anthropic.internal.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnthropicPdfContentSourceTest {

    @Test
    void should_create_from_base64() {
        AnthropicPdfContentSource source = AnthropicPdfContentSource.fromBase64("application/pdf", "base64data");

        assertThat(source.type).isEqualTo("base64");
        assertThat(source.mediaType).isEqualTo("application/pdf");
        assertThat(source.data).isEqualTo("base64data");
        assertThat(source.url).isNull();
    }

    @Test
    void should_create_from_url() {
        AnthropicPdfContentSource source = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");

        assertThat(source.type).isEqualTo("url");
        assertThat(source.mediaType).isNull();
        assertThat(source.data).isNull();
        assertThat(source.url).isEqualTo("https://example.com/doc.pdf");
    }

    @Test
    void should_create_with_three_parameter_constructor() {
        AnthropicPdfContentSource source = new AnthropicPdfContentSource("base64", "application/pdf", "data");

        assertThat(source.type).isEqualTo("base64");
        assertThat(source.mediaType).isEqualTo("application/pdf");
        assertThat(source.data).isEqualTo("data");
        assertThat(source.url).isNull();
    }

    @Test
    void should_have_correct_equals_for_url_sources() {
        AnthropicPdfContentSource source1 = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");
        AnthropicPdfContentSource source2 = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");
        AnthropicPdfContentSource source3 = AnthropicPdfContentSource.fromUrl("https://example.com/other.pdf");

        assertThat(source1).isEqualTo(source2);
        assertThat(source1).isNotEqualTo(source3);
    }

    @Test
    void should_have_correct_equals_for_base64_sources() {
        AnthropicPdfContentSource source1 = AnthropicPdfContentSource.fromBase64("application/pdf", "data");
        AnthropicPdfContentSource source2 = AnthropicPdfContentSource.fromBase64("application/pdf", "data");
        AnthropicPdfContentSource source3 = AnthropicPdfContentSource.fromBase64("application/pdf", "other");

        assertThat(source1).isEqualTo(source2);
        assertThat(source1).isNotEqualTo(source3);
    }

    @Test
    void should_have_correct_equals_for_different_types() {
        AnthropicPdfContentSource urlSource = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");
        AnthropicPdfContentSource base64Source = AnthropicPdfContentSource.fromBase64("application/pdf", "data");

        assertThat(urlSource).isNotEqualTo(base64Source);
    }

    @Test
    void should_have_correct_hashCode() {
        AnthropicPdfContentSource source1 = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");
        AnthropicPdfContentSource source2 = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");

        assertThat(source1.hashCode()).isEqualTo(source2.hashCode());
    }

    @Test
    void should_have_toString() {
        AnthropicPdfContentSource source = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");

        assertThat(source.toString()).contains("url");
        assertThat(source.toString()).contains("https://example.com/doc.pdf");
    }

    @Test
    void should_not_equal_null() {
        AnthropicPdfContentSource source = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");

        assertThat(source).isNotEqualTo(null);
    }

    @Test
    void should_not_equal_different_class() {
        AnthropicPdfContentSource source = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");

        assertThat(source).isNotEqualTo("not a source");
    }

    @Test
    void should_equal_itself() {
        AnthropicPdfContentSource source = AnthropicPdfContentSource.fromUrl("https://example.com/doc.pdf");

        assertThat(source).isEqualTo(source);
    }
}
