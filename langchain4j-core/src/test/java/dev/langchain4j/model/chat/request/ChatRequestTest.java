package dev.langchain4j.model.chat.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

class ChatRequestTest {

    @Test
    void should_keep_backward_compatibility() {

        // given
        UserMessage userMessage = UserMessage.from("hi");
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("tool").build();
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
        ToolSpecification toolSpecification =
                ToolSpecification.builder().name("tool").build();
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
                        .toolSpecifications(
                                ToolSpecification.builder().name("tool").build())
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'parameters' and 'toolSpecifications' on ChatRequest");
    }

    @Test
    void should_create_chat_request_with_multiple_messages() {
        // given
        UserMessage firstMessage = UserMessage.from("Hello");
        UserMessage secondMessage = UserMessage.from("How are you?");

        // when
        ChatRequest chatRequest =
                ChatRequest.builder().messages(firstMessage, secondMessage).build();

        // then
        assertThat(chatRequest.messages()).containsExactly(firstMessage, secondMessage);
    }

    @Test
    void should_create_chat_request_with_multiple_tool_specifications() {
        // given
        ToolSpecification tool1 = ToolSpecification.builder().name("tool1").build();
        ToolSpecification tool2 = ToolSpecification.builder().name("tool2").build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .toolSpecifications(tool1, tool2)
                .build();

        // then
        assertThat(chatRequest.toolSpecifications()).containsExactly(tool1, tool2);
    }

    @Test
    void should_create_chat_request_with_only_parameters() {
        // given
        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .responseFormat(ResponseFormat.JSON)
                .build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .parameters(parameters)
                .build();

        // then
        assertThat(chatRequest.parameters()).isEqualTo(parameters);
        assertThat(chatRequest.responseFormat()).isEqualTo(ResponseFormat.JSON);
    }

    @Test
    void should_handle_null_response_format() {
        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .responseFormat(null)
                .build();

        // then
        assertThat(chatRequest.responseFormat()).isNull();
    }

    @Test
    void should_fail_when_messages_is_null() {
        assertThatThrownBy(() ->
                        ChatRequest.builder().messages((UserMessage[]) null).build())
                .isExactlyInstanceOf(NullPointerException.class);
    }

    @Test
    void should_preserve_order_of_messages() {
        // given
        UserMessage first = UserMessage.from("first");
        UserMessage second = UserMessage.from("second");
        UserMessage third = UserMessage.from("third");

        // when
        ChatRequest chatRequest =
                ChatRequest.builder().messages(first, second, third).build();

        // then
        assertThat(chatRequest.messages()).containsExactly(first, second, third);
    }

    @Test
    void should_preserve_order_of_tool_specifications() {
        // given
        ToolSpecification toolA = ToolSpecification.builder().name("toolA").build();
        ToolSpecification toolB = ToolSpecification.builder().name("toolB").build();
        ToolSpecification toolC = ToolSpecification.builder().name("toolC").build();

        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .toolSpecifications(toolA, toolB, toolC)
                .build();

        // then
        assertThat(chatRequest.toolSpecifications()).containsExactly(toolA, toolB, toolC);
    }

    @Test
    void should_handle_empty_tool_specifications_array() {
        // when
        ChatRequest chatRequest = ChatRequest.builder()
                .messages(UserMessage.from("hi"))
                .toolSpecifications()
                .build();

        // then
        assertThat(chatRequest.toolSpecifications()).isEmpty();
    }

    @Test
    void should_fail_when_both_parameters_and_response_format_are_non_null() {
        // given
        ChatRequestParameters parameters = ChatRequestParameters.builder().build();

        assertThatThrownBy(() -> ChatRequest.builder()
                        .messages(UserMessage.from("hi"))
                        .parameters(parameters)
                        .responseFormat(ResponseFormat.JSON)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'parameters' and 'responseFormat' on ChatRequest");
    }

    @Test
    void should_fail_when_both_parameters_and_tool_specifications_are_non_null() {
        // given
        ChatRequestParameters parameters = ChatRequestParameters.builder().build();
        ToolSpecification tool = ToolSpecification.builder().name("tool").build();

        assertThatThrownBy(() -> ChatRequest.builder()
                        .messages(UserMessage.from("hi"))
                        .parameters(parameters)
                        .toolSpecifications(tool)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'parameters' and 'toolSpecifications' on ChatRequest");
    }
}
