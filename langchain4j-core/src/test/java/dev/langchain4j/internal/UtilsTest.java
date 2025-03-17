package dev.langchain4j.internal;

import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.entry;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings({"ObviousNullCheck", "ConstantValue"})
class UtilsTest {
    @Test
    void get_or_default() {
        assertThat(Utils.getOrDefault("foo", "bar")).isEqualTo("foo");
        assertThat(Utils.getOrDefault(null, "bar")).isEqualTo("bar");

        assertThat(Utils.getOrDefault("foo", () -> "bar")).isEqualTo("foo");
        assertThat(Utils.getOrDefault(null, () -> "bar")).isEqualTo("bar");
    }

    @Test
    void get_or_default_list() {
        List<Integer> nullList = null;
        List<Integer> emptyList = List.of();
        List<Integer> list1 = List.of(1);
        List<Integer> list2 = List.of(2);

        assertThat(Utils.getOrDefault(nullList, nullList)).isSameAs(nullList);
        assertThat(Utils.getOrDefault(nullList, emptyList)).isSameAs(emptyList);

        assertThat(Utils.getOrDefault(emptyList, nullList)).isSameAs(nullList);
        assertThat(Utils.getOrDefault(emptyList, emptyList)).isSameAs(emptyList);

        assertThat(Utils.getOrDefault(nullList, list1)).isSameAs(list1);
        assertThat(Utils.getOrDefault(emptyList, list1)).isSameAs(list1);

        assertThat(Utils.getOrDefault(list1, list2)).isSameAs(list1).isNotSameAs(list2);
    }

    @Test
    void get_or_default_map() {
        Map<String, String> nullMap = null;
        Map<String, String> emptyMap = Map.of();
        Map<String, String> map1 = Map.of("one", "1");
        Map<String, String> map2 = Map.of("two", "2");

        assertThat(Utils.getOrDefault(nullMap, nullMap)).isSameAs(nullMap);
        assertThat(Utils.getOrDefault(nullMap, emptyMap)).isSameAs(emptyMap);

        assertThat(Utils.getOrDefault(emptyMap, nullMap)).isSameAs(nullMap);
        assertThat(Utils.getOrDefault(emptyMap, emptyMap)).isSameAs(emptyMap);

        assertThat(Utils.getOrDefault(nullMap, map1)).isSameAs(map1);
        assertThat(Utils.getOrDefault(emptyMap, map1)).isSameAs(map1);

        assertThat(Utils.getOrDefault(map1, map2)).isSameAs(map1).isNotSameAs(map2);
    }

    @Test
    void is_null_or_blank() {
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
    void string_is_null_or_empty() {
        assertThat(Utils.isNullOrEmpty((String) null)).isTrue();
        assertThat(Utils.isNullOrEmpty("")).isTrue();
        assertThat(Utils.isNullOrEmpty(" ")).isFalse();
        assertThat(Utils.isNullOrEmpty("\n")).isFalse();
        assertThat(Utils.isNullOrEmpty("foo")).isFalse();
    }

    @Test
    void string_is_not_null_or_empty() {
        assertThat(Utils.isNotNullOrEmpty(null)).isFalse();
        assertThat(Utils.isNotNullOrEmpty("")).isFalse();
        assertThat(Utils.isNotNullOrEmpty(" ")).isTrue();
        assertThat(Utils.isNotNullOrEmpty("\n")).isTrue();
        assertThat(Utils.isNotNullOrEmpty("foo")).isTrue();
    }

    @Test
    void are_not_null_or_blank() {
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
    void collection_is_null_or_empty() {
        assertThat(Utils.isNullOrEmpty((Collection<?>) null)).isTrue();
        assertThat(Utils.isNullOrEmpty(emptyList())).isTrue();
        assertThat(Utils.isNullOrEmpty(Collections.singletonList("abc"))).isFalse();
    }

    @Test
    void iterable_is_null_or_empty() {
        assertThat(Utils.isNullOrEmpty((Iterable<?>) null)).isTrue();
        assertThat(Utils.isNullOrEmpty((Iterable<?>) emptyList())).isTrue();
        assertThat(Utils.isNullOrEmpty((Iterable<?>) Collections.singletonList("abc")))
                .isFalse();
    }

    @Test
    void repeat() {
        assertThat(Utils.repeat("foo", 0)).isEmpty();
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
        assertThat(UUID.fromString(uuid1)).isInstanceOf(UUID.class);
        assertThat(UUID.fromString(uuid2)).isInstanceOf(UUID.class);
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
        assertThat(UUID.fromString(uuidFromInput1)).isInstanceOf(UUID.class);
        assertThat(UUID.fromString(uuidFromInput2)).isInstanceOf(UUID.class);

        // Test if hashing is consistent for the same input
        assertThat(Utils.generateUUIDFrom(input1)).isEqualTo(uuidFromInput1);
    }

    @Test
    void generateUUIDFromEmptyInputWorks() {
        String uuidFromEmptyInput = Utils.generateUUIDFrom("");

        assertThat(uuidFromEmptyInput).isNotNull().isNotEmpty();

        // Validate if the returned string is in the UUID format
        assertThat(UUID.fromString(uuidFromEmptyInput)).isInstanceOf(UUID.class);
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
        return Stream.of(
                Arguments.of(null, "null"),
                Arguments.of("", "\"\""),
                Arguments.of(" ", "\" \""),
                Arguments.of("hello", "\"hello\""));
    }

    @Test
    void first_chars() {
        assertThat(Utils.firstChars(null, 3)).isNull();
        assertThat(Utils.firstChars("", 3)).isEmpty();
        assertThat(Utils.firstChars("foo", 3)).isEqualTo("foo");
        assertThat(Utils.firstChars("foobar", 3)).isEqualTo("foo");
    }

    @Test
    void read_bytes() throws IOException {
        HttpServer httpServer =
                HttpServer.create(new InetSocketAddress(0), 0); // or use InetSocketAddress(0) for ephemeral port
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
    void copy_if_not_null_list() {
        assertThat(Utils.copyIfNotNull((List<?>) null)).isNull();
        assertThat(Utils.copyIfNotNull(emptyList())).isEmpty();
        assertThat(Utils.copyIfNotNull(singletonList("one"))).containsExactly("one");
        assertThat(Utils.copyIfNotNull(asList("one", "two"))).containsExactly("one", "two");
    }

    @Test
    void copy_if_not_null_map() {
        assertThat(Utils.copyIfNotNull((Map<?, ?>) null)).isNull();
        assertThat(Utils.copyIfNotNull(emptyMap())).isEmpty();
        assertThat(Utils.copyIfNotNull(singletonMap("key", "value"))).containsExactly(entry("key", "value"));
    }

    @Test
    void ensure_trailing_forward_slash() {
        assertThat(Utils.ensureTrailingForwardSlash("https://example.com")).isEqualTo("https://example.com/");
        assertThat(Utils.ensureTrailingForwardSlash("https://example.com/")).isEqualTo("https://example.com/");
        assertThat(Utils.ensureTrailingForwardSlash("https://example.com/a")).isEqualTo("https://example.com/a/");
        assertThat(Utils.ensureTrailingForwardSlash("https://example.com/a/")).isEqualTo("https://example.com/a/");
        assertThat(Utils.ensureTrailingForwardSlash("https://example.com/a/b")).isEqualTo("https://example.com/a/b/");
    }
}
