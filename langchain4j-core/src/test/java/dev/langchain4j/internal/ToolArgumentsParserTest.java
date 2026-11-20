package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ToolArgumentsParserTest {

    @Test
    void null_and_blank_give_empty_map() {
        assertThat(ToolArgumentsParser.argumentsAsMap(null)).isEmpty();
        assertThat(ToolArgumentsParser.argumentsAsMap("  ")).isEmpty();
    }

    @Test
    void strict_and_lenient_forms() {
        assertThat(ToolArgumentsParser.argumentsAsMap("{\"a\":1}")).containsEntry("a", 1);
        assertThat(ToolArgumentsParser.argumentsAsMap("{\"a\":1,}")).containsEntry("a", 1);
        assertThat(ToolArgumentsParser.argumentsAsMap("\"{\\\"a\\\":\\\"b\\\"}\""))
                .containsEntry("a", "b");
    }

    @Test
    void helpers_handle_null_and_empty() {
        assertThat(ToolArgumentsParser.removeTrailingComma(null)).isNull();
        assertThat(ToolArgumentsParser.removeTrailingComma("")).isEmpty();
        assertThat(ToolArgumentsParser.removeTrailingComma("[1,]")).isEqualTo("[1]");
        assertThat(ToolArgumentsParser.normalizeJsonString(null)).isNull();
        assertThat(ToolArgumentsParser.normalizeJsonString("")).isEmpty();
        assertThat(ToolArgumentsParser.normalizeJsonString("\"{\\\"a\\\":1}\"")).isEqualTo("{\"a\":1}");
    }

    @Test
    void argument() {
        assertThat(ToolArgumentsParser.argumentsAsMap("{\"foo\":\"bar\", \"qux\": 12, \"abc\": 1.23}"))
                .containsEntry("foo", "bar")
                .containsEntry("qux", 12)
                .containsEntry("abc", 1.23);
    }

    @Test
    void argument_comma() {
        assertThat(ToolArgumentsParser.argumentsAsMap("{\"foo\":\"bar\", \"qux\": 12,}"))
                .containsEntry("foo", "bar")
                .containsEntry("qux", 12);
    }

    @Test
    void argument_leading_trailing_quotes() {
        assertThat(ToolArgumentsParser.argumentsAsMap("\"{\"foo\":\"bar\"}\"")).containsEntry("foo", "bar");
    }

    @Test
    void argumentsAsMap_should_parse_arguments_containing_escaped_double_quotes_1() {

        // given JSON containing escaped double quotes (\")
        String arguments = """
                {"arg0":"SELECT COUNT(A) FROM \\"Samples\\".\\"samples.example.com\\".\\"zip_lookup.csv\\""}
                """;

        // when
        Map<String, Object> argumentsMap = ToolArgumentsParser.argumentsAsMap(arguments);

        // then result does not contain escaping
        assertThat(argumentsMap).containsEntry("arg0", """
                SELECT COUNT(A) FROM "Samples"."samples.example.com"."zip_lookup.csv"
                """.trim());
    }

    @Test
    void argumentsAsMap_should_parse_arguments_containing_escaped_double_quotes_2() {

        // given JSON containing escaped double quotes (\")
        String arguments = """
                {
                    "conditionalTaskCreation":{
                        "firstNameInput":"${$context.personal_info.first_name}",
                        "lastNameInput":"${$context.personal_info.last_name}",
                        "conditionInput":"${($context.personal_info.first_name | startswith(\\"d\\"))}"
                    }
                }
                """;

        // when
        Map<String, Object> argumentsMap = ToolArgumentsParser.argumentsAsMap(arguments);

        // then result does not contain escaping
        assertThat(argumentsMap)
                .containsEntry(
                        "conditionalTaskCreation",
                        Map.of(
                                "firstNameInput", "${$context.personal_info.first_name}",
                                "lastNameInput", "${$context.personal_info.last_name}",
                                "conditionInput", """
                        ${($context.personal_info.first_name | startswith("d"))}
                        """.trim()));
    }

    @Test
    void argument_leading_trailing_and_escaped_quotes() {
        assertThat(ToolArgumentsParser.argumentsAsMap("\"{\\\"foo\\\":\\\"bar\\\"}\""))
                .containsEntry("foo", "bar");
    }

    @Test
    void argument_comma_array() {
        Map<String, Object> argumentsAsMap = ToolArgumentsParser.argumentsAsMap(
                "{\"key\":[{\"foo\":\"bar\", \"qux\": 12,},{\"foo\":\"bar\", \"qux\": 12,},]}");
        assertThat(argumentsAsMap).containsOnlyKeys("key");

        List<Object> keys = (List<Object>) argumentsAsMap.get("key");
        keys.forEach(key -> {
            assertThat(key).isInstanceOf(LinkedHashMap.class);
            assertThat(((LinkedHashMap<?, ?>) key).containsKey("foo")).isTrue();
            assertThat(((LinkedHashMap<?, ?>) key).containsKey("qux")).isTrue();
            assertThat(((LinkedHashMap<?, ?>) key).get("foo")).isEqualTo("bar");
            assertThat(((LinkedHashMap<?, ?>) key).get("qux")).isEqualTo(12);
        });
    }

    @ParameterizedTest
    @MethodSource
    void should_remove_trailing_comma(String inputJson, String expectedOutputJson) {
        String outputJson = ToolArgumentsParser.removeTrailingComma(inputJson);
        assertThat(outputJson).isEqualTo(expectedOutputJson);
    }

    private static Stream<Arguments> should_remove_trailing_comma() {
        return Stream.of(
                Arguments.of("{\"name\":\"John\",\"age\":30}", "{\"name\":\"John\",\"age\":30}"),
                Arguments.of("{\"name\":\"John\",\"age\":30,}", "{\"name\":\"John\",\"age\":30}"),
                Arguments.of("[\"apple\",\"banana\"]", "[\"apple\",\"banana\"]"),
                Arguments.of("[\"apple\",\"banana\",]", "[\"apple\",\"banana\"]"),
                Arguments.of(
                        "{\"person\":{\"name\":\"John\",\"age\":30},\"city\":\"New York\"}",
                        "{\"person\":{\"name\":\"John\",\"age\":30},\"city\":\"New York\"}"),
                Arguments.of(
                        "{\"person\":{\"name\":\"John\",\"age\":30,},\"city\":\"New York\",}",
                        "{\"person\":{\"name\":\"John\",\"age\":30},\"city\":\"New York\"}"),
                Arguments.of(
                        "[{\"name\":\"John\",\"hobbies\":[\"reading\",\"swimming\"]}]",
                        "[{\"name\":\"John\",\"hobbies\":[\"reading\",\"swimming\"]}]"),
                Arguments.of(
                        "[{\"name\":\"John\",\"hobbies\":[\"reading\",\"swimming\",],},]",
                        "[{\"name\":\"John\",\"hobbies\":[\"reading\",\"swimming\"]}]"),
                Arguments.of("{,}", "{}"),
                Arguments.of("[,]", "[]"),
                Arguments.of("{\"name\":\"John\"}", "{\"name\":\"John\"}"),
                Arguments.of("{\"name\":\"John\",}", "{\"name\":\"John\"}"),
                Arguments.of(
                        "{\"a\":[{\"b\":{\"c\":[],},\"d\":{},},],\"e\":\"value\",}",
                        "{\"a\":[{\"b\":{\"c\":[]},\"d\":{}}],\"e\":\"value\"}"),
                Arguments.of(
                        "{\"a\":\"value1\", /*comment*/ \"b\":\"value2\",}",
                        "{\"a\":\"value1\", /*comment*/ \"b\":\"value2\"}"),
                Arguments.of("{ \"name\" : \"John\" , \"age\" : 30 , }", "{ \"name\" : \"John\" , \"age\" : 30  }"),
                Arguments.of(
                        "{\n  \"name\": \"John\",\n  \"age\": 30,\n}", "{\n  \"name\": \"John\",\n  \"age\": 30\n}"));
    }
}
