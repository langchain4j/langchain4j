package dev.langchain4j.agent.tool;

import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;

class ToolExecutionRequestUtilTest implements WithAssertions {
    @Test
    public void test_argument() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                .id("id")
                .name("name")
                .arguments("{\"foo\":\"bar\", \"qux\": 12}")
                .build();

        // TODO: Langchain4j: is ok to have now either long or double ? It fixes other issues.
        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments()))
                .containsEntry("foo", "bar")
                        .containsEntry("qux", 12L);

        assertThat((String) ToolExecutionRequestUtil.argument(request, "foo"))
                .isEqualTo("bar");
        assertThat((Number) ToolExecutionRequestUtil.argument(request, "qux"))
                .isEqualTo(12L);
    }
}