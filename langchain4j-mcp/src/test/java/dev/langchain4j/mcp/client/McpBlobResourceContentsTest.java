package dev.langchain4j.mcp.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class McpBlobResourceContentsTest {

    @Test
    void blobLongerThanOneLineIsEncodedWithoutLineBreaks() {
        byte[] payload = new byte[58];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) i;
        }

        McpBlobResourceContents contents = McpBlobResourceContents.create("file:///blob", payload);

        assertThat(contents.blob()).doesNotContain("\r").doesNotContain("\n");
        assertThat(Base64.getDecoder().decode(contents.blob())).isEqualTo(payload);
    }

    @Test
    void shortBlobRoundTripsThroughTheStrictDecoder() {
        byte[] payload = {1, 2, 3};

        McpBlobResourceContents contents = McpBlobResourceContents.create("file:///blob", payload);

        assertThat(Base64.getDecoder().decode(contents.blob())).isEqualTo(payload);
    }
}
