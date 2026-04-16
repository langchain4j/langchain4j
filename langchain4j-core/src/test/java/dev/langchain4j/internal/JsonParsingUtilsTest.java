package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive unit tests for JsonParsingUtils.extractAndParseJson method.
 * These tests cover various scenarios including nested JSON structures,
 * text with noise, multiple JSON blocks, and edge cases.
 */
class JsonParsingUtilsTest {

    /**
     * Test POJO class with various field types to test different JSON structures.
     * Includes primitive types, collections, and nested objects.
     */
    static class MyPojo {
        public String name;
        public int age;
        public List<String> tags;
        public Map<String, Object> extra;

        public MyPojo() {}

        public MyPojo(String name, int age) {
            this.name = name;
            this.age = age;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyPojo myPojo = (MyPojo) o;
            return age == myPojo.age && java.util.Objects.equals(name, myPojo.name);
        }
    }

    /**
     * Test basic JSON object extraction without any surrounding text.
     * This is the simplest case where the entire text is valid JSON.
     */
    @Test
    void extract_simple_object() throws Exception {
        String json = "{\"name\":\"Tom\",\"age\":18}";
        JsonParsingUtils.ParsedJson<MyPojo> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isNotNull();
        assertThat(result.value().name).isEqualTo("Tom");
        assertThat(result.value().age).isEqualTo(18);
    }

    /**
     * Test JSON object extraction when surrounded by non-JSON text.
     * This tests the ability to find and extract JSON from mixed content.
     * The method should ignore the prefix and suffix text.
     */
    @Test
    void extract_object_with_prefix_suffix() throws Exception {
        String json = "prefix {\"name\":\"Jerry\",\"age\":20} suffix";
        JsonParsingUtils.ParsedJson<MyPojo> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isNotNull();
        assertThat(result.value().name).isEqualTo("Jerry");
        assertThat(result.value().age).isEqualTo(20);
    }

    /**
     * Test JSON array extraction containing multiple objects.
     * This verifies that the method can handle array structures and
     * properly parse each element in the array.
     */
    @Test
    void extract_array() throws Exception {
        String json = "[{\"name\":\"A\",\"age\":1},{\"name\":\"B\",\"age\":2}]";
        JsonParsingUtils.ParsedJson<MyPojo[]> result =
                JsonParsingUtils.extractAndParseJson(json, MyPojo[].class);
        assertThat(result).isNotNull();
        assertThat(result.value().length).isEqualTo(2);
        assertThat(result.value()[0].name).isEqualTo("A");
        assertThat(result.value()[1].age).isEqualTo(2);
    }

    /**
     * Test JSON array extraction when surrounded by noise text.
     * This tests the lastIndexOf logic - it should find the outermost ']'
     * even when there are other characters before it.
     */
    @Test
    void extract_array_with_noise() throws Exception {
        String json = "abc [{\"name\":\"A\",\"age\":1},{\"name\":\"B\",\"age\":2}] xyz";
        JsonParsingUtils.ParsedJson<MyPojo[]> result =
                JsonParsingUtils.extractAndParseJson(json, MyPojo[].class);
        assertThat(result).isNotNull();
        assertThat(result.value().length).isEqualTo(2);
    }

    /**
     * Test nested array extraction with multiple levels of nesting.
     * This is a critical test for the lastIndexOf logic - it must find
     * the outermost closing bracket, not the inner ones.
     * The structure is: [[object1], [object2]]
     */
    @Test
    void extract_nested_array() throws Exception {
        String json = "[[{\"name\":\"A\",\"age\":1}],[{\"name\":\"B\",\"age\":2}]]";
        JsonParsingUtils.ParsedJson<MyPojo[][]> result =
                JsonParsingUtils.extractAndParseJson(json, MyPojo[][].class);
        assertThat(result).isNotNull();
        assertThat(result.value().length).isEqualTo(2);
        assertThat(result.value()[0][0].name).isEqualTo("A");
        assertThat(result.value()[1][0].name).isEqualTo("B");
    }

    /**
     * Test JSON object with an array field.
     * This verifies that the method can handle objects containing
     * array properties and parse them correctly.
     */
    @Test
    void extract_object_with_array_field() throws Exception {
        String json = "{\"name\":\"Tom\",\"age\":18,\"tags\":[\"a\",\"b\"]}";
        JsonParsingUtils.ParsedJson<MyPojo> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isNotNull();
        assertThat(result.value().tags).containsExactly("a", "b");
    }

