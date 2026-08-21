package dev.langchain4j.internal;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Utility class for URI operations.
 */
public final class UriUtils {

    private UriUtils() {}

    private static final BitSet ALLOWED = new BitSet(128);
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    static {
        // RFC 3986: unreserved characters
        for (char c = 'a'; c <= 'z'; c++) ALLOWED.set(c);
        for (char c = 'A'; c <= 'Z'; c++) ALLOWED.set(c);
        for (char c = '0'; c <= '9'; c++) ALLOWED.set(c);
        for (char c : new char[] {'-', '.', '_', '~'}) ALLOWED.set(c);
        // gen-delims + sub-delims (keep URL syntax intact)
        for (char c :
                new char[] {':', '/', '?', '#', '[', ']', '@', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '='})
            ALLOWED.set(c);
    }

    /**
     * Checks if a character is a valid hexadecimal digit.
     */
    private static boolean isHex(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F') || (ch >= 'a' && ch <= 'f');
    }

    /**
     * Percent-encodes illegal characters in the given string for use in a URI.
     * <p>
     * Characters that are allowed in URIs according to RFC 3986 (unreserved characters,
     * gen-delims, and sub-delims) are preserved. This includes URL structural characters
     * like {@code :}, {@code /}, {@code ?}, {@code #}, etc.
     * <p>
     * Existing percent-encoded sequences ({@code %XX}) are preserved to avoid double-encoding.
     * <p>
     * Illegal characters (spaces, non-ASCII, pipe {@code |}, etc.) are percent-encoded using UTF-8.
     *
     * @param s the input string to encode
     * @return the percent-encoded string
     */
    private static String percentEncodeIllegal(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        int n = s.length();
        int i = 0;

        while (i < n) {
            int nextI;
            char ch = s.charAt(i);

            // preserve existing %XX sequences
            if (ch == '%' && i + 2 < n && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
                out.append(s, i, i + 3);
                nextI = i + 3;
            } else if (ch < 128 && ALLOWED.get(ch)) {
                out.append(ch);
                nextI = i + 1;
            } else {
                int cp = s.codePointAt(i);
                byte[] bytes = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    int v = b & 0xFF;
                    out.append('%').append(HEX[v >>> 4]).append(HEX[v & 0x0F]);
                }
                nextI = i + Character.charCount(cp);
            }

            i = nextI;
        }
        return out.toString();
    }

    /**
     * Creates a URI from the given string, handling illegal characters safely.
     * <p>
     * This method first attempts to create a URI directly. If that fails due to illegal characters
     * (such as spaces, non-ASCII characters, or characters like {@code |}), it will percent-encode
     * only the illegal characters while preserving URL structural characters ({@code :}, {@code /}, etc.).
     * <p>
     * If the input is null or blank, this method returns null.
     * If the URI cannot be created even after encoding, null is returned.
     *
     * @param uriString the input string to convert to a URI
     * @return a URI object, or null if the input is null, blank, or cannot be parsed
     */
    public static URI createUriSafely(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            return null;
        }

        try {
            return URI.create(uriString);
        } catch (IllegalArgumentException ex) {
            // second attempt with targeted percent-encoding
            String encoded = percentEncodeIllegal(uriString);
            try {
                return URI.create(encoded);
            } catch (IllegalArgumentException ex2) {
                return null;
            }
        }
    }
}
