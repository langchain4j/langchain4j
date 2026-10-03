package dev.langchain4j.model.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BedrockBatchConverseMapperTest {

    private static Map<String, Object> modelInput(ChatRequest request) {
        return BedrockBatchConverseMapper.toModelInput(request, Map.of(), true);
    }

    private static ChatResponse chatResponse(String modelOutputJson, boolean returnThinking) {
        return BedrockBatchConverseMapper.toChatResponse(
                BedrockBatchConverseMapper.fromJsonLine(modelOutputJson), "model-x", returnThinking);
    }

    @Test
    void should_write_a_record_on_a_single_line() {
        String line = BedrockBatchConverseMapper.toJsonLine(Map.of(
                "recordId",
                "r0000000000",
                "modelInput",
                modelInput(ChatRequest.builder()
                        .messages(SystemMessage.from("be brief"), UserMessage.from("hi"))
                        .build())));

        assertThat(line).doesNotContain("\n");
    }

    @Test
    void should_build_the_messages_and_inference_config() {
        Map<String, Object> modelInput = modelInput(ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(ChatRequestParameters.builder()
                        .maxOutputTokens(100)
                        .temperature(0.5)
                        .topP(0.9)
                        .stopSequences(List.of("END"))
                        .build())
                .build());

        assertThat(modelInput.get("messages"))
                .isEqualTo(List.of(Map.of("role", "user", "content", List.of(Map.of("text", "hi")))));
        assertThat(modelInput.get("inferenceConfig"))
                .isEqualTo(Map.of("maxTokens", 100, "temperature", 0.5, "topP", 0.9, "stopSequences", List.of("END")));
        assertThat(modelInput).doesNotContainKeys("system", "additionalModelRequestFields", "modelId");
    }

    @Test
    void should_put_system_messages_into_the_system_field() {
        Map<String, Object> modelInput = modelInput(ChatRequest.builder()
                .messages(SystemMessage.from("be brief"), UserMessage.from("hi"))
                .build());

        assertThat(modelInput.get("system")).isEqualTo(List.of(Map.of("text", "be brief")));
        assertThat(modelInput.get("messages"))
                .isEqualTo(List.of(Map.of("role", "user", "content", List.of(Map.of("text", "hi")))));
    }

    @Test
    void should_put_the_text_of_a_bedrock_system_message_into_the_system_field() {
        Map<String, Object> modelInput = modelInput(ChatRequest.builder()
                .messages(
                        BedrockSystemMessage.builder()
                                .addText("be brief")
                                .addText("answer in English")
                                .build(),
                        UserMessage.from("hi"))
                .build());

        assertThat(modelInput.get("system"))
                .isEqualTo(List.of(Map.of("text", "be brief"), Map.of("text", "answer in English")));
    }

    @Test
    void should_reject_a_bedrock_system_message_with_a_cache_point() {
        ChatRequest request = ChatRequest.builder()
                .messages(
                        BedrockSystemMessage.builder()
                                .addTextWithCachePoint("large static context")
                                .build(),
                        UserMessage.from("hi"))
                .build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> modelInput(request))
                .withMessage("Prompt caching is not supported by Bedrock batch inference");
    }

    @Test
    void should_send_image_content_as_base64_bytes() {
        Map<String, Object> modelInput = modelInput(ChatRequest.builder()
                .messages(UserMessage.from(TextContent.from("describe"), ImageContent.from("aGVsbG8=", "image/png")))
                .build());

        assertThat(modelInput.get("messages"))
                .isEqualTo(List.of(Map.of(
                        "role",
                        "user",
                        "content",
                        List.of(
                                Map.of("text", "describe"),
                                Map.of("image", Map.of("format", "png", "source", Map.of("bytes", "aGVsbG8=")))))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void should_name_a_pdf_document_after_its_file_like_bedrock_chat_model() {
        PdfFile pdf = PdfFile.builder()
                .base64Data("JVBERi0=")
                .url("https://example.com/reports/quarterly-report.pdf")
                .build();

        Map<String, Object> modelInput = modelInput(ChatRequest.builder()
                .messages(UserMessage.from(PdfFileContent.from(pdf)))
                .build());

        Map<String, Object> message = (Map<String, Object>) ((List<?>) modelInput.get("messages")).get(0);
        Map<String, Object> block = (Map<String, Object>) ((List<?>) message.get("content")).get(0);
        assertThat(block.get("document"))
                .isEqualTo(Map.of("format", "pdf", "name", "quarterly-report", "source", Map.of("bytes", "JVBERi0=")));
    }

    @Test
    void should_send_the_additional_model_request_fields() {
        Map<String, Object> modelInput = BedrockBatchConverseMapper.toModelInput(
                ChatRequest.builder().messages(UserMessage.from("hi")).build(), Map.of("top_k", 10), true);

        assertThat(modelInput.get("additionalModelRequestFields")).isEqualTo(Map.of("top_k", 10));
    }

    @Test
    void should_send_thinking_with_its_signature_back_when_enabled() {
        AiMessage aiMessage = AiMessage.builder()
                .text("answer")
                .thinking("hmm")
                .attributes(Map.of("thinking_signature", "sig"))
                .build();
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("q"), aiMessage, UserMessage.from("again"))
                .build();

        assertThat(((List<?>) BedrockBatchConverseMapper.toModelInput(request, Map.of(), true)
                                .get("messages"))
                        .get(1))
                .isEqualTo(Map.of(
                        "role",
                        "assistant",
                        "content",
                        List.of(
                                Map.of(
                                        "reasoningContent",
                                        Map.of("reasoningText", Map.of("text", "hmm", "signature", "sig"))),
                                Map.of("text", "answer"))));
        assertThat(((List<?>) BedrockBatchConverseMapper.toModelInput(request, Map.of(), false)
                                .get("messages"))
                        .get(1))
                .isEqualTo(Map.of("role", "assistant", "content", List.of(Map.of("text", "answer"))));
    }

    @Test
    void should_accept_an_ai_message_that_carries_only_thinking() {
        ChatRequest request = ChatRequest.builder()
                .messages(
                        UserMessage.from("q"),
                        AiMessage.builder().thinking("hmm").build(),
                        UserMessage.from("again"))
                .build();

        assertThat(((List<?>) modelInput(request).get("messages"))).hasSize(3);
    }

    @Test
    void should_reject_an_ai_message_with_tool_execution_requests() {
        ChatRequest request = ChatRequest.builder()
                .messages(
                        UserMessage.from("q"),
                        AiMessage.from(ToolExecutionRequest.builder()
                                .id("1")
                                .name("tool")
                                .arguments("{}")
                                .build()))
                .build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> modelInput(request))
                .withMessage("Tool calling is not supported by Bedrock batch inference");
    }

    @Test
    void should_reject_a_tool_execution_result_message() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from("q"), ToolExecutionResultMessage.from("1", "tool", "result"))
                .build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> modelInput(request))
                .withMessage("Tool calling is not supported by Bedrock batch inference");
    }

    @Test
    void should_name_the_batch_model_as_the_limit_for_content_it_does_not_map() {
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(AudioContent.from("aGVsbG8=", "audio/wav")))
                .build();

        assertThatExceptionOfType(UnsupportedFeatureException.class)
                .isThrownBy(() -> modelInput(request))
                .withMessage("AUDIO content is not supported by BedrockBatchChatModel");
    }

    @Test
    void should_parse_the_text_finish_reason_and_token_usage() {
        ChatResponse response = chatResponse(
                "{\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"hello\"}]}},"
                        + "\"stopReason\":\"end_turn\",\"usage\":{\"inputTokens\":7,\"outputTokens\":3,"
                        + "\"totalTokens\":10,\"cacheReadInputTokenCount\":0,\"cacheWriteInputTokenCount\":0}}",
                false);

        assertThat(response.aiMessage().text()).isEqualTo("hello");
        assertThat(response.metadata().finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.metadata().modelName()).isEqualTo("model-x");
        BedrockTokenUsage tokenUsage = (BedrockTokenUsage) response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(7);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(3);
    }

    @Test
    void should_join_text_blocks_with_a_blank_line() {
        ChatResponse response = chatResponse(
                "{\"output\":{\"message\":{\"content\":[{\"text\":\"one\"},{\"text\":\"two\"}]}},"
                        + "\"stopReason\":\"end_turn\"}",
                false);

        assertThat(response.aiMessage().text()).isEqualTo("one\n\ntwo");
    }

    @Test
    void should_leave_the_text_empty_when_the_output_has_none() {
        ChatResponse response =
                chatResponse("{\"output\":{\"message\":{\"content\":[]}},\"stopReason\":\"max_tokens\"}", false);

        assertThat(response.aiMessage().text()).isNull();
    }

    @Test
    void should_return_thinking_and_its_signature_only_when_enabled() {
        String modelOutput = "{\"output\":{\"message\":{\"content\":["
                + "{\"reasoningContent\":{\"reasoningText\":{\"text\":\"hmm\",\"signature\":\"sig\"}}},"
                + "{\"text\":\"answer\"}]}},\"stopReason\":\"end_turn\"}";

        AiMessage withThinking = chatResponse(modelOutput, true).aiMessage();
        assertThat(withThinking.thinking()).isEqualTo("hmm");
        assertThat(withThinking.attribute("thinking_signature", String.class)).isEqualTo("sig");
        assertThat(withThinking.text()).isEqualTo("answer");

        AiMessage withoutThinking = chatResponse(modelOutput, false).aiMessage();
        assertThat(withoutThinking.thinking()).isNull();
        assertThat(withoutThinking.attributes()).isEmpty();
        assertThat(withoutThinking.text()).isEqualTo("answer");
    }

    @ParameterizedTest
    @CsvSource({
        "end_turn, STOP",
        "stop_sequence, STOP",
        "max_tokens, LENGTH",
        "model_context_window_exceeded, LENGTH",
        "tool_use, TOOL_EXECUTION",
        "content_filtered, CONTENT_FILTER",
        "guardrail_intervened, CONTENT_FILTER",
        "something_new, OTHER"
    })
    void should_map_the_stop_reason(String stopReason, FinishReason expected) {
        ChatResponse response = chatResponse(
                "{\"output\":{\"message\":{\"content\":[{\"text\":\"x\"}]}},\"stopReason\":\"" + stopReason + "\"}",
                false);

        assertThat(response.metadata().finishReason()).isEqualTo(expected);
    }
}
