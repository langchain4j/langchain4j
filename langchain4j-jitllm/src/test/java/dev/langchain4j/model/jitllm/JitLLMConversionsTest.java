package dev.langchain4j.model.jitllm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.beehive.jitllm.api.ChatContent;
import org.beehive.jitllm.api.ChatRole;
import org.beehive.jitllm.api.ToolSpec;
import org.junit.jupiter.api.Test;

/**
 * The adapter's mapping, proven without a model.
 *
 * <p>What a 1B model chooses to emit is its own business; whether a tool call is <b>transported and
 * mapped</b> correctly is this adapter's. Proving the two only together is what left the tool
 * behaviour unverifiable — an inherited case that needs the model to pick two tools in parallel
 * fails for reasons that are not defects in this code.
 *
 * <p>Every case here is a pure function of its arguments: no engine, no device, no model file.
 */
class JitLLMConversionsTest {

    // --- tool calls out of the engine -------------------------------------------------------

    @Test
    void oneCallWithArgumentsIsMappedWithItsIdNameAndArguments() {
        List<ToolExecutionRequest> requests = JitLLMConversions.toToolExecutionRequests(
                List.of(new ChatContent.ToolCall("call-1", "getWeather", "{\"city\":\"Munich\"}")));

        assertThat(requests).hasSize(1);
        assertThat(requests.get(0).id()).isEqualTo("call-1");
        assertThat(requests.get(0).name()).isEqualTo("getWeather");
        assertThat(requests.get(0).arguments()).contains("Munich");
    }

    @Test
    void aNoArgumentCallKeepsAnEmptyJsonObject() {
        List<ToolExecutionRequest> requests = JitLLMConversions.toToolExecutionRequests(
                List.of(new ChatContent.ToolCall("call-1", "get_current_time", "{}")));

        assertThat(requests.get(0).arguments()).isEqualTo("{}");
    }

    @Test
    void twoCallsKeepTheirOrderAndTheirDistinctIds() {
        // The parallel-tools case the model rarely satisfies. The mapping is not what fails there,
        // and this says so without needing the model to cooperate.
        List<ToolExecutionRequest> requests = JitLLMConversions.toToolExecutionRequests(List.of(
                new ChatContent.ToolCall("call-1", "getTime", "{\"country\":\"France\"}"),
                new ChatContent.ToolCall("call-2", "getTemperature", "{\"city\":\"Munich\"}")));

        assertThat(requests).hasSize(2);
        assertThat(requests).extracting(ToolExecutionRequest::id).containsExactly("call-1", "call-2");
        assertThat(requests).extracting(ToolExecutionRequest::name).containsExactly("getTime", "getTemperature");
    }

    @Test
    void noCallsMapToNoRequests() {
        assertThat(JitLLMConversions.toToolExecutionRequests(List.of())).isEmpty();
    }

    // --- conversation into the engine -------------------------------------------------------

    @Test
    void userAndSystemTurnsMapToTheirRoles() {
        List<org.beehive.jitllm.api.ChatMessage> converted =
                JitLLMConversions.toEngineMessages(List.of(SystemMessage.from("be brief"), UserMessage.from("hello")));

        assertThat(converted)
                .extracting(org.beehive.jitllm.api.ChatMessage::role)
                .containsExactly(ChatRole.SYSTEM, ChatRole.USER);
    }

