package dev.langchain4j.model.openai;

import dev.ai4j.openai4j.chat.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.ai4j.openai4j.chat.ToolType.FUNCTION;
import static dev.langchain4j.model.openai.InternalOpenAiHelper.*;
import static dev.langchain4j.model.output.FinishReason.STOP;
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
    void test_isOpenAiModel() {

        assertThat(isOpenAiModel(null)).isFalse();
        assertThat(isOpenAiModel("")).isFalse();
        assertThat(isOpenAiModel(" ")).isFalse();
        assertThat(isOpenAiModel("llama2")).isFalse();

        assertThat(isOpenAiModel("gpt-3.5-turbo")).isTrue();
        assertThat(isOpenAiModel("ft:gpt-3.5-turbo:my-org:custom_suffix:id")).isTrue();
    }

    @Test
    void test_removeTokenUsage() {

        assertThat(removeTokenUsage(Response.from(AiMessage.from("Hello"))))
                .isEqualTo(Response.from(AiMessage.from("Hello")));
        assertThat(removeTokenUsage(Response.from(AiMessage.from("Hello"), new TokenUsage(42))))
                .isEqualTo(Response.from(AiMessage.from("Hello")));
        assertThat(removeTokenUsage(Response.from(AiMessage.from("Hello"), new TokenUsage(42), STOP)))
                .isEqualTo(Response.from(AiMessage.from("Hello"), null, STOP));
    }
}