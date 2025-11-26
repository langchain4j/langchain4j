package dev.langchain4j;

import static dev.langchain4j.JsonTestUtils.jsonify;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonTestUtilsTest {

    @Test
    void should_jsonify() {
        assertThat(jsonify("")).isEqualTo("\"\"");
        assertThat(jsonify("a")).isEqualTo("\"a\"");
        assertThat(jsonify("a b")).isEqualTo("\"a b\"");
        assertThat(jsonify("a\nb")).isEqualTo("\"a\\nb\"");
    }

    @Test
    void should_jsonify_null() {
        assertThat(jsonify(null)).isEqualTo("null");
    }

    @Test
    void should_jsonify_special_characters() {
        assertThat(jsonify("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(jsonify("a\\b")).isEqualTo("\"a\\\\b\"");
        assertThat(jsonify("a\tb")).isEqualTo("\"a\\tb\"");
        assertThat(jsonify("a\rb")).isEqualTo("\"a\\rb\"");
    }

    @Test
    void should_jsonify_empty_vs_whitespace() {
        assertThat(jsonify("")).isEqualTo("\"\"");
        assertThat(jsonify(" ")).isEqualTo("\" \"");
    }
}
