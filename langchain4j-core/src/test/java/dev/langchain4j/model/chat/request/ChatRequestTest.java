package dev.langchain4j.model.chat.request;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRequestTest {

    @Test
    void should_keep_backward_compatibility() {

        // given
        UserMessage userMessage = UserMessage.from("hi");
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("tool")
                .build();
        ResponseFormat responseFormat = ResponseFormat.JSON;

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .toolSpecifications(toolSpecification)
                .responseFormat(responseFormat)
                .build();

        // then
        assertThat(chatRequest.messages()).containsExactly(userMessage);
        assertThat(chatRequest.toolSpecifications()).containsExactly(toolSpecification);
        assertThat(chatRequest.responseFormat()).isEqualTo(responseFormat);
    }

    @Test
    void should_set_messages_and_request_parameters() {

        // given
        UserMessage userMessage = UserMessage.from("hi");
        ToolSpecification toolSpecification = ToolSpecification.builder()
                .name("tool")
                .build();
        ResponseFormat responseFormat = ResponseFormat.JSON;
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .toolSpecifications(toolSpecification)
                .responseFormat(responseFormat)
                .build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(userMessage)
                .parameters(parameters)
                .build();

        // then
        assertThat(chatRequest.messages()).containsExactly(userMessage);
        assertThat(chatRequest.parameters()).isEqualTo(parameters);

        assertThat(chatRequest.toolSpecifications()).containsExactly(toolSpecification);
        assertThat(chatRequest.responseFormat()).isEqualTo(responseFormat);
    }

    @Test
    void should_fail_when_both_request_parameters_and_response_format_are_set() {

        assertThatThrownBy(() -> ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(DefaultChatRequestParameters.EMPTY)
                .responseFormat(ResponseFormat.JSON)
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'parameters' and 'responseFormat' on ChatRequest");
    }

    @Test
    void should_fail_when_both_request_parameters_and_toolSpecifications_are_set() {

        assertThatThrownBy(() -> ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(DefaultChatRequestParameters.EMPTY)
                .toolSpecifications(ToolSpecification.builder().name("tool").build())
                .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'parameters' and 'toolSpecifications' on ChatRequest");
    }
}
