package dev.langchain4j.agent.tool;

import static java.util.Collections.singletonMap;

import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.Collections;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ToolSpecificationTest implements WithAssertions {
    @Test
    void builder() {
        ToolSpecification ts = ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(singletonMap("foo", singletonMap("bar", "baz")))
                        .required(Collections.singletonList("foo"))
                        .build())
                .build();

        assertThat(ts.name()).isEqualTo("name");
        assertThat(ts.description()).isEqualTo("description");
        assertThat(ts.toolParameters().type()).isEqualTo("type");
    }

    @Test
    void parameter_builder() {
        ToolSpecification ts = ToolSpecification.builder()
                .name("name")
                .description("description")
                .addParameter("req", JsonSchemaProperty.BOOLEAN)
                .addOptionalParameter("foo", JsonSchemaProperty.STRING, JsonSchemaProperty.description("description"))
                .addOptionalParameter("bar", JsonSchemaProperty.INTEGER)
                .build();

        assertThat(ts.name()).isEqualTo("name");
        assertThat(ts.description()).isEqualTo("description");
        assertThat(ts.toolParameters().type()).isEqualTo("object");
        assertThat(ts.toolParameters().properties().get("req")).containsEntry("type", "boolean");
        assertThat(ts.toolParameters().properties().get("foo"))
                .containsEntry("type", "string")
                .containsEntry("description", "description");
        assertThat(ts.toolParameters().properties().get("bar")).containsEntry("type", "integer");
        assertThat(ts.toolParameters().required()).containsOnly("req");
    }

    @Test
    void equals_hash() {
        ToolSpecification sp1 = ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(singletonMap("foo", singletonMap("bar", "baz")))
                        .required(Collections.singletonList("foo"))
                        .build())
                .build();

        ToolSpecification sp2 = ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(ToolParameters.builder()
                        .type("type")
                        .properties(singletonMap("foo", singletonMap("bar", "baz")))
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
                                .properties(singletonMap("foo", singletonMap("bar", "baz")))
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
                                .properties(singletonMap("foo", singletonMap("bar", "baz")))
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
                                .properties(singletonMap("foo", singletonMap("bar", "baz")))
                                .required(Collections.singletonList("changed"))
                                .build())
                        .build())
                .isNotEqualTo(sp1)
                .doesNotHaveSameHashCodeAs(sp1);
    }

    @Test
    void to_string() {
        ToolSpecification sp1 = ToolSpecification.builder()
                .name("name")
                .description("description")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("foo")
                        .required("foo")
                        .build())
                .build();

        assertThat(sp1.toString())
                .isEqualTo("ToolSpecification { " + "name = \"name\", "
                        + "description = \"description\", "
                        + "parameters = JsonObjectSchema {description = null, properties = {foo=JsonStringSchema {description = null }}, required = [foo], additionalProperties = null, definitions = null }, "
                        + "toolParameters = null "
                        + "}");
    }
}
