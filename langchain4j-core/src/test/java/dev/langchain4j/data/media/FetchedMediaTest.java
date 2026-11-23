package dev.langchain4j.data.media;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FetchedMediaTest {

    @Test
    void should_hold_bytes_and_mime_type() {
        byte[] bytes = "png".getBytes(UTF_8);

        FetchedMedia media = FetchedMedia.from(bytes, "image/png");

        assertThat(media.bytes()).isSameAs(bytes);
        assertThat(media.mimeType()).isEqualTo("image/png");
    }

    @Test
    void should_have_no_mime_type_when_not_known() {
        assertThat(FetchedMedia.from(new byte[1]).mimeType()).isNull();
        assertThat(FetchedMedia.from(new byte[1], null).mimeType()).isNull();
        assertThat(FetchedMedia.from(new byte[1], " ").mimeType()).isNull();
    }

    @Test
    void should_reject_null_bytes() {
        assertThatThrownBy(() -> FetchedMedia.from(null, "image/png"))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bytes");
    }

    @Test
    void should_compare_by_content() {
        FetchedMedia media = FetchedMedia.from("png".getBytes(UTF_8), "image/png");

        assertThat(media)
                .isEqualTo(FetchedMedia.from("png".getBytes(UTF_8), "image/png"))
                .hasSameHashCodeAs(FetchedMedia.from("png".getBytes(UTF_8), "image/png"))
                .isNotEqualTo(FetchedMedia.from("jpg".getBytes(UTF_8), "image/png"))
                .isNotEqualTo(FetchedMedia.from("png".getBytes(UTF_8), "image/jpeg"))
                .isNotEqualTo(FetchedMedia.from("png".getBytes(UTF_8)))
                .isNotEqualTo(null)
                .isNotEqualTo("png");
        assertThat(media).isEqualTo(media);
    }

    @Test
    void should_show_the_size_but_not_the_content() {
        FetchedMedia media = FetchedMedia.from("secret-content".getBytes(UTF_8), "text/plain");

        assertThat(media.toString())
                .isEqualTo("FetchedMedia { sizeInBytes = 14, mimeType = \"text/plain\" }")
                .doesNotContain("secret");
        assertThat(FetchedMedia.from(new byte[0]).toString())
                .isEqualTo("FetchedMedia { sizeInBytes = 0, mimeType = null }");
    }
}
