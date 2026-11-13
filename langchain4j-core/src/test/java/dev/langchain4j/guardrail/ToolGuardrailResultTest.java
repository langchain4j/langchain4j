package dev.langchain4j.guardrail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolGuardrailResultTest {

    private static final ToolExecutionRequest EXEC = ToolExecutionRequest.builder()
            .id("id1")
            .name("search")
            .arguments("{\"q\":\"a\"}")
            .build();
    private static final GuardrailRequestParams PARAMS = GuardrailRequestParams.builder()
            .userMessageTemplate("t")
            .variables(Map.of())
            .build();
    private static final ImageContent IMAGE = ImageContent.from("http://example.com/a.png");

    private static final ToolInputGuardrail INPUT = request -> ToolInputGuardrailResult.success();
    private static final ToolOutputGuardrail OUTPUT = request -> ToolOutputGuardrailResult.success();

    private static ToolOutputGuardrailRequest outputRequest(boolean isError, Content... contents) {
        return ToolOutputGuardrailRequest.builder()
                .toolExecutionRequest(EXEC)
                .requestParams(PARAMS)
                .resultContents(List.of(contents))
                .isError(isError)
                .build();
    }

    @Test
    void input_success_is_a_singleton_without_rewrite() {
        var result = ToolInputGuardrailResult.success();

        assertThat(result).isSameAs(INPUT.success());
        assertThat(result.result()).isEqualTo(GuardrailResult.Result.SUCCESS);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
        assertThat(result.isFatal()).isFalse();
        assertThat(result.successfulText()).isNull();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void input_success_with_carries_rewritten_arguments() {
        var result = INPUT.successWith("{\"q\":\"b\"}");

        assertThat(result.result()).isEqualTo(GuardrailResult.Result.SUCCESS_WITH_RESULT);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isTrue();
        assertThat(result.successfulText()).isEqualTo("{\"q\":\"b\"}");
        assertThat(result.asString()).isEqualTo("Success with '{\"q\":\"b\"}'");
    }

    @Test
    void input_success_with_null_is_plain_success() {
        assertThat(ToolInputGuardrailResult.successWith(null)).isSameAs(ToolInputGuardrailResult.success());
    }

    @Test
    void input_failure_is_not_success_and_not_fatal() {
        var cause = new IllegalStateException("boom");
        var result = INPUT.failure("not allowed", cause);

        assertThat(result.result()).isEqualTo(GuardrailResult.Result.FAILURE);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.isFatal()).isFalse();
        assertThat(result.hasRewrittenResult()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).message()).isEqualTo("not allowed");
        assertThat(result.getFirstFailureException()).isSameAs(cause);
        assertThat(INPUT.failure("no cause").getFirstFailureException()).isNull();
    }

    @Test
    void input_fatal_is_fatal() {
        var cause = new IllegalStateException("boom");
        var result = INPUT.fatal("stop", cause);

        assertThat(result.result()).isEqualTo(GuardrailResult.Result.FATAL);
        assertThat(result.isFatal()).isTrue();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.getFirstFailureException()).isSameAs(cause);
        assertThat(INPUT.fatal("stop").isFatal()).isTrue();
    }

    @Test
    void input_failure_requires_message() {
        assertThatThrownBy(() -> INPUT.failure(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void input_validated_by_records_the_guardrail_class() {
        var result = INPUT.failure("nope").validatedBy(INPUT.getClass());

        assertThat(result.failures().get(0).guardrailClass()).isEqualTo(INPUT.getClass());
        assertThat(result.failures().get(0).message()).isEqualTo("nope");
        assertThat(result.failures().get(0).asString())
                .contains(INPUT.getClass().getName())
                .contains("nope");
    }

    @Test
    void input_aggregated_failures_via_package_constructor() {
        var first = new ToolInputGuardrailResult.Failure("one");
        var second = new ToolInputGuardrailResult.Failure("two");
        var result = new ToolInputGuardrailResult(new ArrayList<>(List.of(first, second)), false);

        assertThat(result.failures()).hasSize(2);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.asString()).contains("one").contains("two");
    }

    @Test
    void input_equals_and_hash_code() {
        assertThat(INPUT.successWith("{}"))
                .isEqualTo(INPUT.successWith("{}"))
                .hasSameHashCodeAs(INPUT.successWith("{}"));
        assertThat(INPUT.successWith("{}")).isNotEqualTo(INPUT.successWith("{\"a\":1}"));
        assertThat(INPUT.successWith("{}")).isNotEqualTo(INPUT.success());
    }

    @Test
    void output_success_is_a_singleton_without_rewrite() {
        var result = OUTPUT.success();

        assertThat(result).isSameAs(ToolOutputGuardrailResult.success());
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.hasRewrittenResult()).isFalse();
        assertThat(result.hasRewrittenContents()).isFalse();
        assertThat(result.successfulText()).isNull();
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void output_text_rewrite_keeps_other_contents_and_error_flag() {
        var request = outputRequest(true, new TextContent("old"), IMAGE);
        var result = OUTPUT.successWith("new");

        assertThat(result.hasRewrittenResult()).isTrue();
        assertThat(result.hasRewrittenContents()).isFalse();
        assertThat(result.successfulText()).isEqualTo("new");
        assertThat(result.resultContents(request)).containsExactly(new TextContent("new"), IMAGE);
        assertThat(result.isError(request)).isTrue();
    }

    @Test
    void output_text_rewrite_with_null_is_plain_success() {
        assertThat(ToolOutputGuardrailResult.successWith((String) null)).isSameAs(ToolOutputGuardrailResult.success());
    }

    @Test
    void output_contents_rewrite_replaces_contents_and_error_flag() {
        var request = outputRequest(false, new TextContent("old"));
        var result = OUTPUT.successWith(List.of(new TextContent("a"), IMAGE, new TextContent("b")), true);

        assertThat(result.hasRewrittenResult()).isTrue();
        assertThat(result.hasRewrittenContents()).isTrue();
        assertThat(result.successfulText()).isEqualTo("a\nb");
        assertThat(result.resultContents(request)).containsExactly(new TextContent("a"), IMAGE, new TextContent("b"));
        assertThat(result.isError(request)).isTrue();
    }

    @Test
    void output_contents_rewrite_can_clear_the_error_flag() {
        var request = outputRequest(true, new TextContent("old"));
        var result = OUTPUT.successWith(List.of(IMAGE), false);

        assertThat(result.successfulText()).isEmpty();
        assertThat(result.isError(request)).isFalse();
    }

    @Test
    void output_contents_rewrite_validates_arguments_and_copies_the_list() {
        assertThatThrownBy(() -> OUTPUT.successWith(null, false)).isInstanceOf(IllegalArgumentException.class);

        var withNull = new ArrayList<Content>();
        withNull.add(null);
        assertThatThrownBy(() -> OUTPUT.successWith(withNull, false)).isInstanceOf(IllegalArgumentException.class);

        var contents = new ArrayList<Content>(List.of(new TextContent("a")));
        var result = OUTPUT.successWith(contents, false);
        contents.add(IMAGE);
        assertThat(result.resultContents(outputRequest(false, new TextContent("x"))))
                .hasSize(1);
    }

    @Test
    void output_without_rewrite_reads_through_to_the_request() {
        var request = outputRequest(true, new TextContent("old"), IMAGE);
        var result = OUTPUT.success();

        assertThat(result.resultContents(request)).isEqualTo(request.resultContents());
        assertThat(result.isError(request)).isTrue();
        assertThat(result.apply(request)).isSameAs(request);
    }

    @Test
    void output_apply_produces_the_rewritten_request_for_chaining() {
        var request = outputRequest(false, new TextContent("old"), IMAGE);

        var textRewritten = OUTPUT.successWith("new").apply(request);
        assertThat(textRewritten.resultContents()).containsExactly(new TextContent("new"), IMAGE);
        assertThat(textRewritten.isError()).isFalse();

        var contentsRewritten =
                OUTPUT.successWith(List.of(new TextContent("blocked")), true).apply(request);
        assertThat(contentsRewritten.resultContents()).containsExactly(new TextContent("blocked"));
        assertThat(contentsRewritten.isError()).isTrue();
        assertThat(contentsRewritten.toolExecutionRequest()).isEqualTo(EXEC);
    }

    @Test
    void output_failure_and_fatal() {
        var cause = new IllegalStateException("boom");
        var failure = OUTPUT.failure("blocked", cause);
        var fatal = OUTPUT.fatal("stop");

        assertThat(failure.result()).isEqualTo(GuardrailResult.Result.FAILURE);
        assertThat(failure.isSuccess()).isFalse();
        assertThat(failure.isFatal()).isFalse();
        assertThat(failure.failures().get(0).message()).isEqualTo("blocked");
        assertThat(failure.getFirstFailureException()).isSameAs(cause);
        assertThat(fatal.isFatal()).isTrue();
        assertThat(OUTPUT.failure("m").getFirstFailureException()).isNull();
        assertThat(OUTPUT.fatal("m", cause).getFirstFailureException()).isSameAs(cause);
        assertThatThrownBy(() -> OUTPUT.failure(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void output_validated_by_records_the_guardrail_class() {
        var result = OUTPUT.failure("nope").validatedBy(OUTPUT.getClass());

        assertThat(result.failures().get(0).guardrailClass()).isEqualTo(OUTPUT.getClass());
    }

    @Test
    void output_equals_and_hash_code() {
        assertThat(OUTPUT.successWith("a"))
                .isEqualTo(OUTPUT.successWith("a"))
                .hasSameHashCodeAs(OUTPUT.successWith("a"));
        assertThat(OUTPUT.successWith("a")).isNotEqualTo(OUTPUT.successWith("b"));
        assertThat(OUTPUT.successWith(List.of(new TextContent("a")), true))
                .isNotEqualTo(OUTPUT.successWith(List.of(new TextContent("a")), false));
    }

    @Test
    void exceptions_carry_message_and_cause() {
        var cause = new RuntimeException("c");

        assertThat(new ToolInputGuardrailException("m", cause))
                .isInstanceOf(GuardrailException.class)
                .hasMessage("m")
                .hasCause(cause);
        assertThat(new ToolOutputGuardrailException("m"))
                .isInstanceOf(GuardrailException.class)
                .hasMessage("m")
                .hasNoCause();
        assertThat(new ToolInputGuardrailException("m")).hasMessage("m");
        assertThat(new ToolOutputGuardrailException("m", cause)).hasCause(cause);
    }
}
