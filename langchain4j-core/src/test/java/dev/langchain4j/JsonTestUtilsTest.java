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
}
