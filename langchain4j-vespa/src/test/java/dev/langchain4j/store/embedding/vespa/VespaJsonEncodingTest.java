package dev.langchain4j.store.embedding.vespa;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The feed payload has to reach Vespa as UTF-8 whatever the JVM's default charset is, so the
 * bytes are asserted directly rather than through a {@code String} round-trip.
 */
class VespaJsonEncodingTest {

    private static final String NON_ASCII_TEXT = "café 한글 🚀";

    private static Record record() {
        return new Record(
                "id-1",
                null,
                new Record.Fields("id::doc::1", NON_ASCII_TEXT, new Record.Fields.Vector(List.of(0.1f, 0.2f))));
    }

    @Test
    void feed_payload_is_encoded_as_utf8() throws Exception {
        ByteArrayInputStream payload = VespaEmbeddingStore.toJsonStream(List.of(record()));

        assertThat(new String(payload.readAllBytes(), UTF_8)).contains(NON_ASCII_TEXT);
    }

    @Test
    void feed_payload_bytes_match_utf8_encoding_of_the_text() throws Exception {
        ByteArrayInputStream payload = VespaEmbeddingStore.toJsonStream(List.of(record()));

        assertThat(payload.readAllBytes()).contains(toBytes(NON_ASCII_TEXT));
    }

    private static byte[] toBytes(String text) {
        return text.getBytes(UTF_8);
    }
}
