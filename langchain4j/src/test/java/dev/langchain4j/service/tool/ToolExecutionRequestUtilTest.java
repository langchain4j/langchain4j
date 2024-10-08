package dev.langchain4j.service.tool;

import com.google.gson.internal.LinkedTreeMap;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ToolExecutionRequestUtilTest implements WithAssertions {

    @Test
    public void test_argument() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("{\"foo\":\"bar\", \"qux\": 12}")
                .build();

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments()))
                .containsEntry("foo", "bar")
                .containsEntry("qux", 12.0);
    }

    @Test
    public void test_argument_comma() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("{\"foo\":\"bar\", \"qux\": 12,}")
                .build();

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments()))
                .containsEntry("foo", "bar")
                .containsEntry("qux", 12.0);
    }

    @Test
    public void test_argument_comma_array() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments(
                        "{\"key\":[{\"foo\":\"bar\", \"qux\": 12,},{\"foo\":\"bar\", \"qux\": 12,},]}")
                .build();

        Map<String, Object> argumentsAsMap = ToolExecutionRequestUtil.argumentsAsMap(request.arguments());
        assertThat(argumentsAsMap).containsOnlyKeys("key");

        List<Object> keys = (List<Object>) argumentsAsMap.get("key");
        keys.forEach(key -> {
            assertInstanceOf(LinkedTreeMap.class, key);
            assertTrue(((LinkedTreeMap<?, ?>) key).containsKey("foo"));
            assertTrue(((LinkedTreeMap<?, ?>) key).containsKey("qux"));
            assertThat(((LinkedTreeMap<?, ?>) key).get("foo")).isEqualTo("bar");
            assertThat(((LinkedTreeMap<?, ?>) key).get("qux")).isEqualTo(12.0);
        });
    }

    @ParameterizedTest
    @MethodSource
    void should_remove_trailing_comma(String inputJson, String expectedOutputJson) {
        String outputJson = ToolExecutionRequestUtil.removeTrailingComma(inputJson);
        assertEquals(expectedOutputJson, outputJson);
    }

    private static Stream<Arguments> should_remove_trailing_comma() {
        return Stream.of(
                Arguments.of(
                        "{\"name\":\"John\",\"age\":30}",
                        "{\"name\":\"John\",\"age\":30}"
                ),
                Arguments.of(
                        "{\"name\":\"John\",\"age\":30,}",
                        "{\"name\":\"John\",\"age\":30}"
                ),
                Arguments.of(
                        "[\"apple\",\"banana\"]",
                        "[\"apple\",\"banana\"]"
                ),
                Arguments.of(
                        "[\"apple\",\"banana\",]",
                        "[\"apple\",\"banana\"]"
                ),
                Arguments.of(
                        "{\"person\":{\"name\":\"John\",\"age\":30},\"city\":\"New York\"}",
                        "{\"person\":{\"name\":\"John\",\"age\":30},\"city\":\"New York\"}"
                ),
                Arguments.of(
                        "{\"person\":{\"name\":\"John\",\"age\":30,},\"city\":\"New York\",}",
                        "{\"person\":{\"name\":\"John\",\"age\":30},\"city\":\"New York\"}"
                ),
                Arguments.of(
                        "[{\"name\":\"John\",\"hobbies\":[\"reading\",\"swimming\"]}]",
                        "[{\"name\":\"John\",\"hobbies\":[\"reading\",\"swimming\"]}]"
                ),
                Arguments.of(
                        "[{\"name\":\"John\",\"hobbies\":[\"reading\",\"swimming\",],},]",
                        "[{\"name\":\"John\",\"hobbies\":[\"reading\",\"swimming\"]}]"
                ),
                Arguments.of(
                        "{,}",
                        "{}"
                ),
                Arguments.of(
                        "[,]",
                        "[]"
                ),
                Arguments.of(
                        "{\"name\":\"John\"}",
                        "{\"name\":\"John\"}"
                ), Arguments.of(
                        "{\"name\":\"John\",}",
                        "{\"name\":\"John\"}"
                ),
                Arguments.of(
                        "{\"a\":[{\"b\":{\"c\":[],},\"d\":{},},],\"e\":\"value\",}",
                        "{\"a\":[{\"b\":{\"c\":[]},\"d\":{}}],\"e\":\"value\"}"
                ),
                Arguments.of(
                        "{\"a\":\"value1\", /*comment*/ \"b\":\"value2\",}",
                        "{\"a\":\"value1\", /*comment*/ \"b\":\"value2\"}"
                ),
                Arguments.of(
                        "{ \"name\" : \"John\" , \"age\" : 30 , }",
                        "{ \"name\" : \"John\" , \"age\" : 30  }"
                ),
                Arguments.of(
                        "{\n  \"name\": \"John\",\n  \"age\": 30,\n}",
                        "{\n  \"name\": \"John\",\n  \"age\": 30\n}"
                )
        );
    }
}