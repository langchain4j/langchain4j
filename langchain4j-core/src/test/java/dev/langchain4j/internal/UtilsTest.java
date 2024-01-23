package dev.langchain4j.internal;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Stream;

import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@SuppressWarnings({"ObviousNullCheck", "ConstantValue"})
class UtilsTest {
    @Test
    public void test_getOrDefault() {
        assertThat(Utils.getOrDefault("foo", "bar")).isEqualTo("foo");
        assertThat(Utils.getOrDefault(null, "bar")).isEqualTo("bar");

        assertThat(Utils.getOrDefault("foo", () -> "bar")).isEqualTo("foo");
        assertThat(Utils.getOrDefault(null, () -> "bar")).isEqualTo("bar");
    }

    @Test
    public void test_isNullOrBlank() {
        assertThat(Utils.isNullOrBlank(null)).isTrue();
        assertThat(Utils.isNullOrBlank("")).isTrue();
        assertThat(Utils.isNullOrBlank(" ")).isTrue();
        assertThat(Utils.isNullOrBlank("foo")).isFalse();

        assertThat(Utils.isNotNullOrBlank(null)).isFalse();
        assertThat(Utils.isNotNullOrBlank("")).isFalse();
        assertThat(Utils.isNotNullOrBlank(" ")).isFalse();
        assertThat(Utils.isNotNullOrBlank("foo")).isTrue();
    }

    @Test
    public void test_areNotNullOrBlank() {
        assertThat(Utils.areNotNullOrBlank()).isFalse();
        assertThat(Utils.areNotNullOrBlank((String) null)).isFalse();
        assertThat(Utils.areNotNullOrBlank("")).isFalse();
        assertThat(Utils.areNotNullOrBlank(" ")).isFalse();
        assertThat(Utils.areNotNullOrBlank("foo")).isTrue();
        assertThat(Utils.areNotNullOrBlank("foo", "bar")).isTrue();
        assertThat(Utils.areNotNullOrBlank("foo", null)).isFalse();
        assertThat(Utils.areNotNullOrBlank(null, "bar")).isFalse();
        assertThat(Utils.areNotNullOrBlank(null, null)).isFalse();
    }

    @Test
    public void test_isNullOrEmpty() {
        assertThat(Utils.isNullOrEmpty(null)).isTrue();
        assertThat(Utils.isNullOrEmpty(emptyList())).isTrue();
        assertThat(Utils.isNullOrEmpty(Collections.singletonList("abc"))).isFalse();
    }

    @Test
    @SuppressWarnings("deprecation")
    public void test_isCollectionEmpty() {
        assertThat(Utils.isCollectionEmpty(null)).isTrue();
        assertThat(Utils.isCollectionEmpty(emptyList())).isTrue();
        assertThat(Utils.isCollectionEmpty(Collections.singletonList("abc"))).isFalse();
    }

    @Test
    public void test_repeat() {
        assertThat(Utils.repeat("foo", 0)).isEqualTo("");
        assertThat(Utils.repeat("foo", 1)).isEqualTo("foo");
        assertThat(Utils.repeat("foo", 2)).isEqualTo("foofoo");
        assertThat(Utils.repeat("foo", 3)).isEqualTo("foofoofoo");
    }

    @Test
    void randomUUIDWorks() {
        String uuid1 = Utils.randomUUID();
        String uuid2 = Utils.randomUUID();

        assertThat(uuid1).isNotNull().isNotEmpty();
        assertThat(uuid2).isNotNull().isNotEmpty();

        // Checking if the two generated UUIDs are not the same
        assertThat(uuid1).isNotEqualTo(uuid2);

        // Validate if the returned string is in the UUID format
        assertThat(UUID.fromString(uuid1))
                .isInstanceOf(UUID.class);
        assertThat(UUID.fromString(uuid2))
                .isInstanceOf(UUID.class);
    }

    @Test
    void generateUUIDFromTextWorks() {
        String input1 = "Hello";
        String input2 = "World";

        String uuidFromInput1 = Utils.generateUUIDFrom(input1);
        String uuidFromInput2 = Utils.generateUUIDFrom(input2);

        assertThat(uuidFromInput1).isNotNull().isNotEmpty();
        assertThat(uuidFromInput2).isNotNull().isNotEmpty();

        // Different inputs should produce different UUIDs
        assertThat(uuidFromInput1).isNotEqualTo(uuidFromInput2);

        // Validate if the returned string is in the UUID format
        assertThat(UUID.fromString(uuidFromInput1))
                .isInstanceOf(UUID.class);
        assertThat(UUID.fromString(uuidFromInput2))
                .isInstanceOf(UUID.class);

        // Test if hashing is consistent for the same input
        assertThat(Utils.generateUUIDFrom(input1)).isEqualTo(uuidFromInput1);
    }

    @Test
    void generateUUIDFromEmptyInputWorks() {
        String uuidFromEmptyInput = Utils.generateUUIDFrom("");

        assertThat(uuidFromEmptyInput).isNotNull().isNotEmpty();

        // Validate if the returned string is in the UUID format
        assertThat(UUID.fromString(uuidFromEmptyInput))
                .isInstanceOf(UUID.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void generateUUIDFromNullInputWorks() {
        assertThatExceptionOfType(NullPointerException.class).isThrownBy(() -> Utils.generateUUIDFrom(null));
    }

    @MethodSource
    @ParameterizedTest
    void test_quoted(String string, String expected) {
        assertThat(quoted(string)).isEqualTo(expected);
    }

    static Stream<Arguments> test_quoted() {
        return Stream.of(Arguments.of(null, "null"), Arguments.of("", "\"\""), Arguments.of(" ", "\" \""), Arguments.of("hello", "\"hello\""));
    }

    @Test
    public void test_firstChars() {
        assertThat(Utils.firstChars(null, 3)).isNull();
        assertThat(Utils.firstChars("", 3)).isEmpty();
        assertThat(Utils.firstChars("foo", 3)).isEqualTo("foo");
        assertThat(Utils.firstChars("foobar", 3)).isEqualTo("foo");
    }

    @Test
    public void test_readBytes() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(0), 0); // or use InetSocketAddress(0) for ephemeral port
        try {
            int port = httpServer.getAddress().getPort();
            httpServer.createContext("/ok_endpoint", exchange -> {
                byte[] response = "hello".getBytes();
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            httpServer.createContext("/error_endpoint", exchange -> {
                byte[] response = "nope".getBytes();
                exchange.sendResponseHeaders(HttpURLConnection.HTTP_INTERNAL_ERROR, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            httpServer.start();

            assertThat(Utils.readBytes("http://localhost:" + port + "/ok_endpoint"))
                    .isEqualTo("hello".getBytes());

            assertThatExceptionOfType(RuntimeException.class)
                    .isThrownBy(() -> Utils.readBytes("http://localhost:" + port + "/error_endpoint"))
                    .withMessageContaining("Error while reading: 500");

        } finally {
            httpServer.stop(0);
        }
    }

    @Test
    void test_copyIfNotNull() {
        assertThat(Utils.copyIfNotNull(null)).isNull();
        assertThat(Utils.copyIfNotNull(emptyList())).isEmpty();
        assertThat(Utils.copyIfNotNull(singletonList("one"))).containsExactly("one");
        assertThat(Utils.copyIfNotNull(asList("one", "two"))).containsExactly("one", "two");
    }
}
