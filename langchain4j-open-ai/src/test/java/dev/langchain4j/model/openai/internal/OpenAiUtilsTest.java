package dev.langchain4j.model.openai.internal;

import static dev.langchain4j.model.openai.internal.OpenAiUtils.aiMessageFrom;
import static dev.langchain4j.model.openai.internal.OpenAiUtils.toOpenAiToolChoice;
import static dev.langchain4j.model.openai.internal.chat.ToolType.FUNCTION;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.openai.internal.chat.AssistantMessage;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionChoice;
import dev.langchain4j.model.openai.internal.chat.ChatCompletionResponse;
import dev.langchain4j.model.openai.internal.chat.FunctionCall;
import dev.langchain4j.model.openai.internal.chat.ToolCall;
import dev.langchain4j.model.openai.internal.chat.ToolChoiceMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OpenAiUtilsTest {

    @Test
    void should_return_ai_message_with_text_when_no_functions_and_tool_calls_are_present() {

        // given
        String messageContent = "hello";

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .content(messageContent)
                                .build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.text()).contains(messageContent);
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();
    }

    @Test
    void should_return_ai_message_with_toolExecutionRequests_when_function_is_present() {

        // given
        String functionName = "current_time";
        String functionArguments = "{}";

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .functionCall(FunctionCall.builder()
                                        .name(functionName)
                                        .arguments(functionArguments)
                                        .build())
                                .build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.toolExecutionRequests())
                .containsExactly(ToolExecutionRequest.builder()
                        .name(functionName)
                        .arguments(functionArguments)
                        .build());
    }

    @Test
    void should_return_ai_message_with_toolExecutionRequests_when_tool_calls_are_present() {

        // given
        String functionName = "current_time";
        String functionArguments = "{}";

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .toolCalls(ToolCall.builder()
                                        .type(FUNCTION)
                                        .function(FunctionCall.builder()
                                                .name(functionName)
                                                .arguments(functionArguments)
                                                .build())
                                        .build())
                                .build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.toolExecutionRequests())
                .containsExactly(ToolExecutionRequest.builder()
                        .name(functionName)
                        .arguments(functionArguments)
                        .build());
    }

    @Test
    void should_return_ai_message_with_toolExecutionRequests_and_text_when_tool_calls_and_content_are_both_present() {

        // given
        String functionName = "current_time";
        String functionArguments = "{}";

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .content("Hello")
                                .toolCalls(ToolCall.builder()
                                        .type(FUNCTION)
                                        .function(FunctionCall.builder()
                                                .name(functionName)
                                                .arguments(functionArguments)
                                                .build())
                                        .build())
                                .build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.text()).isEqualTo("Hello");
        assertThat(aiMessage.toolExecutionRequests())
                .containsExactly(ToolExecutionRequest.builder()
                        .name(functionName)
                        .arguments(functionArguments)
                        .build());
    }

    @Test
    void should_map_tool_choice() {
        assertThat(toOpenAiToolChoice(ToolChoice.AUTO)).isEqualTo(ToolChoiceMode.AUTO);
        assertThat(toOpenAiToolChoice(ToolChoice.REQUIRED)).isEqualTo(ToolChoiceMode.REQUIRED);
        assertThat(toOpenAiToolChoice(null)).isNull();
    }

    @ParameterizedTest
    @EnumSource
    void should_map_all_tool_choices(ToolChoice toolChoice) {
        assertThat(toOpenAiToolChoice(toolChoice)).isNotNull();
    }

    @Test
    void should_include_thinking_content_when_returnThinking_is_true() {
        // given
        String messageContent = "The answer is 1";
        String reasoningContent = "Let me think...";

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .content(messageContent)
                                .reasoningContent(reasoningContent)
                                .build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response, true);

        // then
        assertThat(aiMessage.text()).isEqualTo(messageContent);
        assertThat(aiMessage.thinking()).isEqualTo(reasoningContent);
    }

    @Test
    void should_exclude_thinking_content_when_returnThinking_is_false() {
        // given
        String messageContent = "The answer is 1";
        String reasoningContent = "Let me think...";

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .content(messageContent)
                                .reasoningContent(reasoningContent)
                                .build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response, false);

        // then
        assertThat(aiMessage.text()).isEqualTo(messageContent);
        assertThat(aiMessage.thinking()).isNull();
    }

    @Test
    void should_handle_tool_call_with_id() {
        // given
        String toolCallId = "id";
        String functionName = "check";
        String functionArguments = "{\"query\":\"OpenAI\"}";

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder()
                                .toolCalls(singletonList(ToolCall.builder()
                                        .id(toolCallId)
                                        .type(FUNCTION)
                                        .function(FunctionCall.builder()
                                                .name(functionName)
                                                .arguments(functionArguments)
                                                .build())
                                        .build()))
                                .build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest request = aiMessage.toolExecutionRequests().get(0);
        assertThat(request.id()).isEqualTo(toolCallId);
        assertThat(request.name()).isEqualTo(functionName);
        assertThat(request.arguments()).isEqualTo(functionArguments);
    }

    @Test
    void should_return_null_text_when_content_is_null() {
        // given
        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(singletonList(ChatCompletionChoice.builder()
                        .message(AssistantMessage.builder().content(null).build())
                        .build()))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).isEmpty();
    }

    /**
     * Regression test for <a href="https://github.com/langchain4j/langchain4j/issues/4931">Issue #4931</a>.
     * Some OpenAI-compatible providers split text and tool_calls across multiple choices.
     * For example, choices[0] has content and choices[1] has tool_calls.
     * All tool_calls must be extracted regardless of which choice they appear in.
     */
    @Test
    void should_extract_tool_calls_from_all_choices_when_text_and_tools_are_split_across_choices() {
        // given
        // choices[0] returns text content, choices[1] returns tool_calls
        String textContent = "Let me look that up for you.";
        String functionName = "current_time";
        String functionArguments = "{}";

        ChatCompletionChoice textChoice = ChatCompletionChoice.builder()
                .message(AssistantMessage.builder()
                        .content(textContent)
                        .build())
                .build();

        ChatCompletionChoice toolCallChoice = ChatCompletionChoice.builder()
                .message(AssistantMessage.builder()
                        .toolCalls(ToolCall.builder()
                                .type(FUNCTION)
                                .function(FunctionCall.builder()
                                        .name(functionName)
                                        .arguments(functionArguments)
                                        .build())
                                .build())
                        .build())
                .build();

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(java.util.List.of(textChoice, toolCallChoice))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);


        // then
        assertThat(aiMessage.text()).isEqualTo(textContent);
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        assertThat(aiMessage.toolExecutionRequests().get(0).name()).isEqualTo(functionName);
        assertThat(aiMessage.toolExecutionRequests().get(0).arguments()).isEqualTo(functionArguments);
    }

    @Test
    void should_merge_tool_calls_when_multiple_choices_have_tool_calls() {
        // given
        String functionName1 = "get_weather";
        String functionArguments1 = "{\"city\":\"Toronto\"}";
        String functionName2 = "get_time";
        String functionArguments2 = "{}";

        ChatCompletionChoice choice1 = ChatCompletionChoice.builder()
                .message(AssistantMessage.builder()
                        .toolCalls(ToolCall.builder()
                                .id("call_1")
                                .type(FUNCTION)
                                .function(FunctionCall.builder()
                                        .name(functionName1)
                                        .arguments(functionArguments1)
                                        .build())
                                .build())
                        .build())
                .build();

        ChatCompletionChoice choice2 = ChatCompletionChoice.builder()
                .message(AssistantMessage.builder()
                        .toolCalls(ToolCall.builder()
                                .id("call_2")
                                .type(FUNCTION)
                                .function(FunctionCall.builder()
                                        .name(functionName2)
                                        .arguments(functionArguments2)
                                        .build())
                                .build())
                        .build())
                .build();

        ChatCompletionResponse response = ChatCompletionResponse.builder()
                .choices(java.util.List.of(choice1, choice2))
                .build();

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);
        assertThat(aiMessage.toolExecutionRequests().get(0).id()).isEqualTo("call_1");
        assertThat(aiMessage.toolExecutionRequests().get(0).name()).isEqualTo(functionName1);
        assertThat(aiMessage.toolExecutionRequests().get(1).id()).isEqualTo("call_2");
        assertThat(aiMessage.toolExecutionRequests().get(1).name()).isEqualTo(functionName2);
    }
}
