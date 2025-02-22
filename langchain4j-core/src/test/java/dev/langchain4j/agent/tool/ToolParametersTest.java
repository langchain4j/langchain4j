package dev.langchain4j.agent.tool;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ToolParametersTest implements WithAssertions {
    @Test
    void equals_hash() {
        ToolParameters tp1 = ToolParameters.builder()
                .type("foo")
                .properties(singletonMap("abc", singletonMap("xyz", 12)))
                .required(singletonList("jkl"))
                .build();
        ToolParameters tp2 = ToolParameters.builder()
                .type("foo")
                .properties(singletonMap("abc", singletonMap("xyz", 12)))
                .required(singletonList("jkl"))
                .build();

        assertThat(tp1)
                .isEqualTo(tp1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(tp2)
                .hasSameHashCodeAs(tp2);

        assertThat(ToolParameters.builder()
                        .type("changed")
                        .properties(singletonMap("abc", singletonMap("xyz", 12)))
                        .required(singletonList("jkl"))
                        .build())
                .isNotEqualTo(tp1)
                .doesNotHaveSameHashCodeAs(tp1);

        assertThat(ToolParameters.builder()
                        .type("foo")
                        .properties(singletonMap("abc", singletonMap("xyz", "changed")))
                        .required(singletonList("jkl"))
                        .build())
                .isNotEqualTo(tp1)
                .doesNotHaveSameHashCodeAs(tp1);

        assertThat(ToolParameters.builder()
                        .type("foo")
                        .properties(singletonMap("abc", singletonMap("xyz", 12)))
                        .required(singletonList("changed"))
                        .build())
                .isNotEqualTo(tp1)
                .doesNotHaveSameHashCodeAs(tp1);
    }

    @Test
    void to_string() {
        ToolParameters parameters = ToolParameters.builder()
                .type("foo")
                .properties(singletonMap("abc", singletonMap("xyz", 12)))
                .required(singletonList("jkl"))
                .build();
        assertThat(parameters.toString())
                .isEqualTo("ToolParameters { type = \"foo\", properties = {abc={xyz=12}}, required = [jkl] }");
    }

    @Test
    void builder() {
        {
            // Defaults.
            ToolParameters parameters = ToolParameters.builder().build();
            assertThat(parameters.type()).isEqualTo("object");
            assertThat(parameters.properties()).isEmpty();
            assertThat(parameters.required()).isEmpty();
        }
    }
}
