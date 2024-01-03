package dev.langchain4j.agent.tool;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ToolExecutionRequestTest implements WithAssertions {
    @Test
    public void test_builder() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("arguments")
                .build();

        assertThat(request.id()).isEqualTo("id");
        assertThat(request.name()).isEqualTo("name");
        assertThat(request.arguments()).isEqualTo("arguments");

        assertThat(request)
                .hasToString(
                        "ToolExecutionRequest { id = \"id\", name = \"name\", arguments = \"arguments\" }");
    }

    @Test
    public void test_equals_hash() {
        ToolExecutionRequest req1 = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("arguments")
                .build();

        ToolExecutionRequest req2 = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("arguments")
                .build();

        assertThat(req1)
                .isEqualTo(req1)
                .isNotEqualTo(null)
                .isNotEqualTo(new Object())
                .isEqualTo(req2)
                .hasSameHashCodeAs(req2);

        assertThat(ToolExecutionRequest.builder()
                .id("foo")
                .name("name")
                .arguments("arguments")
                .build())
                .isNotEqualTo(req1)
                .doesNotHaveSameHashCodeAs(req1);

        assertThat(ToolExecutionRequest.builder()
                .id("id")
                .name("foo")
                .arguments("arguments")
                .build())
                .isNotEqualTo(req1)
                .doesNotHaveSameHashCodeAs(req1);

        assertThat(ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("foo")
                .build())
                .isNotEqualTo(req1)
                .doesNotHaveSameHashCodeAs(req1);
    }

    @Test
    public void test_allNull() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .build();

        assertThat(request.id()).isNull();
        assertThat(request.name()).isNull();
        assertThat(request.arguments()).isNull();

        assertThat(request)
                .isEqualTo(ToolExecutionRequest.builder().build());
    }

}