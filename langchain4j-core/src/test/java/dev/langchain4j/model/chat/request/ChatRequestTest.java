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
                        .parameters(ChatRequestParameters.builder().build())
                        .responseFormat(ResponseFormat.JSON)
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'parameters' and 'responseFormat' on ChatRequest");
    }

    @Test
    void should_fail_when_both_request_parameters_and_toolSpecifications_are_set() {

        assertThatThrownBy(() -> ChatRequest.builder()
                        .messages(UserMessage.from("hi"))
                        .parameters(ChatRequestParameters.builder().build())
                        .toolSpecifications(
                                ToolSpecification.builder().name("tool").build())
                        .build())
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cannot set both 'parameters' and 'toolSpecifications' on ChatRequest");
    }

    @Test
    void should_copy_parameters_from_existing_instance_and_allow_overrides() {
        // given
        ChatRequestParameters original = ChatRequestParameters.builder()
                .modelName("original-model")
                .temperature(0.5)
                .stopSequences("STOP_ONE")
                .build();

        // when
        // copy from the original, but override some fields
        ChatRequestParameters copy = ChatRequestParameters.builder(original)
                .temperature(0.9) // override
                .stopSequences("STOP_TWO") // override
                .build();

        // then
        // fields not overridden remain from 'original'
        assertThat(copy.modelName()).isEqualTo("original-model"); // same as original
        assertThat(copy.temperature()).isEqualTo(0.9); // overridden
        assertThat(copy.stopSequences()).containsExactly("STOP_TWO"); // overridden
    }
}