    /**
     * Test JSON object with a map/object field.
     * This verifies that the method can handle nested object structures
     * and parse complex JSON hierarchies.
     */
    @Test
    void extract_object_with_map_field() throws Exception {
        String json = "{\"name\":\"Tom\",\"age\":18,\"extra\":{\"k1\":123,\"k2\":\"v2\"}}";
        JsonParsingUtils.ParsedJson<MyPojo> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isNotNull();
        assertThat(result.value().extra).containsEntry("k1", 123).containsEntry("k2", "v2");
    }

    /**
     * Test extraction when multiple JSON blocks are present in the text.
     * This is a key test for the lastIndexOf logic - it should extract
     * the last (rightmost) JSON block, not the first one.
     * The method should find the outermost closing brace/bracket from the end.
     */
    @Test
    void extract_multiple_json_blocks() throws Exception {
        String json = "foo {\"name\":\"A\",\"age\":1} bar {\"name\":\"B\",\"age\":2}";
        JsonParsingUtils.ParsedJson<MyPojo> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isNotNull();
        // Should extract the last JSON block due to lastIndexOf logic
        assertThat(result.value().name).isEqualTo("B");
    }

    /**
     * Test behavior when no valid JSON is present in the text.
     * The method should return an empty Optional when no parseable
     * JSON structure can be found.
     */
    @Test
    void extract_invalid_json() throws Exception {
        String json = "not a json";
        assertThatThrownBy(() -> JsonParsingUtils.extractAndParseJson(json, MyPojo.class))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(JsonParseException.class);
    }

    /**
     * Test JSON object with array elements containing bracket characters.
     * This tests that the method correctly handles brackets that are
     * part of string content rather than JSON structure.
     * The lastIndexOf should not be confused by brackets in string values.
     */
    @Test
    void extract_json_with_inner_brackets() throws Exception {
        String json = "{\"name\":\"Tom\",\"age\":18,\"tags\":[\"a\",\"b\",\"[c]\"]}";
        JsonParsingUtils.ParsedJson<MyPojo> result = JsonParsingUtils.extractAndParseJson(json, MyPojo.class);
        assertThat(result).isNotNull();
        assertThat(result.value().tags).contains("[c]");
    }

    /**
     * Test complex JSON array with nested objects and arrays.
     * This comprehensive test verifies that the method can handle
     * complex nested structures and properly extract the entire array
     * using the lastIndexOf logic to find the outermost closing bracket.
     */
    @Test
    void extract_json_array_with_nested_objects_and_arrays() throws Exception {
        String json =
                "[{\"name\":\"Tom\",\"age\":18,\"tags\":[\"a\",\"b\"]},{\"name\":\"Jerry\",\"age\":20,\"tags\":[\"x\",\"y\"]}]";
        JsonParsingUtils.ParsedJson<MyPojo[]> result =
                JsonParsingUtils.extractAndParseJson(json, MyPojo[].class);
        assertThat(result).isNotNull();
        assertThat(result.value()[1].tags).containsExactly("x", "y");
    }

    /**
     * Regression test for #4585: JsonEOFException in PojoOutputParser
     * when streaming responses are truncated mid-value.
     * <p>
     * When the LLM streams a partial JSON like:
     * {@code {"response": "The sky appears blue due to..."}
     * missing the closing {@code }}, the last {@code } might be inside a string
     * value (e.g., after "atmosphere."). The extraction must find the true
     * structural closing brace, not one inside a quoted string.
     */
    @Test
    void should_handle_truncated_json_with_braces_in_string_values() throws Exception {
        // JSON truncated mid-value with closing } missing
        // The string contains periods that might confuse bracket-finding
        String truncatedJson = "{\"response\": \"The sky appears blue due to Rayleigh scattering. "
            + "This process involves the interaction of light with molecules in the Earth's atmosphere. "
            + "Blue light has shorter wavelengths than other colors of light, so it is scattered more efficiently. "
            + "This scattering effect causes light to appear blue when we look up.\"}";

        // This should NOT throw - extractAndParseJson should handle truncation
        JsonParsingUtils.ParsedJson<Map> result = JsonParsingUtils.extractAndParseJson(truncatedJson, Map.class);

        assertThat(result).isNotNull();
        assertThat(result.value()).containsKey("response");
        assertThat((String) result.value().get("response"))
            .startsWith("The sky appears blue");
    }

    /**
     * Test that truncated JSON where the very last character is a quote
     * (meaning the closing brace is completely missing) is still handled.
     */
    @Test
    void should_handle_json_with_missing_closing_brace() throws Exception {
        String truncatedJson = "{\"name\": \"Test\"";

        JsonParsingUtils.ParsedJson<Map> result = JsonParsingUtils.extractAndParseJson(truncatedJson, Map.class);

        assertThat(result).isNotNull();
        assertThat(result.value()).containsKey("name");
        assertThat(result.value().get("name")).isEqualTo("Test");
    }
}
