package dev.langchain4j.internal;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.HttpParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatRequestValidationUtilsTest {

    @Test
    void validateMessages_shouldAllowTextContent() {
        UserMessage message = new UserMessage(List.of(new TextContent("hello")));
        assertThatCode(() -> ChatRequestValidationUtils.validateMessages(List.of(message)))
                .doesNotThrowAnyException();
    }

    @Test
    void validateMessages_shouldRejectNonTextContent() {
        UserMessage message = new UserMessage(List.of(new ImageContent("http://image.com")));
        assertThatThrownBy(() -> ChatRequestValidationUtils.validateMessages(List.of(message)))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("Content of type image is not supported");
    }

    @Test
    void validateTools_shouldRejectNonEmptyList() {
        ToolSpecification tool = ToolSpecification.builder().name("myTool").build();
        assertThatThrownBy(() -> ChatRequestValidationUtils.validate(List.of(tool)))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("tools are not supported yet");
    }

    @Test
    void validateTools_shouldAllowEmptyList() {
        assertThatCode(() -> ChatRequestValidationUtils.validate(List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateToolChoice_shouldRejectNonAuto() {
        assertThatThrownBy(() -> ChatRequestValidationUtils.validate(ToolChoice.REQUIRED))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("ToolChoice.REQUIRED is not supported");
    }

    @Test
    void validateToolChoice_shouldAllowAuto() {
        assertThatCode(() -> ChatRequestValidationUtils.validate(ToolChoice.AUTO))
                .doesNotThrowAnyException();
    }

    @Test
    void validateResponseFormat_shouldRejectJson() {
        assertThatThrownBy(() -> ChatRequestValidationUtils.validate(ResponseFormat.JSON))
                .isInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("JSON response format");
    }

    @Test
    void validateResponseFormat_shouldAllowOtherFormats() {
        assertThatCode(() -> ChatRequestValidationUtils.validate(ResponseFormat.TEXT))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_no_http_parameters_should_fail_when_http_parameters_are_set() {

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("X-Correlation-Id", "123"))
                        .build())
                .build();

        assertThatThrownBy(() -> ChatRequestValidationUtils.validateNoHttpParameters(parameters))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters");
        assertThatThrownBy(() -> ChatRequestValidationUtils.validateParameters(parameters))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters");
    }

    @Test
    void validate_no_http_parameters_should_pass_when_http_parameters_are_not_set() {

        ChatRequestParameters parameters = ChatRequestParameters.builder().build();

        assertThatCode(() -> ChatRequestValidationUtils.validateNoHttpParameters(parameters))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_no_http_parameters_should_pass_when_http_parameters_have_nothing_to_send() {

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder().customHeaders(Map.of()).build())
                .build();

        assertThatCode(() -> ChatRequestValidationUtils.validateNoHttpParameters(parameters))
                .doesNotThrowAnyException();
        assertThatCode(() -> ChatRequestValidationUtils.validateNoHttpParametersInBatch(parameters))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_no_http_parameters_in_batch_should_fail_with_batch_specific_message() {

        ChatRequestParameters parameters = ChatRequestParameters.builder()
                .httpParameters(HttpParameters.builder()
                        .customHeaders(Map.of("X-Correlation-Id", "123"))
                        .build())
                .build();

        assertThatThrownBy(() -> ChatRequestValidationUtils.validateNoHttpParametersInBatch(parameters))
                .isExactlyInstanceOf(UnsupportedFeatureException.class)
                .hasMessageContaining("httpParameters")
                .hasMessageContaining("batch");
    }
}

