package dev.langchain4j.mcp.client.transport;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class McpHeaderEncodingTest {

    @Test
    void asciiValuePassesThrough() {
        assertThat(McpHeaderEncoding.encode("myTool")).isEqualTo("myTool");
    }

    @Test
    void emptyValuePassesThrough() {
        assertThat(McpHeaderEncoding.encode("")).isEqualTo("");
    }

    @Test
    void resourceUriWithAsciiPassesThrough() {
        assertThat(McpHeaderEncoding.encode("file:///project/main.rs")).isEqualTo("file:///project/main.rs");
    }

    @Test
    void nonAsciiIsBase64Encoded() {
        String value = "file:///文档.json";
        String encoded = McpHeaderEncoding.encode(value);
        assertThat(encoded).startsWith("=?base64?").endsWith("?=");
        String decoded = new String(
                Base64.getDecoder().decode(encoded.substring(9, encoded.length() - 2)), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(value);
    }

    @Test
    void leadingSpaceIsEncoded() {
        String encoded = McpHeaderEncoding.encode(" leading");
        assertThat(encoded).startsWith("=?base64?");
    }

    @Test
    void trailingSpaceIsEncoded() {
        String encoded = McpHeaderEncoding.encode("trailing ");
        assertThat(encoded).startsWith("=?base64?");
    }

    @Test
    void leadingTabIsEncoded() {
        String encoded = McpHeaderEncoding.encode("\tleading");
        assertThat(encoded).startsWith("=?base64?");
    }

    @Test
    void sentinelLookalikeIsEncoded() {
        String encoded = McpHeaderEncoding.encode("=?base64?something?=");
        assertThat(encoded).startsWith("=?base64?");
        String decoded = new String(
                Base64.getDecoder().decode(encoded.substring(9, encoded.length() - 2)), StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo("=?base64?something?=");
    }

    @Test
    void controlCharacterIsEncoded() {
        String encoded = McpHeaderEncoding.encode("hascontrol");
        assertThat(encoded).startsWith("=?base64?");
    }
}
