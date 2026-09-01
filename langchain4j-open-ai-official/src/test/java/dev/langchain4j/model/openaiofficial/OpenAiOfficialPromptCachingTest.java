package dev.langchain4j.model.openaiofficial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.completions.CompletionUsage;
import com.openai.models.responses.EasyInputMessage;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseInputContent;
import com.openai.models.responses.ResponseInputItem;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code gpt-5.6+} prompt caching surface of both OpenAI APIs:
 * {@code prompt_cache_key}, {@code prompt_cache_options}, {@code prompt_cache_breakpoint}
 * and {@code cache_write_tokens}.
 */
class OpenAiOfficialPromptCachingTest {

    private static final OpenAiOfficialResponsesChatRequestParameters RESPONSES_PARAMETERS =
            OpenAiOfficialResponsesChatRequestParameters.builder()
                    .modelName("gpt-5.6")
                    .build();

    private static Map<String, Object> breakpointAttributes() {
        return Map.of(
                OpenAiOfficialPromptCacheBreakpoint.ATTRIBUTE_KEY, OpenAiOfficialPromptCacheBreakpoint.MODE_EXPLICIT);
    }

    private static ChatCompletionCreateParams chatCompletionParams(
            OpenAiOfficialChatRequestParameters parameters, dev.langchain4j.data.message.ChatMessage... messages) {
        ChatRequest chatRequest = ChatRequest.builder().messages(messages).build();
        return InternalOpenAiOfficialHelper.toOpenAiChatCompletionCreateParams(chatRequest, parameters, false, false)
                .messages(InternalOpenAiOfficialHelper.toOpenAiMessages(List.of(messages)))
                .build();
    }

    // ----- Chat Completions: prompt_cache_key / prompt_cache_options -----

    @Test
    void should_send_prompt_cache_key_and_prompt_cache_options_on_chat_completions() {
        ChatCompletionCreateParams params = chatCompletionParams(
                OpenAiOfficialChatRequestParameters.builder()
                        .modelName("gpt-5.6")
                        .promptCacheKey("agent_123_v1")
                        .promptCacheOptions(OpenAiOfficialPromptCacheOptions.builder()
                                .mode(OpenAiOfficialPromptCacheOptions.MODE_EXPLICIT)
                                .ttl(OpenAiOfficialPromptCacheOptions.TTL_30M)
                                .build())
                        .build(),
                UserMessage.from("Hi"));

        assertThat(params.promptCacheKey()).contains("agent_123_v1");
        assertThat(params.promptCacheOptions().orElseThrow().mode())
                .contains(ChatCompletionCreateParams.PromptCacheOptions.Mode.EXPLICIT);
        assertThat(params.promptCacheOptions().orElseThrow().ttl())
                .contains(ChatCompletionCreateParams.PromptCacheOptions.Ttl._30M);
    }

    @Test
    void should_not_send_prompt_cache_fields_on_chat_completions_by_default() {
        ChatCompletionCreateParams params = chatCompletionParams(
                OpenAiOfficialChatRequestParameters.builder()
                        .modelName("gpt-5.6")
                        .build(),
                UserMessage.from("Hi"));

        assertThat(params.promptCacheKey()).isEmpty();
        assertThat(params.promptCacheOptions()).isEmpty();
    }

    @Test
    void should_store_prompt_cache_fields_in_chat_model_default_request_parameters() {
        OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
                .apiKey("test")
                .modelName("gpt-5.6")
                .defaultRequestParameters(OpenAiOfficialChatRequestParameters.builder()
                        .promptCacheKey("key")
                        .promptCacheOptions(OpenAiOfficialPromptCacheOptions.explicit())
                        .build())
                .build();

        OpenAiOfficialChatRequestParameters parameters =
                (OpenAiOfficialChatRequestParameters) model.defaultRequestParameters();
        assertThat(parameters.promptCacheKey()).isEqualTo("key");
        assertThat(parameters.promptCacheOptions()).isEqualTo(OpenAiOfficialPromptCacheOptions.explicit());
    }

