package dev.langchain4j.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSource;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.internal.ToolArgumentsParser;
import dev.langchain4j.invocation.InvocationContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ToolGuardrailRequestTest {

    private static final ToolExecutionRequest EXEC = ToolExecutionRequest.builder()
            .id("id1")
            .name("search")
            .arguments("{\"q\":\"a\"}")
            .build();
    private static final GuardrailRequestParams PARAMS = GuardrailRequestParams.builder()
            .userMessageTemplate("t")
            .variables(java.util.Map.of())
            .build();

    private static GuardrailRequestParams paramsWithMemory(Object memoryId) {
        return GuardrailRequestParams.builder()
                .userMessageTemplate("t")
                .variables(java.util.Map.of())
                .invocationContext(
                        InvocationContext.builder().chatMemoryId(memoryId).build())
                .build();
    }

    private static ToolOutputGuardrailRequest.Builder output() {
        return ToolOutputGuardrailRequest.builder().toolExecutionRequest(EXEC).requestParams(PARAMS);
    }

    @Test
    void input_requires_execution_request_and_params() {
        assertThatThrownBy(() -> ToolInputGuardrailRequest.builder()
                        .requestParams(PARAMS)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolExecutionRequest");
        assertThatThrownBy(() -> ToolInputGuardrailRequest.builder()
                        .toolExecutionRequest(EXEC)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestParams");
    }

    @Test
    void input_defaults_and_accessors() {
        ToolInputGuardrailRequest request = ToolInputGuardrailRequest.builder()
                .toolExecutionRequest(EXEC)
                .requestParams(PARAMS)
                .build();

        assertThat(request.toolSpecification()).isNull();
        assertThat(request.toolSource()).isSameAs(ToolSource.programmatic());
        assertThat(request.toolName()).isEqualTo("search");
        assertThat(request.arguments()).isEqualTo("{\"q\":\"a\"}");
        assertThat(request.invocationContext()).isNull();
        assertThat(request.memoryId()).isNull();
        assertThat(request.requestParams()).isSameAs(PARAMS);
    }

    @Test
    void input_memory_id_is_null_safe_and_reachable() {
        ToolInputGuardrailRequest.Builder builder =
                ToolInputGuardrailRequest.builder().toolExecutionRequest(EXEC);

        assertThat(builder.requestParams(paramsWithMemory(null)).build().memoryId())
                .isNull();
        ToolInputGuardrailRequest request =
                builder.requestParams(paramsWithMemory("m1")).build();
        assertThat(request.memoryId()).isEqualTo("m1");
        assertThat(request.invocationContext()).isNotNull();
    }

    @Test
    void input_with_text_replaces_arguments_keeps_id_and_name() {
        ToolSpecification spec = ToolSpecification.builder().name("search").build();
        ToolInputGuardrailRequest request = ToolInputGuardrailRequest.builder()
                .toolExecutionRequest(EXEC)
                .toolSpecification(spec)
                .toolSource(ToolSource.mcp("s1"))
                .requestParams(PARAMS)
                .build();

        ToolInputGuardrailRequest rewritten = request.withText("{\"q\":\"b\"}");

        assertThat(rewritten.arguments()).isEqualTo("{\"q\":\"b\"}");
        assertThat(rewritten.toolExecutionRequest().id()).isEqualTo("id1");
        assertThat(rewritten.toolName()).isEqualTo("search");
        assertThat(rewritten.toolSpecification()).isSameAs(spec);
        assertThat(rewritten.toolSource()).isEqualTo(ToolSource.mcp("s1"));
        assertThat(request.arguments()).isEqualTo("{\"q\":\"a\"}");
    }

    private static ToolInputGuardrailRequest inputWithArguments(String arguments) {
        return ToolInputGuardrailRequest.builder()
                .toolExecutionRequest(EXEC.toBuilder().arguments(arguments).build())
                .requestParams(PARAMS)
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void input_arguments_as_map_nested_and_null_values() {
        Map<String, Object> map = inputWithArguments(
                        "{\"a\":\"x\",\"n\":null,\"num\":3,\"o\":{\"k\":[1,null,{\"z\":true}]}}")
                .argumentsAsMap();

        assertThat(map).containsEntry("a", "x").containsEntry("n", null);
        assertThat(map.get("num")).isInstanceOf(Number.class);
        assertThat(map.get("o")).isInstanceOf(Map.class);
        Map<?, ?> nested = (Map<?, ?>) map.get("o");
        assertThat(nested.get("k")).isInstanceOf(List.class);
        List<Object> list = (List<Object>) nested.get("k");
        assertThat(list).hasSize(3).containsNull();
        Map<String, Object> inList = (Map<String, Object>) list.get(2);
        assertThat(inList).containsEntry("z", true);
        assertThatThrownBy(() -> inList.put("y", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> map.put("b", 1)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> nested.clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> list.add(1)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void input_arguments_as_map_blank_and_empty() {
        assertThat(inputWithArguments("").argumentsAsMap()).isEmpty();
        assertThat(inputWithArguments("  ").argumentsAsMap()).isEmpty();
        assertThat(inputWithArguments("{}").argumentsAsMap()).isEmpty();
    }

    @Test
    void input_arguments_as_map_rejects_invalid_and_non_object() {
        for (String bad : List.of("{not json", "[1,2]", "42", "null")) {
            assertThatThrownBy(() -> inputWithArguments(bad).argumentsAsMap())
                    .as(bad)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void input_arguments_as_map_accepts_lenient_forms() {
        assertThat(inputWithArguments("{\"foo\":\"bar\",}").argumentsAsMap()).containsEntry("foo", "bar");
        assertThat(inputWithArguments("\"{\\\"foo\\\":\\\"bar\\\"}\"").argumentsAsMap())
                .containsEntry("foo", "bar");
        assertThat(inputWithArguments("{\"l\":[1,2,],}").argumentsAsMap()).containsEntry("l", List.of(1, 2));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"foo\":\"bar\", \"qux\": 12,}",
                "\"{\\\"foo\\\":\\\"bar\\\"}\"",
                "{\"l\":[1,2,],\"o\":{\"n\":null,\"k\":[{\"z\":true}]}}",
                "{\"foo\":\"bar\"}",
                "",
                "  "
            })
    void input_arguments_as_map_matches_tool_arguments_parser(String arguments) {
        assertThat(inputWithArguments(arguments).argumentsAsMap())
                .isEqualTo(ToolArgumentsParser.argumentsAsMap(arguments));
    }

    @Test
    void input_arguments_as_map_reflects_with_text_and_to_builder() {
        ToolInputGuardrailRequest request = inputWithArguments("{\"q\":\"a\"}");

        assertThat(request.withText("{\"q\":\"b\"}").argumentsAsMap()).containsEntry("q", "b");
        assertThat(request.toBuilder().build().argumentsAsMap()).containsEntry("q", "a");
        assertThat(request.argumentsAsMap()).containsEntry("q", "a");
    }

    @Test
    void input_to_builder_round_trip() {
        ToolInputGuardrailRequest request = ToolInputGuardrailRequest.builder()
                .toolExecutionRequest(EXEC)
                .toolSource(ToolSource.mcp("s1"))
                .requestParams(PARAMS)
                .build();

        ToolInputGuardrailRequest copy = request.toBuilder().build();

        assertThat(copy.toolExecutionRequest()).isEqualTo(EXEC);
        assertThat(copy.toolSource()).isEqualTo(request.toolSource());
        assertThat(copy.requestParams()).isSameAs(PARAMS);
        assertThat(copy.toolSpecification()).isNull();
    }

    @Test
    void output_requires_execution_request_and_params() {
        assertThatThrownBy(() -> ToolOutputGuardrailRequest.builder()
                        .requestParams(PARAMS)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("toolExecutionRequest");
        assertThatThrownBy(() -> ToolOutputGuardrailRequest.builder()
                        .toolExecutionRequest(EXEC)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requestParams");
    }

    @Test
    void output_defaults_and_accessors() {
        ToolOutputGuardrailRequest request = output().build();

        assertThat(request.resultContents()).isEmpty();
        assertThat(request.isError()).isFalse();
        assertThat(request.text()).isEmpty();
        assertThat(request.toolSource()).isSameAs(ToolSource.programmatic());
        assertThat(request.toolSpecification()).isNull();
        assertThat(request.toolName()).isEqualTo("search");
        assertThat(request.arguments()).isEqualTo("{\"q\":\"a\"}");
        assertThat(request.memoryId()).isNull();
        assertThat(request.invocationContext()).isNull();
    }

    @Test
    void output_memory_id_reachable() {
        ToolOutputGuardrailRequest request =
                output().requestParams(paramsWithMemory(42)).build();

        assertThat(request.memoryId()).isEqualTo(42);
    }

    @Test
    void output_contents_are_immutable_copy() {
        List<Content> source = new ArrayList<>(List.of(new TextContent("a")));
        ToolOutputGuardrailRequest request = output().resultContents(source).build();
        source.add(new TextContent("b"));

        assertThat(request.resultContents()).hasSize(1);
        assertThatThrownBy(() -> request.resultContents().add(new TextContent("c")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(output().resultContents(null).build().resultContents()).isEmpty();
    }

    @Test
    void output_text_joins_multiple_text_contents() {
        ToolOutputGuardrailRequest request = output().resultContents(
                        List.of(new TextContent("a"), ImageContent.from("http://x/i.png"), new TextContent("b")))
                .build();

        assertThat(request.text()).isEqualTo("a\nb");
    }

    @Test
    void output_text_is_empty_for_image_only_result() {
        ToolOutputGuardrailRequest request = output().resultContents(List.of(ImageContent.from("http://x/i.png")))
                .build();

        assertThat(request.text()).isEmpty();
    }

    @Test
    void output_with_text_replaces_text_and_keeps_non_text() {
        ImageContent image = ImageContent.from("http://x/i.png");
        ToolOutputGuardrailRequest request = output().resultContents(
                        List.of(new TextContent("a"), image, new TextContent("b")))
                .isError(true)
                .build();

        ToolOutputGuardrailRequest rewritten = request.withText("z");

        assertThat(rewritten.resultContents()).containsExactly(new TextContent("z"), image);
        assertThat(rewritten.text()).isEqualTo("z");
        assertThat(rewritten.isError()).isTrue();
        assertThat(request.text()).isEqualTo("a\nb");
    }

    @Test
    void output_with_text_on_image_only_and_empty_results_adds_text() {
        ImageContent image = ImageContent.from("http://x/i.png");

        assertThat(output().resultContents(List.of(image)).build().withText("z").resultContents())
                .containsExactly(new TextContent("z"), image);
        assertThat(output().build().withText("z").resultContents()).containsExactly(new TextContent("z"));
    }

    @Test
    void with_text_rejects_null() {
        assertThatThrownBy(() -> ToolInputGuardrailRequest.builder()
                        .toolExecutionRequest(EXEC)
                        .requestParams(PARAMS)
                        .build()
                        .withText(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
        assertThatThrownBy(() -> output().build().withText(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void output_rejects_null_content_elements() {
        assertThatThrownBy(() -> output().resultContents(java.util.Arrays.asList(new TextContent("a"), null))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resultContents");
    }

    @Test
    void output_custom_text_typed_content_is_not_text_content() {
        Content custom = new Content() {
            @Override
            public dev.langchain4j.data.message.ContentType type() {
                return dev.langchain4j.data.message.ContentType.TEXT;
            }
        };
        ToolOutputGuardrailRequest request =
                output().resultContents(List.of(new TextContent("a"), custom)).build();

        assertThat(request.text()).isEqualTo("a");
        assertThat(request.withText("z").resultContents()).containsExactly(new TextContent("z"), custom);
    }

    @Test
    void output_with_contents_replaces_contents_and_error_flag() {
        ToolOutputGuardrailRequest request =
                output().resultContents(List.of(new TextContent("a"))).build();

        ToolOutputGuardrailRequest rewritten = request.withContents(List.of(new TextContent("blocked")), true);

        assertThat(rewritten.resultContents()).containsExactly(new TextContent("blocked"));
        assertThat(rewritten.isError()).isTrue();
        assertThat(rewritten.toolExecutionRequest()).isEqualTo(EXEC);
    }

    @Test
    void output_to_builder_round_trip() {
        ToolSpecification spec = ToolSpecification.builder().name("search").build();
        ToolOutputGuardrailRequest request = output().toolSpecification(spec)
                .toolSource(ToolSource.mcp("s1"))
                .resultContents(List.of(new TextContent("a")))
                .isError(true)
                .build();

        ToolOutputGuardrailRequest copy = request.toBuilder().build();

        assertThat(copy.toolSpecification()).isSameAs(spec);
        assertThat(copy.toolSource()).isEqualTo(ToolSource.mcp("s1"));
        assertThat(copy.resultContents()).containsExactly(new TextContent("a"));
        assertThat(copy.isError()).isTrue();
        assertThat(copy.requestParams()).isSameAs(PARAMS);
    }
}
