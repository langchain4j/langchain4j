package dev.langchain4j.agent.tool;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

class ToolSpecificationTest implements WithAssertions {
    @Test
    public void test_builder() {
        ToolSpecification ts = ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(Collections.singletonMap("foo", Collections.singletonMap("bar", "baz")))
                        .required(Collections.singletonList("foo"))
                        .build())
                        .build();

        assertThat(ts.name()).isEqualTo("name");
        assertThat(ts.description()).isEqualTo("description");
        assertThat(ts.parameters().type()).isEqualTo("type");
    }

    @Test
    public void test_parameter_builder() {
        ToolSpecification ts = ToolSpecification.builder()
                .name("name")
                .description("description")
                .addParameter("req", JsonSchemaProperty.BOOLEAN)
                .addOptionalParameter("foo", JsonSchemaProperty.STRING, JsonSchemaProperty.description("description"))
                .addOptionalParameter("bar", JsonSchemaProperty.INTEGER)
                .build();

        assertThat(ts.name()).isEqualTo("name");
        assertThat(ts.description()).isEqualTo("description");
        assertThat(ts.parameters().type()).isEqualTo("object");
        assertThat(ts.parameters().properties().get("req"))
                .containsEntry("type", "boolean");
        assertThat(ts.parameters().properties().get("foo"))
                .containsEntry("type", "string")
                .containsEntry("description", "description");
        assertThat(ts.parameters().properties().get("bar"))
                .containsEntry("type", "integer");
        assertThat(ts.parameters().required()).containsOnly("req");
    }

    @Test
    public void test_equals_hash() {
        ToolSpecification sp1 = ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(Collections.singletonMap("foo", Collections.singletonMap("bar", "baz")))
                        .required(Collections.singletonList("foo"))
                        .build())
                .build();

        ToolSpecification sp2 = ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(Collections.singletonMap("foo", Collections.singletonMap("bar", "baz")))
                        .required(Collections.singletonList("foo"))
                        .build())
                .build();

        assertThat(sp1)
                .isEqualTo(sp1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(sp2)
                .hasSameHashCodeAs(sp2);

        assertThat(ToolSpecification.builder()
                .name("changed")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(Collections.singletonMap("foo", Collections.singletonMap("bar", "baz")))
                        .required(Collections.singletonList("foo"))
                        .build())
                .build())
                .isNotEqualTo(sp1)
                .doesNotHaveSameHashCodeAs(sp1);

        assertThat(ToolSpecification.builder()
                .name("name")
                .description("changed")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(Collections.singletonMap("foo", Collections.singletonMap("bar", "baz")))
                        .required(Collections.singletonList("foo"))
                        .build())
                .build())
                .isNotEqualTo(sp1)
                .doesNotHaveSameHashCodeAs(sp1);

        assertThat(ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(Collections.singletonMap("foo", Collections.singletonMap("bar", "baz")))
                        .required(Collections.singletonList("changed"))
                        .build())
                .build())
                .isNotEqualTo(sp1)
                .doesNotHaveSameHashCodeAs(sp1);
    }

    @Test
    public void test_toString() {
        ToolSpecification sp1 = ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(Collections.singletonMap("foo", Collections.singletonMap("bar", "baz")))
                        .required(Collections.singletonList("foo"))
                        .build())
                .build();

        assertThat(sp1.toString())
                .isEqualTo(
                        "ToolSpecification { name = \"name\", description = \"description\", parameters = ToolParameters { type = \"type\", properties = {foo={bar=baz}}, required = [foo] } }");
    }

}