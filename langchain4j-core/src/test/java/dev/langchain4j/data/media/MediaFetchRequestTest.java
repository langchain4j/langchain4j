package dev.langchain4j.data.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

class MediaFetchRequestTest {

    private static final URI CAT = URI.create("https://example.com/cat.png");

    @Test
    void should_hold_the_url() {
        assertThat(MediaFetchRequest.from(CAT).url()).isSameAs(CAT);
    }

    @Test
    void should_reject_a_null_url() {
        assertThatThrownBy(() -> MediaFetchRequest.from(null))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("url cannot be null");
    }

    @Test
    void should_compare_by_url() {
        MediaFetchRequest request = MediaFetchRequest.from(CAT);

        assertThat(request)
                .isEqualTo(request)
                .isEqualTo(MediaFetchRequest.from(URI.create("https://example.com/cat.png")))
                .hasSameHashCodeAs(MediaFetchRequest.from(URI.create("https://example.com/cat.png")))
                .isNotEqualTo(MediaFetchRequest.from(URI.create("https://example.com/dog.png")))
                .isNotEqualTo(null)
                .isNotEqualTo(CAT);
    }

    @Test
    void should_show_only_scheme_and_host_of_the_url() {
        assertThat(MediaFetchRequest.from(CAT)).hasToString("MediaFetchRequest { url = \"https://example.com/...\" }");
        assertThat(MediaFetchRequest.from(URI.create("https://user:secret@example.com:8443/a/b.png?X-Token=abc")))
                .hasToString("MediaFetchRequest { url = \"https://example.com:8443/...\" }");
        assertThat(MediaFetchRequest.from(URI.create("data:image/png;base64,AAAA")))
                .hasToString("MediaFetchRequest { url = \"data:...\" }");
    }
}
