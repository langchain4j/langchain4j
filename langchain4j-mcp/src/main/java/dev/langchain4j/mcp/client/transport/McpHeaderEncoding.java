package dev.langchain4j.mcp.client.transport;

import dev.langchain4j.Internal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes MCP header values per the spec's Value Encoding rule.
 * Values outside the printable ASCII range (0x20–0x7E), with leading/trailing
 * whitespace, or that look like the encoding sentinel are wrapped as
 * {@code =?base64?{base64}?=}.
 */
@Internal
public class McpHeaderEncoding {

    private McpHeaderEncoding() {}

    public static String encode(String value) {
        if (value.isEmpty()) {
            return value;
        }
        boolean needsEncoding = false;
        if (value.charAt(0) == ' '
                || value.charAt(0) == '\t'
                || value.charAt(value.length() - 1) == ' '
                || value.charAt(value.length() - 1) == '\t') {
            needsEncoding = true;
        }
        if (!needsEncoding) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if (c < 0x20 || c > 0x7E) {
                    needsEncoding = true;
                    break;
                }
            }
        }
        if (!needsEncoding && value.startsWith("=?base64?") && value.endsWith("?=")) {
            needsEncoding = true;
        }
        if (needsEncoding) {
            return "=?base64?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
        }
        return value;
    }
}