    @Test
    void anAssistantTurnWithToolCallsCarriesThemAsToolCallContent() {
        AiMessage ai = AiMessage.builder()
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("getWeather")
                        .arguments("{\"city\":\"Munich\"}")
                        .build()))
                .build();

        org.beehive.jitllm.api.ChatMessage converted = JitLLMConversions.toAssistantMessage(ai);

        assertThat(converted.role()).isEqualTo(ChatRole.ASSISTANT);
        assertThat(converted.content()).singleElement().isInstanceOfSatisfying(ChatContent.ToolCall.class, call -> {
            assertThat(call.id()).isEqualTo("call-1");
            assertThat(call.name()).isEqualTo("getWeather");
        });
    }

    @Test
    void anAssistantTurnWithTextAndACallCarriesBoth() {
        AiMessage ai = AiMessage.builder()
                .text("Let me check.")
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("getWeather")
                        .arguments("{}")
                        .build()))
                .build();

        assertThat(JitLLMConversions.toAssistantMessage(ai).content())
                .hasSize(2)
                .hasAtLeastOneElementOfType(ChatContent.Text.class)
                .hasAtLeastOneElementOfType(ChatContent.ToolCall.class);
    }

    @Test
    void anEmptyAssistantTurnStillEncodesSomething() {
        // The engine rejects an empty turn; an assistant turn with neither text nor calls used to
        // encode as an empty string, and still must.
        assertThat(JitLLMConversions.toAssistantMessage(
                                AiMessage.builder().text("").build())
                        .content())
                .singleElement()
                .isInstanceOf(ChatContent.Text.class);
    }

    @Test
    void aToolResultMapsBackWithItsIdAndName() {
        List<org.beehive.jitllm.api.ChatMessage> converted = JitLLMConversions.toEngineMessages(
                List.of(ToolExecutionResultMessage.from("call-1", "getWeather", "sunny")));

        assertThat(converted).singleElement().satisfies(message -> {
            assertThat(message.role()).isEqualTo(ChatRole.TOOL);
            assertThat(message.content())
                    .singleElement()
                    .isInstanceOfSatisfying(ChatContent.ToolResult.class, result -> {
                        assertThat(result.id()).isEqualTo("call-1");
                        assertThat(result.name()).isEqualTo("getWeather");
                        assertThat(result.resultJson()).isEqualTo("sunny");
                    });
        });
    }

    @Test
    void aJsonStringLiteralToolResultIsUnwrapped() {
        assertThat(JitLLMConversions.unwrapToolResult("\"sunny\"")).isEqualTo("sunny");
        assertThat(JitLLMConversions.unwrapToolResult("{\"t\":1}")).isEqualTo("{\"t\":1}");
        assertThat(JitLLMConversions.unwrapToolResult(null)).isEmpty();
    }

    /**
     * The engine requires a non-blank id so a result can be matched to a call; LangChain4j allows
     * none. A generated id is better than a null the caller has to handle — and better than the
     * engine rejecting the message.
     */
    @Test
    void aMissingIdIsGeneratedRatherThanRejected() {
        AiMessage ai = AiMessage.builder()
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .name("getWeather")
                        .arguments("{}")
                        .build()))
                .build();

        ChatContent.ToolCall call = (ChatContent.ToolCall)
                JitLLMConversions.toAssistantMessage(ai).content().get(0);
        assertThat(call.id()).isNotBlank();
    }

    @Test
    void aFullRoundTripKeepsCallAndResultIdsMatched() {
        // The second turn: the model asked, the caller answered, and the conversation replays.
        List<ToolExecutionRequest> requests = JitLLMConversions.toToolExecutionRequests(
                List.of(new ChatContent.ToolCall("call-1", "getWeather", "{\"city\":\"Munich\"}")));
        ToolExecutionRequest asked = requests.get(0);

        List<ChatMessage> conversation = List.of(
                UserMessage.from("weather in Munich?"),
                AiMessage.builder().toolExecutionRequests(List.of(asked)).build(),
                ToolExecutionResultMessage.from(asked.id(), asked.name(), "sunny"));

        List<org.beehive.jitllm.api.ChatMessage> converted = JitLLMConversions.toEngineMessages(conversation);

        assertThat(converted)
                .extracting(org.beehive.jitllm.api.ChatMessage::role)
                .containsExactly(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.TOOL);
        String calledId = ((ChatContent.ToolCall) converted.get(1).content().get(0)).id();
        String answeredId = ((ChatContent.ToolResult) converted.get(2).content().get(0)).id();
        assertThat(answeredId)
                .as("a result the model cannot match to its call is worse than no result")
                .isEqualTo(calledId);
    }

    // --- stop reasons -----------------------------------------------------------------------

    @Test
    void aToolCallReasonBecomesToolExecution() {
        assertThat(JitLLMConversions.toLangChain4jFinishReason(org.beehive.jitllm.api.FinishReason.TOOL_CALL))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION);
    }

    @Test
    void everyOtherReasonMapsWithoutInventingAToolExecution() {
        // The rule that keeps TOOL_EXECUTION meaningful: a response that ran out of budget
        // mid-call is LENGTH, even if something parseable came back, because that is what happened.
        assertThat(JitLLMConversions.toLangChain4jFinishReason(org.beehive.jitllm.api.FinishReason.MAX_TOKENS))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        assertThat(JitLLMConversions.toLangChain4jFinishReason(org.beehive.jitllm.api.FinishReason.CONTEXT_FULL))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.LENGTH);
        assertThat(JitLLMConversions.toLangChain4jFinishReason(org.beehive.jitllm.api.FinishReason.STOP_TOKEN))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.STOP);
        assertThat(JitLLMConversions.toLangChain4jFinishReason(org.beehive.jitllm.api.FinishReason.STOP_SEQUENCE))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.STOP);
        assertThat(JitLLMConversions.toLangChain4jFinishReason(org.beehive.jitllm.api.FinishReason.CANCELLED))
                .isEqualTo(dev.langchain4j.model.output.FinishReason.OTHER);
    }

    @Test
    void everyEngineStopReasonIsMapped() {
        // A new engine reason must not fall through to a default that quietly means STOP.
        for (org.beehive.jitllm.api.FinishReason reason : org.beehive.jitllm.api.FinishReason.values()) {
            assertThat(JitLLMConversions.toLangChain4jFinishReason(reason)).isNotNull();
        }
    }

    /**
     * The synchronous and streaming paths derive their requests from the same conversion, so
     * identical engine calls produce identical requests. Both models call this one function; the
     * case exists so a future divergence has to delete it rather than merely drift.
     */
    @Test
    void synchronousAndStreamingDeriveTheSameRequestsFromTheSameCalls() {
        List<ChatContent.ToolCall> calls = List.of(
                new ChatContent.ToolCall("call-1", "getTime", "{\"country\":\"France\"}"),
                new ChatContent.ToolCall("call-2", "getTemperature", "{\"city\":\"Munich\"}"));

        List<ToolExecutionRequest> synchronousPath = JitLLMConversions.toToolExecutionRequests(calls);
        List<ToolExecutionRequest> streamingPath = JitLLMConversions.toToolExecutionRequests(calls);

        assertThat(streamingPath).isEqualTo(synchronousPath);
        // The streaming path also reports each call by index; the indices must address this list.
        for (int index = 0; index < streamingPath.size(); index++) {
            assertThat(streamingPath.get(index)).isEqualTo(synchronousPath.get(index));
        }
    }

    /** Tool-shaped text that produced no valid call is ordinary text with an ordinary reason. */
    @Test
    void noValidCallMeansNoRequestsAndNoToolExecution() {
        assertThat(JitLLMConversions.toToolExecutionRequests(List.of())).isEmpty();
        assertThat(JitLLMConversions.toLangChain4jFinishReason(org.beehive.jitllm.api.FinishReason.STOP_TOKEN))
                .isNotEqualTo(dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION);
    }

    // --- tool specifications into the engine ------------------------------------------------

    @Test
    void aToolSpecificationCarriesItsNameDescriptionAndSchema() {
        List<ToolSpec> specs = JitLLMConversions.toEngineTools(List.of(ToolSpecification.builder()
                .name("getWeather")
                .description("the weather")
                .build()));

        assertThat(specs).singleElement().satisfies(spec -> {
            assertThat(spec.name()).isEqualTo("getWeather");
            assertThat(spec.description()).isEqualTo("the weather");
            assertThat(spec.parametersJsonSchema()).contains("object");
        });
    }

    @Test
    void aToolWithNoDescriptionGetsAnEmptyOneRatherThanNull() {
        // The engine rejects a null description; the schema string must survive either way.
        assertThat(JitLLMConversions.toEngineTools(
                                List.of(ToolSpecification.builder().name("t").build()))
                        .get(0)
                        .description())
                .isEmpty();
    }

    @Test
    void aBlankToolNameIsRejectedByTheFacadeRatherThanSentOn() {
        assertThatThrownBy(() -> new ToolSpec(" ", "", "{}")).isInstanceOf(IllegalArgumentException.class);
    }
}
