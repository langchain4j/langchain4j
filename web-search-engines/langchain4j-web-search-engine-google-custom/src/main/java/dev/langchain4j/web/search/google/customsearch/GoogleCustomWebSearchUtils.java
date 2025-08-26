package dev.langchain4j.web.search.google.customsearch;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Utility class for Google Custom Web Search operations.
 */
final class GoogleCustomWebSearchUtils {

    private GoogleCustomWebSearchUtils() {}

    private static final BitSet ALLOWED = new BitSet(128);
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    static {
        // RFC 3986: unreserved
        for (char c = 'a'; c <= 'z'; c++) ALLOWED.set(c);
        for (char c = 'A'; c <= 'Z'; c++) ALLOWED.set(c);
        for (char c = '0'; c <= '9'; c++) ALLOWED.set(c);
        for (char c : new char[] {'-', '.', '_', '~'}) ALLOWED.set(c);
        // gen-delims + sub-delims (keep URL syntax intact)
        for (char c :
                new char[] {':', '/', '?', '#', '[', ']', '@', '!', '$', '&', '\'', '(', ')', '*', '+', ',', ';', '='})
            ALLOWED.set(c);
    }

    // Checks if a character is a valid hexadecimal digit
    private static boolean isHex(char ch) {
        return (ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F') || (ch >= 'a' && ch <= 'f');
    }

    /**
     * Percent-encodes illegal characters in the given string for use in a URI.
     * Existing percent-encoded sequences (%XX) are preserved.
     *
     * @param s the input string to encode
     * @return the percent-encoded string
     */
    private static String percentEncodeIllegal(String s) {
        StringBuilder out = new StringBuilder(s.length() + 16);
        int n = s.length();
        int i = 0;

        while (i < n) {
            int nextI; // compute the next position once per iteration

            char ch = s.charAt(i);

            // preserve existing %XX
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

            i = nextI; // single assignment in one place
        }
        return out.toString();
    }

    /**
     * Creates a URI from the given string, returning null if the input is null or blank.
     * If the input string is not a valid URI, it attempts to percent-encode illegal characters
     * and create the URI again. If it still fails, it returns an empty URI.
     *
     * @param uriString the input string to convert to a URI
     * @return a URI object, or null if the input is null or blank, or an empty URI if invalid
     */
    public static URI createUriSafely(String uriString) {
        if (uriString == null || uriString.isBlank()) return null;

        try {
            return URI.create(uriString);
        } catch (IllegalArgumentException ex) {
            // second attempt with targeted percent-encoding
            String encoded = percentEncodeIllegal(uriString);
            try {
                return URI.create(encoded);
            } catch (IllegalArgumentException ex2) {
                return URI.create("");
            }
        }
    }
}
