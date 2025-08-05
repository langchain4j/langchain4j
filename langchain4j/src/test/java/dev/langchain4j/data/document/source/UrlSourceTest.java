package dev.langchain4j.data.document.source;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class UrlSourceTest {

    private static final String VALID_URL =
            "https://raw.githubusercontent.com/langchain4j/langchain4j/main/langchain4j/src/test/resources/test-file-utf8.txt";

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
    void should_fail_to_connect_due_to_timeout() throws MalformedURLException {
        WireMockServer wireMockServer =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();

        try {
            int port = wireMockServer.port();
            configureFor("localhost", port);

            stubFor(get(urlEqualTo("/slow"))
                    .willReturn(aResponse()
                            .withFixedDelay(3000) // 3 seconds delay
                            .withBody("Delayed response")));

            String slowUrl = String.format("http://localhost:%d/slow", port);
            UrlSource source = new UrlSource(new URL(slowUrl), 1000, 1000); // 1s timeout

            IOException ex = assertThrows(IOException.class, source::inputStream);
            assertTrue(
                    ex.getMessage().contains("timed out")
                            || ex.getMessage().toLowerCase().contains("connect"),
                    "Expected timeout or connection error, but got: " + ex.getMessage());

        } finally {
            wireMockServer.stop();
        }
    }

    @Test
    void should_apply_default_timeouts_when_using_standard_constructor() throws Exception {
        URL url = createUrl(VALID_URL);
        UrlSource source = new UrlSource(url);

        assertThat(getField(source, "connectTimeoutMillis")).isZero();
        assertThat(getField(source, "readTimeoutMillis")).isZero();
    }

    @Test
    void should_apply_custom_timeouts_correctly() throws Exception {
        URL url = createUrl(VALID_URL);
        UrlSource source = new UrlSource(url, 2345, 6789);

        assertEquals(2345, getField(source, "connectTimeoutMillis"));
        assertEquals(6789, getField(source, "readTimeoutMillis"));
    }

    @Test
    void should_create_url_source_from_string_with_timeouts() throws Exception {
        UrlSource source = UrlSource.from(VALID_URL, 1500, 2500);

        assertNotNull(source);
        assertEquals(VALID_URL, source.metadata().getString("url"));
        assertEquals(1500, getField(source, "connectTimeoutMillis"));
        assertEquals(2500, getField(source, "readTimeoutMillis"));
    }

    @Test
    void should_create_url_source_from_url_with_timeouts() throws Exception {
        URL url = createUrl(VALID_URL);
        UrlSource source = UrlSource.from(url, 3000, 4000);

        assertNotNull(source);
        assertEquals(VALID_URL, source.metadata().getString("url"));
        assertEquals(3000, getField(source, "connectTimeoutMillis"));
        assertEquals(4000, getField(source, "readTimeoutMillis"));
    }

    @Test
    void should_create_url_source_from_uri_with_timeouts() throws Exception {
        URI uri = URI.create(VALID_URL);
        UrlSource source = UrlSource.from(uri, 5000, 6000);

        assertNotNull(source);
        assertEquals(VALID_URL, source.metadata().getString("url"));
        assertEquals(5000, getField(source, "connectTimeoutMillis"));
        assertEquals(6000, getField(source, "readTimeoutMillis"));
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
