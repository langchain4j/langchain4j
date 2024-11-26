package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.AssistantMessage;
import dev.ai4j.openai4j.chat.ChatCompletionChoice;
import dev.ai4j.openai4j.chat.ChatCompletionResponse;
import dev.ai4j.openai4j.chat.FunctionCall;
import dev.ai4j.openai4j.chat.ToolCall;
import dev.ai4j.openai4j.chat.ToolChoiceMode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.ToolChoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static dev.ai4j.openai4j.chat.ToolType.FUNCTION;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.aiMessageFrom;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.toOpenAiToolChoice;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class InternalOpenAiHelperTest {

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
        assertThat(aiMessage.toolExecutionRequests()).isNull();
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
        assertThat(aiMessage.toolExecutionRequests()).containsExactly(ToolExecutionRequest
                .builder()
                .name(functionName)
                .arguments(functionArguments)
                .build()
        );
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
        assertThat(aiMessage.toolExecutionRequests()).containsExactly(ToolExecutionRequest
                .builder()
                .name(functionName)
                .arguments(functionArguments)
                .build()
        );
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
        assertThat(aiMessage.toolExecutionRequests()).containsExactly(ToolExecutionRequest
                .builder()
                .name(functionName)
                .arguments(functionArguments)
                .build()
        );
    }

    @Test
    void should_map_tool_choice() {
        assertThat(toOpenAiToolChoice(ToolChoice.AUTO)).isEqualTo(ToolChoiceMode.AUTO);
        assertThat(toOpenAiToolChoice(ToolChoice.REQUIRED)).isEqualTo(ToolChoiceMode.REQUIRED);
    }

    @ParameterizedTest
    @EnumSource
    void should_map_all_tool_choices(ToolChoice toolChoice) {
        assertThat(toOpenAiToolChoice(toolChoice)).isNotNull();
    }
}
