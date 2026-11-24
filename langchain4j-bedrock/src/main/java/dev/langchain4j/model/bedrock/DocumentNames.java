package dev.langchain4j.model.bedrock;

import dev.langchain4j.Internal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * Names the documents of one request. Not thread-safe: an instance is used for one request.
 */
@Internal
final class DocumentNames {

    private final Map<String, Integer> counts = new HashMap<>();

    /**
     * Returns the name of a document that has no URL, for example one created from a file or from base64 data:
     * {@code document-} and the first 12 hex digits of the SHA-256 hash of its content. The same document gets the
     * same name in every request, so that prompt caching keeps working.
     */
    static String nameOf(byte[] content) {
        return "document-" + HexFormat.of().formatHex(sha256(content)).substring(0, 12);
    }

    /**
     * Returns the given name the first time, and the name with the suffix {@code -2}, then {@code -3}, and so on when
     * it appears again in this request, so that the names in a request are distinct.
     */
    String distinct(String name) {
        int count = counts.merge(name, 1, Integer::sum);
        return count == 1 ? name : name + "-" + count;
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java platform supports SHA-256
        }
    }
}