    // ----- Chat Completions: prompt_cache_breakpoint -----

    @Test
    void should_send_breakpoint_on_chat_completions_system_message() {
        ChatCompletionMessageParam param = InternalOpenAiOfficialHelper.toOpenAiMessage(SystemMessage.builder()
                .text("Shared instructions")
                .attributes(breakpointAttributes())
                .build());

        var parts = param.asSystem().content().asArrayOfContentParts();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).isEqualTo("Shared instructions");
        assertThat(parts.get(0).promptCacheBreakpoint()).isPresent();
    }

    @Test
    void should_send_chat_completions_system_message_as_plain_string_when_not_marked() {
        ChatCompletionMessageParam param =
                InternalOpenAiOfficialHelper.toOpenAiMessage(SystemMessage.from("Shared instructions"));

        assertThat(param.asSystem().content().text()).contains("Shared instructions");
    }

    @Test
    void should_send_breakpoint_on_single_text_chat_completions_user_message() {
        UserMessage userMessage = UserMessage.from("Long document");
        userMessage.attributes().putAll(breakpointAttributes());

        ChatCompletionMessageParam param = InternalOpenAiOfficialHelper.toOpenAiMessage(userMessage);

        var parts = param.asUser().content().asArrayOfContentParts();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).asText().promptCacheBreakpoint()).isPresent();
    }

    @Test
    void should_send_breakpoint_only_on_last_content_block_of_chat_completions_user_message() {
        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("Describe this"))
                .addContent(ImageContent.from("http://image.url"))
                .attributes(breakpointAttributes())
                .build();

        ChatCompletionMessageParam param = InternalOpenAiOfficialHelper.toOpenAiMessage(userMessage);

        var parts = param.asUser().content().asArrayOfContentParts();
        assertThat(parts).hasSize(2);
        assertThat(parts.get(0).asText().promptCacheBreakpoint()).isEmpty();
        assertThat(parts.get(1).asImageUrl().promptCacheBreakpoint()).isPresent();
    }

    @Test
    void should_send_breakpoint_on_chat_completions_tool_message() {
        ChatCompletionMessageParam param =
                InternalOpenAiOfficialHelper.toOpenAiMessage(ToolExecutionResultMessage.builder()
                        .id("call_123")
                        .toolName("getWeather")
                        .text("Sunny")
                        .attributes(breakpointAttributes())
                        .build());

        var parts = param.asTool().content().asArrayOfContentParts();
        assertThat(parts).hasSize(1);
        assertThat(parts.get(0).text()).isEqualTo("Sunny");
        assertThat(parts.get(0).promptCacheBreakpoint()).isPresent();
    }

    @Test
    void should_fail_when_breakpoint_is_set_on_chat_completions_ai_message() {
        AiMessage aiMessage = AiMessage.builder()
                .text("Hello!")
                .attributes(breakpointAttributes())
                .build();

        assertThatThrownBy(() -> InternalOpenAiOfficialHelper.toOpenAiMessage(aiMessage))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("AiMessage");
    }

    @Test
    void should_fail_when_breakpoint_mode_is_not_supported() {
        SystemMessage systemMessage = SystemMessage.builder()
                .text("Shared instructions")
                .attributes(Map.of(OpenAiOfficialPromptCacheBreakpoint.ATTRIBUTE_KEY, "implicit"))
                .build();

        assertThatThrownBy(() -> InternalOpenAiOfficialHelper.toOpenAiMessage(systemMessage))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt_cache_breakpoint")
                .hasMessageContaining("implicit");
    }

    // ----- Responses: prompt_cache_options -----

    @Test
    void should_send_prompt_cache_options_on_responses() {
        ResponseCreateParams params = OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(
                ChatRequest.builder().messages(UserMessage.from("Hi")).build(),
                OpenAiOfficialResponsesChatRequestParameters.builder()
                        .modelName("gpt-5.6")
                        .promptCacheOptions(OpenAiOfficialPromptCacheOptions.builder()
                                .mode(OpenAiOfficialPromptCacheOptions.MODE_EXPLICIT)
                                .ttl(OpenAiOfficialPromptCacheOptions.TTL_30M)
                                .build())
                        .build());

        assertThat(params.promptCacheOptions().orElseThrow().mode())
                .contains(ResponseCreateParams.PromptCacheOptions.Mode.EXPLICIT);
        assertThat(params.promptCacheOptions().orElseThrow().ttl())
                .contains(ResponseCreateParams.PromptCacheOptions.Ttl._30M);
    }

    @Test
    void should_not_send_prompt_cache_options_on_responses_by_default() {
        ResponseCreateParams params = OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(
                ChatRequest.builder().messages(UserMessage.from("Hi")).build(), RESPONSES_PARAMETERS);

        assertThat(params.promptCacheOptions()).isEmpty();
    }

    @Test
    void should_store_prompt_cache_options_in_responses_model_default_request_parameters() {
        OpenAiOfficialPromptCacheOptions promptCacheOptions = OpenAiOfficialPromptCacheOptions.explicit();

        OpenAiOfficialResponsesChatModel chatModel = OpenAiOfficialResponsesChatModel.builder()
                .apiKey("test")
                .modelName("gpt-5.6")
                .promptCacheOptions(promptCacheOptions)
                .build();
        OpenAiOfficialResponsesStreamingChatModel streamingChatModel =
                OpenAiOfficialResponsesStreamingChatModel.builder()
                        .apiKey("test")
                        .modelName("gpt-5.6")
                        .promptCacheOptions(promptCacheOptions)
                        .build();

        assertThat(((OpenAiOfficialResponsesChatRequestParameters) chatModel.defaultRequestParameters())
                        .promptCacheOptions())
                .isEqualTo(promptCacheOptions);
        assertThat(((OpenAiOfficialResponsesChatRequestParameters) streamingChatModel.defaultRequestParameters())
                        .promptCacheOptions())
                .isEqualTo(promptCacheOptions);
    }

    // ----- Responses: prompt_cache_breakpoint -----

    private static List<ResponseInputItem> responsesInput(dev.langchain4j.data.message.ChatMessage... messages) {
        ResponseCreateParams params = OpenAiOfficialResponsesStreamingChatModel.buildRequestParams(
                ChatRequest.builder().messages(messages).build(), RESPONSES_PARAMETERS);
        return params.input().orElseThrow().asResponse();
    }

    @Test
    void should_send_breakpoint_on_responses_system_message() {
        List<ResponseInputItem> input = responsesInput(
                SystemMessage.builder()
                        .text("Shared instructions")
                        .attributes(breakpointAttributes())
                        .build(),
                UserMessage.from("Hi"));

        List<ResponseInputContent> contents =
                input.get(0).asEasyInputMessage().content().asResponseInputMessageContentList();
        assertThat(contents).hasSize(1);
        assertThat(contents.get(0).asInputText().text()).isEqualTo("Shared instructions");
        assertThat(contents.get(0).asInputText().promptCacheBreakpoint()).isPresent();
    }

    @Test
    void should_send_responses_system_message_as_plain_text_when_not_marked() {
        List<ResponseInputItem> input =
                responsesInput(SystemMessage.from("Shared instructions"), UserMessage.from("Hi"));

        EasyInputMessage systemMessage = input.get(0).asEasyInputMessage();
        assertThat(systemMessage.content().textInput()).contains("Shared instructions");
    }

    @Test
    void should_send_breakpoint_only_on_last_content_block_of_responses_user_message() {
        UserMessage userMessage = UserMessage.builder()
                .addContent(TextContent.from("Describe this"))
                .addContent(ImageContent.from("http://image.url"))
                .attributes(breakpointAttributes())
                .build();

        List<ResponseInputContent> contents = responsesInput(userMessage)
                .get(0)
                .asEasyInputMessage()
                .content()
                .asResponseInputMessageContentList();

        assertThat(contents).hasSize(2);
        assertThat(contents.get(0).asInputText().promptCacheBreakpoint()).isEmpty();
        assertThat(contents.get(1).asInputImage().promptCacheBreakpoint()).isPresent();
    }

    @Test
    void should_send_breakpoint_on_responses_tool_execution_result_message() {
        List<ResponseInputItem> input = responsesInput(
                UserMessage.from("What is the weather?"),
                AiMessage.from(ToolExecutionRequest.builder()
                        .id("call_123")
                        .name("getWeather")
                        .arguments("{}")
                        .build()),
                ToolExecutionResultMessage.builder()
                        .id("call_123")
                        .toolName("getWeather")
                        .text("Sunny")
                        .attributes(breakpointAttributes())
                        .build());

        var outputItems =
                input.get(input.size() - 1).asFunctionCallOutput().output().asResponseFunctionCallOutputItemList();
        assertThat(outputItems).hasSize(1);
        assertThat(outputItems.get(0).asInputText().promptCacheBreakpoint()).isPresent();
    }

    @Test
    void should_fail_when_breakpoint_is_set_on_responses_ai_message() {
        AiMessage aiMessage = AiMessage.builder()
                .text("Hello!")
                .attributes(breakpointAttributes())
                .build();

        assertThatThrownBy(() -> responsesInput(UserMessage.from("Hi"), aiMessage))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("AiMessage");
    }

    // ----- cache_write_tokens -----

    @Test
    void should_read_cache_write_tokens_from_chat_completions_usage() {
        CompletionUsage usage = CompletionUsage.builder()
                .promptTokens(2000L)
                .completionTokens(5L)
                .totalTokens(2005L)
                .promptTokensDetails(CompletionUsage.PromptTokensDetails.builder()
                        .cachedTokens(512L)
                        .cacheWriteTokens(1024L)
                        .build())
                .build();

        OpenAiOfficialTokenUsage tokenUsage = InternalOpenAiOfficialHelper.tokenUsageFrom(usage);

        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isEqualTo(512);
        assertThat(tokenUsage.inputTokensDetails().cacheWriteTokens()).isEqualTo(1024);
    }

    @Test
    void should_leave_cache_write_tokens_null_when_chat_completions_does_not_report_it() {
        CompletionUsage usage = CompletionUsage.builder()
                .promptTokens(2000L)
                .completionTokens(5L)
                .totalTokens(2005L)
                .promptTokensDetails(CompletionUsage.PromptTokensDetails.builder()
                        .cachedTokens(512L)
                        .build())
                .build();

        OpenAiOfficialTokenUsage tokenUsage = InternalOpenAiOfficialHelper.tokenUsageFrom(usage);

        assertThat(tokenUsage.inputTokensDetails().cachedTokens()).isEqualTo(512);
        assertThat(tokenUsage.inputTokensDetails().cacheWriteTokens()).isNull();
    }

    @Test
    void should_add_cache_write_tokens() {
        OpenAiOfficialTokenUsage first = OpenAiOfficialTokenUsage.builder()
                .inputTokensDetails(OpenAiOfficialTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .cacheWriteTokens(100)
                        .build())
                .build();
        OpenAiOfficialTokenUsage second = OpenAiOfficialTokenUsage.builder()
                .inputTokensDetails(OpenAiOfficialTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .cacheWriteTokens(200)
                        .build())
                .build();

        OpenAiOfficialTokenUsage result = first.add(second);

        assertThat(result.inputTokensDetails().cachedTokens()).isEqualTo(12);
        assertThat(result.inputTokensDetails().cacheWriteTokens()).isEqualTo(300);
    }

    @Test
    void should_keep_cache_write_tokens_null_when_neither_side_reports_it() {
        OpenAiOfficialTokenUsage first = OpenAiOfficialTokenUsage.builder()
                .inputTokensDetails(OpenAiOfficialTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(5)
                        .build())
                .build();
        OpenAiOfficialTokenUsage second = OpenAiOfficialTokenUsage.builder()
                .inputTokensDetails(OpenAiOfficialTokenUsage.InputTokensDetails.builder()
                        .cachedTokens(7)
                        .build())
                .build();

        assertThat(first.add(second).inputTokensDetails().cacheWriteTokens()).isNull();
    }
}
