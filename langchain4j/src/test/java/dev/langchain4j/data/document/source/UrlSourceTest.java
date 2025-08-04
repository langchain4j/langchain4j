package dev.langchain4j.data.document.source;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.lang.reflect.Field;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class UrlSourceTest {

    private static final String VALID_URL =
            "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";
    /**
     * An intentionally unreachable URL used to simulate network timeouts or connection failures.
     * <p>
     * The IP address {@code 10.255.255.1} is part of the private IP range (10.0.0.0/8)
     * and is typically non-routable on public networks.
     * Port {@code 81} is a non-standard port that is usually closed, increasing the likelihood
     * that the connection attempt will hang or fail quickly.
     * <p>
     * This makes it ideal for testing timeout behavior or error handling in a reliable and predictable way.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc1918">RFC 1918 - Address Allocation for Private Internets</a>
     */
    private static final String INVALID_URL = "http://10.255.255.1:81";

    @Test
    void should_create_url_source_from_valid_url_and_read_content() throws IOException {
        UrlSource source = UrlSource.from(VALID_URL);

        assertNotNull(source);
        assertEquals(VALID_URL, source.metadata().getString("url"));

        try (InputStream is = source.inputStream()) {
            assertNotNull(is);
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("test\ncontent", content.trim());
        }
    }

    @Test
    void should_throw_exception_when_invalid_url_is_provided() {
        assertThrows(RuntimeException.class, () -> UrlSource.from("ht@tp:// bad-url"));
    }

    @Test
    void should_fail_to_connect_due_to_timeout() {
        UrlSource source = new UrlSource(createUrl(INVALID_URL), 1000, 1000); // 1s timeout

        IOException ex = assertThrows(IOException.class, source::inputStream);
        assertTrue(ex.getMessage().contains("connect") || ex.getMessage().contains("timed out"));
    }

    @Test
    void should_apply_default_timeouts_when_using_standard_constructor() throws Exception {
        URL url = createUrl(VALID_URL);
        UrlSource source = new UrlSource(url);

        assertEquals(0, getField(source, "connectTimeoutMillis"));
        assertEquals(0, getField(source, "readTimeoutMillis"));
    }

    @Test
    void should_apply_custom_timeouts_correctly() throws Exception {
        URL url = createUrl(VALID_URL);
        UrlSource source = new UrlSource(url, 2345, 6789);

        assertEquals(2345, getField(source, "connectTimeoutMillis"));
        assertEquals(6789, getField(source, "readTimeoutMillis"));
    }

    private int getField(UrlSource source, String fieldName) throws Exception {
        Field field = UrlSource.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(source);
    }

    private URL createUrl(String url) {
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid test URL: " + url, e);
        }
    }
}
