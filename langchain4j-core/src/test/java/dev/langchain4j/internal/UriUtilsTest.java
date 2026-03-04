package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UriUtilsTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n", " \t \n "})
    void createUriSafely_withNullEmptyOrBlankString_returnsNull(String input) {
        URI result = UriUtils.createUriSafely(input);

        assertThat(result).isNull();
    }

    @Test
    void createUriSafely_withValidHttpsUri_returnsCorrectUri() {
        String validUri = "https://example.com/path?query=value";

        URI result = UriUtils.createUriSafely(validUri);

        assertThat(result).hasToString(validUri);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.com",
                "http://example.org/path",
                "https://example.com:8080/path?param=value",
                "ftp://files.example.com/file.txt",
                "mailto:user@example.com",
                "file:///path/to/file.txt"
            })
    void createUriSafely_withValidUris_returnsCorrectUris(String validUri) {
        URI result = UriUtils.createUriSafely(validUri);

        assertThat(result).hasToString(validUri);
    }

    @Test
    void createUriSafely_withAlreadyEncodedUri_preservesEncoding() {
        String encodedUri = "https://example.com/search?q=hello%20world";

        URI result = UriUtils.createUriSafely(encodedUri);

        assertThat(result).hasToString(encodedUri);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://example.com/path with spaces/file.html",
                "https://example.com/search?q=hello world&lang=en",
                "https://example.com/path{braces}/file.html",
                "https://example.com/path|pipe/file.html",
                "https://example.com/path\\backslash/file.html",
                "https://example.com/path^caret/file.html",
                "https://example.com/path`backtick/file.html",
                "https://example.com/path<less>/file.html",
                "https://example.com/path>greater/file.html",
                "https://example.com/path\"quote/file.html"
            })
    void createUriSafely_withUrlsContainingInvalidCharacters_encodesCorrectly(String urlWithInvalidChars) {
        URI result = UriUtils.createUriSafely(urlWithInvalidChars);

        assertThat(result.toString()).isNotEqualTo(urlWithInvalidChars);
        assertThat(result.toString()).contains("%");
        assertThat(result.toString()).startsWith("https://example.com");
    }

    @Test
    void createUriSafely_withSpaceInUri_encodesSpace() {
        String uriWithSpace = "https://example.com/search?q=hello world";

        URI result = UriUtils.createUriSafely(uriWithSpace);

        assertThat(result.toString()).contains("%20");
        assertThat(result.toString()).doesNotContain(" ");
    }

    @Test
    void createUriSafely_withMixedEncodedAndUnencoded_handlesCorrectly() {
        String mixedUri = "https://example.com/search?q=hello%20world and more";

        URI result = UriUtils.createUriSafely(mixedUri);

        assertThat(result.toString()).contains("%20world"); // preserves existing encoding
        assertThat(result.toString()).contains("%20and%20more"); // encodes new spaces
    }

    @Test
    void createUriSafely_withReservedCharacters_preservesReservedChars() {
        String uriWithReserved = "https://example.com:8080/path?param=value&other=test#fragment";

        URI result = UriUtils.createUriSafely(uriWithReserved);

        assertThat(result).hasToString(uriWithReserved);
    }

    @Test
    void createUriSafely_withUnreservedCharacters_preservesUnreservedChars() {
        String uriWithUnreserved = "https://test.example.com/path_with-dots.html";

        URI result = UriUtils.createUriSafely(uriWithUnreserved);

        assertThat(result).hasToString(uriWithUnreserved);
    }

    @Test
    void createUriSafely_withSeverelyInvalidUri_returnsNull() {
        String invalidUri = "ht[tp://example.com with spaces and [brackets] and {braces}";

        URI result = UriUtils.createUriSafely(invalidUri);

        assertThat(result).isNull();
    }

    @Test
    void createUriSafely_withComplexQuery_handlesCorrectly() {
        String complexQuery = "https://example.com/search?q=java uri encoding test&ie=utf-8";

        URI result = UriUtils.createUriSafely(complexQuery);

        assertThat(result.toString()).contains("%20");
        assertThat(result.toString()).doesNotContain(" ");
    }

    @Test
    void createUriSafely_withPathContainingSpaces_encodesSpaces() {
        String pathWithSpaces = "https://example.com/my documents/file.txt";

        URI result = UriUtils.createUriSafely(pathWithSpaces);

        assertThat(result.toString()).contains("%20");
        assertThat(result.toString()).doesNotContain(" ");
    }

    @Test
    void createUriSafely_withMultipleConsecutiveSpaces_encodesAllSpaces() {
        String uriWithMultipleSpaces = "https://example.com/path   with   spaces";

        URI result = UriUtils.createUriSafely(uriWithMultipleSpaces);

        assertThat(result.toString()).contains("%20%20%20");
        assertThat(result.toString()).doesNotContain(" ");
    }

    @Test
    void createUriSafely_withTabsAndNewlines_encodesCorrectly() {
        String uriWithWhitespace = "https://example.com/path\twith\ttabs\nand\nnewlines";

        URI result = UriUtils.createUriSafely(uriWithWhitespace);

        assertThat(result.toString()).contains("%09"); // tab
        assertThat(result.toString()).contains("%0A"); // newline
        assertThat(result.toString()).doesNotContain("\t");
        assertThat(result.toString()).doesNotContain("\n");
    }

    @Test
    void createUriSafely_withMultiplePercentEncodedSequences_preservesAll() {
        String encodedUri = "https://example.com/search?q=hello%20world%21%40%23";

        URI result = UriUtils.createUriSafely(encodedUri);

        assertThat(result).hasToString(encodedUri);
    }

    @Test
    void createUriSafely_withPartiallyInvalidPercentEncoding_handlesCorrectly() {
        String partiallyInvalidUri = "https://example.com/search?q=hello%20world%ZZ test";

        URI result = UriUtils.createUriSafely(partiallyInvalidUri);

        assertThat(result.toString()).contains("%20world"); // preserves valid encoding
        assertThat(result.toString()).contains("%25ZZ"); // encodes invalid % sequence
        assertThat(result.toString()).contains("%20test"); // encodes space
    }
}
