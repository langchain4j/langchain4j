package dev.langchain4j.agent.tool;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.internal.LinkedTreeMap;
import java.util.ArrayList;
import java.util.List;
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

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments()))
                .containsEntry("foo", "bar")
                        .containsEntry("qux", 12.0);

        assertThat((String) ToolExecutionRequestUtil.argument(request, "foo"))
                .isEqualTo("bar");
        assertThat((Number) ToolExecutionRequestUtil.argument(request, "qux"))
                .isEqualTo(12.0);
    }

    @Test
    public void test_argument_comma() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                                                           .id("id")
                                                           .name("name")
                                                           .arguments("{\"foo\":\"bar\", \"qux\": 12,}")
                                                           .build();

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments()))
              .containsEntry("foo", "bar")
              .containsEntry("qux", 12.0);

        assertThat((String) ToolExecutionRequestUtil.argument(request, "foo"))
              .isEqualTo("bar");
        assertThat((Number) ToolExecutionRequestUtil.argument(request, "qux"))
              .isEqualTo(12.0);
    }

    @Test
    public void test_argument_comma_array() {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
                                                           .id("id")
                                                           .name("name")
                                                           .arguments(
                                                                 "{\"key\":[{\"foo\":\"bar\", \"qux\": 12,},{\"foo\":\"bar\", \"qux\": 12,},]}")
                                                           .build();

        assertThat(ToolExecutionRequestUtil.argumentsAsMap(request.arguments()))
              .containsKey("key");

        assertTrue(ToolExecutionRequestUtil.argument(request, "key") instanceof ArrayList);

        List<Object> keys = ToolExecutionRequestUtil.argument(request, "key");

        keys.forEach(key -> {
            assertTrue(key instanceof LinkedTreeMap);
            assertTrue(((LinkedTreeMap<?, ?>) key).containsKey("foo"));
            assertTrue(((LinkedTreeMap<?, ?>) key).containsKey("qux"));
            assertThat(((LinkedTreeMap<?, ?>) key).get("foo")).isEqualTo("bar");
            assertThat(((LinkedTreeMap<?, ?>) key).get("qux")).isEqualTo(12.0);
        });
    }
}