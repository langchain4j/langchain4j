package dev.langchain4j.model.ark;

import com.volcengine.ark.runtime.model.completion.chat.*;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.model.ark.ArkHelper.*;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class ArkHelperTest {

    @Test
    void should_return_ai_message_with_text_when_no_functions_and_tool_calls_are_present() {

        // given
        String messageContent = "hello";

        ChatCompletionResult response = new ChatCompletionResult();
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content(messageContent).build());
        response.setChoices(singletonList(choice));

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

        ChatCompletionResult response = new ChatCompletionResult();
        ChatCompletionChoice choice = new ChatCompletionChoice();
        choice.setMessage(ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content("unexpected text").functionCall(new ChatFunctionCall(functionName, functionArguments)).build());
        response.setChoices(singletonList(choice));

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.text()).isNull();
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
        String callId = "call4u78";
        String functionName = "current_time";
        String functionArguments = "{}";

        ChatCompletionResult response = new ChatCompletionResult();
        ChatCompletionChoice choice = new ChatCompletionChoice();
        ChatToolCall toolCall = new ChatToolCall(callId, "function", new ChatFunctionCall(functionName, functionArguments));
        choice.setMessage(ChatMessage.builder().role(ChatMessageRole.ASSISTANT).content("unexpected text").toolCalls(singletonList(toolCall)).build());
        response.setChoices(singletonList(choice));

        // when
        AiMessage aiMessage = aiMessageFrom(response);

        // then
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).containsExactly(ToolExecutionRequest
                .builder()
                .id(callId)
                .name(functionName)
                .arguments(functionArguments)
                .build()
        );
    }
}