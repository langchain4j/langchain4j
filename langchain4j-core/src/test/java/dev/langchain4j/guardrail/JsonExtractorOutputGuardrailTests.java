package dev.langchain4j.guardrail;

import static dev.langchain4j.test.guardrail.GuardrailAssertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.message.AiMessage;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JsonExtractorOutputGuardrailTests {
    private static final String JSON =
            """
            {
                "name": "MyObject",
                "description": "Description of MyObject"
            }""";

    private static final JsonExtractorOutputGuardrail<MyObject> MY_OBJECT_JSON_OUTPUT_GUARDRAIL =
            new JsonExtractorOutputGuardrail<>(MyObject.class);
    private static final JsonExtractorOutputGuardrail<Map<String, MyObject>> MAP_OF_MY_OBJECT_JSON_OUTPUT_GUARDRAIL =
            new JsonExtractorOutputGuardrail<>(new TypeReference<>() {});

    @ParameterizedTest
    @MethodSource("guardrails")
    void successfulValidation(String json, JsonExtractorOutputGuardrail<?> guardrail, Object expectedResult) {
        var guardrailSpy = spy(guardrail);
        var result = guardrailSpy.validate(AiMessage.from(json));

        assertThat(result)
                .isNotNull()
                .extracting(
                        OutputGuardrailResult::result,
                        OutputGuardrailResult::successfulText,
                        OutputGuardrailResult::successfulResult)
                .containsExactly(GuardrailResult.Result.SUCCESS_WITH_RESULT, json, expectedResult);

        verify(guardrailSpy).deserialize(json);
    }

    @ParameterizedTest
    @MethodSource("guardrails")
    void successfulValidationAfterTrimming(
            String json, JsonExtractorOutputGuardrail<?> guardrail, Object expectedResult) {
        var input = "abc" + json;
        parseJsonRequiringTrimming(json, guardrail, expectedResult, input);
    }

    @ParameterizedTest
    @MethodSource("guardrails")
    void successfulValidationAfterTrimmingWithInvalidJson(
            String json, JsonExtractorOutputGuardrail<?> guardrail, Object expectedResult) {
        var input = "abc [test] {\"key\":\"value\"} " + json + " [another] xyz";
        parseJsonRequiringTrimming(json, guardrail, expectedResult, input);
    }

    private void parseJsonRequiringTrimming(
            String json, JsonExtractorOutputGuardrail<?> guardrail, Object expectedResult, String input) {
        var guardrailSpy = spy(guardrail);
        var result = guardrailSpy.validate(AiMessage.from(input));

        assertThat(result)
                .isNotNull()
                .extracting(
                        OutputGuardrailResult::result,
                        OutputGuardrailResult::successfulText,
                        OutputGuardrailResult::successfulResult)
                .containsExactly(GuardrailResult.Result.SUCCESS_WITH_RESULT, json, expectedResult);

        verify(guardrailSpy).deserialize(input);
    }

    @Test
    void invalidJson() {
        var guardrail = spy(MY_OBJECT_JSON_OUTPUT_GUARDRAIL);
        var input = "{{" + JSON;
        var result = guardrail.validate(AiMessage.from(input));

        assertThat(result)
                .hasSingleFailureWithMessageAndReprompt(
                        JsonExtractorOutputGuardrail.DEFAULT_REPROMPT_MESSAGE,
                        JsonExtractorOutputGuardrail.DEFAULT_REPROMPT_PROMPT);

        verify(guardrail).deserialize(input);
        verify(guardrail).invokeInvalidJson(any(AiMessage.class), eq(input));
    }

    static Stream<Arguments> guardrails() {
        var result = new MyObject("MyObject", "Description of MyObject");

        return Stream.of(
                Arguments.of(JSON, MY_OBJECT_JSON_OUTPUT_GUARDRAIL, result),
                Arguments.of(
                        "{ \"myObject\": %s}".formatted(JSON),
                        MAP_OF_MY_OBJECT_JSON_OUTPUT_GUARDRAIL,
                        Map.of("myObject", result)));
    }

    record MyObject(String name, String description) {}
}
