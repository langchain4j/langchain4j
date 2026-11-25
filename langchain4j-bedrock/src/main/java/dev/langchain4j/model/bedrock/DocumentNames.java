package dev.langchain4j.model.bedrock;

import static dev.langchain4j.model.bedrock.AbstractBedrockChatModel.extractFilenameWithoutExtensionFromUri;

import dev.langchain4j.Internal;
import dev.langchain4j.data.pdf.PdfFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Names the documents of one request. Not thread-safe: an instance is used for one request.
 */
@Internal
final class DocumentNames {

    private final Set<String> names = new HashSet<>();

    /**
     * Returns the name of a PDF file: the file name of its URL without the extension, or, for a PDF file without a URL
     * (for example one created from a file or from base64 data), {@code document-} and the first 12 hex digits of the
     * SHA-256 hash of its content. The same document gets the same name in every request, so that prompt caching keeps
     * working.
     *
     * @param content the content of the PDF file, only read when the PDF file has no URL
     */
    static String nameOf(PdfFile pdfFile, Supplier<byte[]> content) {
        if (pdfFile.url() != null) {
            return extractFilenameWithoutExtensionFromUri(pdfFile.url());
        }
        return "document-" + HexFormat.of().formatHex(sha256(content.get()), 0, 6);
    }

    /**
     * Returns the given name if it is not used yet in this request, otherwise the name with the first suffix
     * ({@code -2}, {@code -3}, and so on) that gives an unused name, so that the names in a request are distinct.
     */
    String distinct(String name) {
        String distinctName = name;
        for (int suffix = 2; !names.add(distinctName); suffix++) {
            distinctName = name + "-" + suffix;
        }
        return distinctName;
    }

    private static byte[] sha256(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // every Java platform supports SHA-256
        }
    }
}
