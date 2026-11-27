package dev.langchain4j.service.tool;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ToolExecutionRequestUtilTest implements WithAssertions {

    @Test
    void argument() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("{\"foo\":\"bar\", \"qux\": 12, \"abc\": 1.23}")
                .build();

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments()))
                .containsEntry("foo", "bar")
                .containsEntry("qux", 12)
                .containsEntry("abc", 1.23);
    }

    @Test
    void argument_comma() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("{\"foo\":\"bar\", \"qux\": 12,}")
                .build();

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments()))
                .containsEntry("foo", "bar")
                .containsEntry("qux", 12);
    }

    @Test
    void argument_leading_trailing_quotes() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("\"{\"foo\":\"bar\"}\"")
                .build();

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments())).containsEntry("foo", "bar");
    }

    @Test
    void argumentsAsMap_should_parse_arguments_containing_escaped_double_quotes_1() {

        // given JSON containing escaped double quotes (\")
        String arguments = """
                {"arg0":"SELECT COUNT(A) FROM \\"Samples\\".\\"samples.example.com\\".\\"zip_lookup.csv\\""}
                """;

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments(arguments)
                .build();

        // when
        Map<String, Object> argumentsMap = ToolExecutionRequestUtil.argumentsAsMap(request.arguments());

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

        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments(arguments)
                .build();

        // when
        Map<String, Object> argumentsMap = ToolExecutionRequestUtil.argumentsAsMap(request.arguments());

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
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("\"{\\\"foo\\\":\\\"bar\\\"}\"")
                .build();

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments())).containsEntry("foo", "bar");
    }

    @Test
    void argument_comma_array() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("{\"key\":[{\"foo\":\"bar\", \"qux\": 12,},{\"foo\":\"bar\", \"qux\": 12,},]}")
                .build();

        Map<String, Object> argumentsAsMap = ToolExecutionRequestUtil.argumentsAsMap(request.arguments());
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

    // The tests below exercise the fallback path of argumentsAsMap: the arguments are not valid JSON
    // (because of a structural trailing comma, or because of extra enclosing quotes or escaping), so they are
    // repaired and parsed again. Removing a structural trailing comma must not change the content of string values,
    // and the legacy normalization of quoted/escaped arguments must keep its previous behavior.
    // Each test states the raw JSON (as seen by the parser) in a comment above the Java literal.

    @Test
    void argumentsAsMap_trailing_comma_fallback_should_preserve_comma_before_closing_brace_inside_string() {

        // raw: {"code":"int[] a = {1, 2, };"}
        String validArguments = "{\"code\":\"int[] a = {1, 2, };\"}";
        // raw: {"code":"int[] a = {1, 2, };",}
        String argumentsWithTrailingComma = "{\"code\":\"int[] a = {1, 2, };\",}";

        assertTrailingCommaFallbackParsesLikeValidJson(
                validArguments, argumentsWithTrailingComma, Map.of("code", "int[] a = {1, 2, };"));
    }

    @Test
    void argumentsAsMap_trailing_comma_fallback_should_preserve_comma_before_closing_bracket_inside_string() {

        // raw: {"items":"[1, 2, ]"}
        String validArguments = "{\"items\":\"[1, 2, ]\"}";
        // raw: {"items":"[1, 2, ]",}
        String argumentsWithTrailingComma = "{\"items\":\"[1, 2, ]\",}";

        assertTrailingCommaFallbackParsesLikeValidJson(
                validArguments, argumentsWithTrailingComma, Map.of("items", "[1, 2, ]"));
    }

    @Test
    void argumentsAsMap_trailing_comma_fallback_should_preserve_escaped_double_quotes() {

        // raw: {"query":"SELECT * FROM \"Samples\""}
        String validArguments = "{\"query\":\"SELECT * FROM \\\"Samples\\\"\"}";
        // raw: {"query":"SELECT * FROM \"Samples\"",}
        String argumentsWithTrailingComma = "{\"query\":\"SELECT * FROM \\\"Samples\\\"\",}";

        assertTrailingCommaFallbackParsesLikeValidJson(
                validArguments, argumentsWithTrailingComma, Map.of("query", "SELECT * FROM \"Samples\""));
    }

    @Test
    void argumentsAsMap_trailing_comma_fallback_should_preserve_escaped_backslashes() {

        // raw: {"path":"C:\\temp\\"}
        String validArguments = "{\"path\":\"C:\\\\temp\\\\\"}";
        // raw: {"path":"C:\\temp\\",}
        String argumentsWithTrailingComma = "{\"path\":\"C:\\\\temp\\\\\",}";

        // parsed value: C:\temp\
        assertTrailingCommaFallbackParsesLikeValidJson(
                validArguments, argumentsWithTrailingComma, Map.of("path", "C:\\temp\\"));
    }

    private void assertTrailingCommaFallbackParsesLikeValidJson(
            String validArguments, String argumentsWithTrailingComma, Map<String, Object> expected) {

        // preconditions: the valid variant is parsed by the strict path (this also verifies the literal escaping),
        // and the variant with the structural trailing comma is rejected by the strict parser, so the fallback is used
        assertThat(ToolExecutionRequestUtil.argumentsAsMap(validArguments))
                .as("precondition: valid arguments are parsed by the strict path")
                .isEqualTo(expected);
        assertThat(catchThrowable(() -> Json.fromJson(argumentsWithTrailingComma, Map.class)))
                .as("precondition: strict parsing of arguments with a trailing comma fails")
                .isNotNull();

        // when
        Map<String, Object> argumentsMap = ToolExecutionRequestUtil.argumentsAsMap(argumentsWithTrailingComma);

        // then
        assertThat(argumentsMap).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource
    void argumentsAsMap_should_keep_legacy_normalization(String arguments, Map<String, Object> expected) {

        Map<String, Object> argumentsMap = ToolExecutionRequestUtil.argumentsAsMap(arguments);

        assertThat(argumentsMap).isEqualTo(expected);
    }

    // Arguments that are not valid JSON because of extra enclosing quotes and/or escaped quotes.
    // The expected maps are what the legacy normalization produces (strip a leading and/or trailing quote,
    // then replace every \" with "), so escapes other than \" are decoded only once, by the JSON parser.
    // <LF> in the raw comments stands for a real line feed character.
    private static Stream<Arguments> argumentsAsMap_should_keep_legacy_normalization() {
        return Stream.of(
                // pretty-printed, with enclosing quotes: raw newlines and escaped quotes
                // raw: "{<LF>  \"city\": \"Paris\"<LF>}"
                Arguments.of("\"{\n  \\\"city\\\": \\\"Paris\\\"\n}\"", Map.of("city", "Paris")),
                // pretty-printed, without enclosing quotes: raw newlines and escaped quotes
                // raw: {<LF>  \"city\": \"Paris\"<LF>}
                Arguments.of("{\n  \\\"city\\\": \\\"Paris\\\"\n}", Map.of("city", "Paris")),
                // only a leading enclosing quote
                // raw: "{\"foo\":\"bar\"}
                Arguments.of("\"{\\\"foo\\\":\\\"bar\\\"}", Map.of("foo", "bar")),
                // only a trailing enclosing quote
                // raw: {\"foo\":\"bar\"}"
                Arguments.of("{\\\"foo\\\":\\\"bar\\\"}\"", Map.of("foo", "bar")),
                // mixed escaped and unescaped quotes
                // raw: {"foo":\"bar\"}
                Arguments.of("{\"foo\":\\\"bar\\\"}", Map.of("foo", "bar")),
                // escaped newline inside a value
                // raw: "{\"text\":\"line1\nline2\"}"
                Arguments.of("\"{\\\"text\\\":\\\"line1\\nline2\\\"}\"", Map.of("text", "line1\nline2")),
                // regex value
                // raw: "{\"pattern\":\"\\d+\"}"
                Arguments.of("\"{\\\"pattern\\\":\\\"\\\\d+\\\"}\"", Map.of("pattern", "\\d+")),
                // Windows path (parsed value: C:\temp)
                // raw: "{\"path\":\"C:\\temp\"}"
                Arguments.of("\"{\\\"path\\\":\\\"C:\\\\temp\\\"}\"", Map.of("path", "C:\\temp")));
    }

    @Test
    void argumentsAsMap_should_remove_trailing_comma_from_double_encoded_arguments_without_changing_string_values() {

        // raw: "{\"a\":\"x, }\",}"
        // (double-encoded JSON object with a structural trailing comma and a comma before } inside a string value)
        String arguments = "\"{\\\"a\\\":\\\"x, }\\\",}\"";

        Map<String, Object> argumentsMap = ToolExecutionRequestUtil.argumentsAsMap(arguments);

        assertThat(argumentsMap).isEqualTo(Map.of("a", "x, }"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"a\":\"b\"", // raw: {"a":"b"           (missing closing brace)
                "{\"a\":\"x, }", // raw: {"a":"x, }        (unterminated string)
                "{\"a\":1,,}", // raw: {"a":1,,}             (double comma)
                "\"not json\"", // raw: "not json"         (double-encoded, but not a JSON object)
                "\"{\\\"a\\\":\\\"b\\\"\"" // raw: "{\"a\":\"b\"" (double-encoded, missing closing brace)
            })
    void argumentsAsMap_should_fail_when_arguments_are_still_invalid_after_normalization(String arguments) {

        assertThatThrownBy(() -> ToolExecutionRequestUtil.argumentsAsMap(arguments))
                .isInstanceOf(Exception.class);
    }

    @ParameterizedTest
    @MethodSource
    void should_remove_trailing_comma(String inputJson, String expectedOutputJson) {
        String outputJson = ToolExecutionRequestUtil.removeTrailingComma(inputJson);
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
                        "{\n  \"name\": \"John\",\n  \"age\": 30,\n}", "{\n  \"name\": \"John\",\n  \"age\": 30\n}"),
                // commas before a closing brace/bracket INSIDE string values must be preserved
                // raw: {"a":"x, }","b":[1,],} -> {"a":"x, }","b":[1]}
                Arguments.of("{\"a\":\"x, }\",\"b\":[1,],}", "{\"a\":\"x, }\",\"b\":[1]}"),
                // raw: ["x, ]",] -> ["x, ]"]
                Arguments.of("[\"x, ]\",]", "[\"x, ]\"]"),
                // escaped quote inside string: {"a":"say \"hi, ]\"",} -> {"a":"say \"hi, ]\""}
                Arguments.of("{\"a\":\"say \\\"hi, ]\\\"\",}", "{\"a\":\"say \\\"hi, ]\\\"\"}"),
                // escaped backslash right before the closing quote: {"a":"C:\\",} -> {"a":"C:\\"}
                Arguments.of("{\"a\":\"C:\\\\\",}", "{\"a\":\"C:\\\\\"}"),
                // escaped backslash followed by escaped quote: {"a":"\\\", }",} -> {"a":"\\\", }"}
                Arguments.of("{\"a\":\"\\\\\\\", }\",}", "{\"a\":\"\\\\\\\", }\"}"),
                // nested escaped JSON inside a string: {"a":"{\"b\":[1, ],}",} -> {"a":"{\"b\":[1, ],}"}
                Arguments.of("{\"a\":\"{\\\"b\\\":[1, ],}\",}", "{\"a\":\"{\\\"b\\\":[1, ],}\"}"),
                // unterminated string is left untouched: {"a":"x, } -> {"a":"x, }
                Arguments.of("{\"a\":\"x, }", "{\"a\":\"x, }"));
    }
}
