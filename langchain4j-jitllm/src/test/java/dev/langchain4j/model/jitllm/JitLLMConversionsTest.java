package dev.langchain4j.model.jitllm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.time.Duration;
import java.util.List;
import org.beehive.jitllm.api.CancellationToken;
import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.GenerationRequest;
import org.beehive.jitllm.api.GenerationResult;
import org.beehive.jitllm.api.GenerationTimings;
import org.beehive.jitllm.api.ToolSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JitLLMConversionsTest {

    @Test
    void should_map_tool_call_with_normalized_arguments() {

        List<ToolExecutionRequest> requests = JitLLMConversions.toToolExecutionRequests(
                List.of(new ChatContent.ToolCall("call-1", "getWeather", "{ \"city\" : \"Munich\" }")));

        assertThat(requests)
                .containsExactly(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("getWeather")
                        .arguments("{\"city\":\"Munich\"}")
                        .build());
    }

    @Test
    void should_keep_order_of_multiple_tool_calls() {

        List<ToolExecutionRequest> requests = JitLLMConversions.toToolExecutionRequests(List.of(
                new ChatContent.ToolCall("call-1", "getTime", "{\"country\":\"France\"}"),
                new ChatContent.ToolCall("call-2", "getWeather", "{\"city\":\"Munich\"}")));

        assertThat(requests).extracting(ToolExecutionRequest::id).containsExactly("call-1", "call-2");
        assertThat(requests).extracting(ToolExecutionRequest::name).containsExactly("getTime", "getWeather");
    }

    @Test
    void should_keep_tool_call_arguments_as_they_are_when_they_are_not_json() {
        assertThat(JitLLMConversions.normalizeJson("not json")).isEqualTo("not json");
    }

    @Test
    void should_keep_unicode_in_tool_call_arguments() {
        assertThat(JitLLMConversions.normalizeJson("{\"city\":\"München\"}")).isEqualTo("{\"city\":\"München\"}");
    }

    @Test
    void should_map_messages_to_engine_roles() {

        List<org.beehive.jitllm.api.ChatMessage> converted = JitLLMConversions.toEngineMessages(List.of(
                SystemMessage.from("be brief"),
                UserMessage.from("hello"),
                AiMessage.from("hi"),
                ToolExecutionResultMessage.from("call-1", "getWeather", "sunny")));

        assertThat(converted)
                .extracting(org.beehive.jitllm.api.ChatMessage::role)
                .containsExactly(ChatRole.SYSTEM, ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL);
    }

    @Test
    void should_map_assistant_message_with_text_and_tool_call() {

        AiMessage aiMessage = AiMessage.builder()
                .text("Let me check.")
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("getWeather")
                        .arguments("{\"city\":\"Munich\"}")
                        .build()))
                .build();

        org.beehive.jitllm.api.ChatMessage converted = JitLLMConversions.toAssistantMessage(aiMessage);

        assertThat(converted.content())
                .containsExactly(
                        new ChatContent.Text("Let me check."),
                        new ChatContent.ToolCall("call-1", "getWeather", "{\"city\":\"Munich\"}"));
    }

    @Test
    void should_map_empty_assistant_message_to_empty_text() {
        assertThat(JitLLMConversions.toAssistantMessage(AiMessage.from("")).content())
                .containsExactly(new ChatContent.Text(""));
    }

    @Test
    void should_generate_tool_call_id_when_missing_and_keep_it_matched_with_result() {

        AiMessage aiMessage = AiMessage.from(ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("")
                .build());

        ChatContent.ToolCall call = (ChatContent.ToolCall)
                JitLLMConversions.toAssistantMessage(aiMessage).content().get(0);

        assertThat(call.id()).startsWith("call_");
        assertThat(call.argumentsJson()).isEqualTo("{}");
    }

    @Test
    void should_unwrap_tool_result_that_is_a_json_string() {
        assertThat(JitLLMConversions.unwrapToolResult("\"sunny\"")).isEqualTo("sunny");
        assertThat(JitLLMConversions.unwrapToolResult("\"sunny\" is the answer")).isEqualTo("\"sunny\" is the answer");
        assertThat(JitLLMConversions.unwrapToolResult("{\"t\":1}")).isEqualTo("{\"t\":1}");
        assertThat(JitLLMConversions.unwrapToolResult(null)).isEmpty();
    }

    @Test
    void should_map_tool_specification() {

        List<ToolSpec> toolSpecs = JitLLMConversions.toEngineTools(
                List.of(ToolSpecification.builder().name("getTime").build()));

        assertThat(toolSpecs)
                .containsExactly(new ToolSpec("getTime", "", "{\"type\":\"object\",\"properties\":{}}"));
    }

    @Test
    void should_map_request_parameters_to_generation_request() {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hello"))
                .parameters(ChatRequestParameters.builder()
                        .temperature(0.7)
                        .topP(0.8)
                        .maxOutputTokens(100)
                        .stopSequences(List.of("STOP"))
                        .toolSpecifications(
                                ToolSpecification.builder().name("getTime").build())
                        .build())
                .build();
        CancellationToken cancellationToken = new CancellationToken();

        GenerationRequest request = JitLLMConversions.toGenerationRequest(chatRequest, 42, null, cancellationToken);

        assertThat(request.temperature()).isEqualTo(0.7f);
        assertThat(request.topP()).isEqualTo(0.8f);
        assertThat(request.maxNewTokens()).isEqualTo(100);
        assertThat(request.stopSequences()).containsExactly("STOP");
        assertThat(request.tools()).extracting(ToolSpec::name).containsExactly("getTime");
        assertThat(request.seed()).isEqualTo(42L);
        assertThat(request.cancellation()).isSameAs(cancellationToken);
    }

    @Test
    void should_leave_engine_defaults_when_request_parameters_are_not_set() {

        ChatRequest chatRequest =
                ChatRequest.builder().messages(UserMessage.from("hello")).build();

        GenerationRequest request = JitLLMConversions.toGenerationRequest(chatRequest, null, null, null);
        GenerationRequest engineDefaults = GenerationRequest.builder()
                .messages(request.messages())
                .build();

        assertThat(request.temperature()).isEqualTo(engineDefaults.temperature());
        assertThat(request.topP()).isEqualTo(engineDefaults.topP());
        assertThat(request.maxNewTokens()).isEqualTo(engineDefaults.maxNewTokens());
        assertThat(request.seed()).isNull();
        assertThat(request.tools()).isEmpty();
    }

    @Test
    void should_let_builder_values_take_precedence_over_default_request_parameters() {

        ChatRequestParameters parameters = JitLLMConversions.defaultRequestParameters(
                ChatRequestParameters.builder()
                        .temperature(0.1)
                        .topP(0.2)
                        .maxOutputTokens(10)
                        .build(),
                0.9,
                null,
                null,
                List.of("STOP"));

        assertThat(parameters.temperature()).isEqualTo(0.9);
        assertThat(parameters.topP()).isEqualTo(0.2);
        assertThat(parameters.maxOutputTokens()).isEqualTo(10);
        assertThat(parameters.stopSequences()).containsExactly("STOP");
    }

    @Test
    void should_fail_when_parameters_are_not_supported() {
        assertThatThrownBy(() -> JitLLMConversions.validate(
                        ChatRequestParameters.builder().modelName("other").build()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("modelName");
        assertThatThrownBy(() -> JitLLMConversions.validate(
                        ChatRequestParameters.builder().topK(5).build()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("topK");
        assertThatThrownBy(() -> JitLLMConversions.validate(ChatRequestParameters.builder()
                        .toolChoice(ToolChoice.REQUIRED)
                        .build()))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("REQUIRED");
    }

    @Test
    void should_map_token_usage() {

        GenerationResult result =
                new GenerationResult(
                "Berlin",
                12,
                3,
                org.beehive.jitllm.api.FinishReason.STOP_TOKEN,
                new GenerationTimings(Duration.ZERO, Duration.ZERO, 12, 3));

        assertThat(JitLLMConversions.toTokenUsage(result)).isEqualTo(new TokenUsage(12, 3));
    }

    @ParameterizedTest
    @EnumSource(org.beehive.jitllm.api.FinishReason.class)
    void should_map_finish_reason(org.beehive.jitllm.api.FinishReason finishReason) {

        FinishReason expected =
                switch (finishReason) {
                    case TOOL_CALL -> FinishReason.TOOL_EXECUTION;
                    case MAX_TOKENS, CONTEXT_FULL -> FinishReason.LENGTH;
                    case STOP_TOKEN, STOP_SEQUENCE -> FinishReason.STOP;
                    case CANCELLED -> FinishReason.OTHER;
                };

        assertThat(JitLLMConversions.toFinishReason(finishReason)).isEqualTo(expected);
    }
}
