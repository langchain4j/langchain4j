package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fasterxml.jackson.core.JsonParseException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.tool.ToolArgumentsErrorHandler;
import dev.langchain4j.service.tool.ToolErrorHandlerResult;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class AbstractAiServicesWithToolErrorHandlerTest {

    protected abstract void configureGetWeatherThrowingExceptionTool(RuntimeException e, AiServices<?> aiServiceBuilder);

    protected abstract void configureGetWeatherTool(AiServices<?> aiServiceBuilder);

    interface Assistant {

        String chat(String userMessage);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_propagate_error_message_thrown_from_tool_to_LLM_by_default(boolean executeToolsConcurrently) {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeatherThrowingException")
                .arguments("{\"arg0\":\"Munich\"}")
                .build();

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest),
                AiMessage.from("I was not able to get the weather")
        ));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(spyModel);

        String toolErrorMessage = "Weather service is unavailable";
        configureGetWeatherThrowingExceptionTool(new RuntimeException(toolErrorMessage), assistantBuilder);

        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assistant.chat("What is the weather in Munich?");

        // then
        verify(spyModel).chat(argThat((ChatRequest chatRequest) -> chatRequest.messages().size() == 1));
        verify(spyModel).chat(argThat((ChatRequest chatRequest) -> chatRequest.messages().size() == 3
                && chatRequest.messages().get(2) instanceof ToolExecutionResultMessage toolResult
                && toolResult.text().equals(toolErrorMessage)));
        ignoreOtherInteractions(spyModel);
        verifyNoMoreInteractions(spyModel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_customize_error_returned_from_tool_before_sending_to_LLM(boolean executeToolsConcurrently) {

        // given
        String toolErrorMessage = "Weather service is unavailable";
        String customizedErrorMessage = "Can't get weather information";

        ToolExecutionErrorHandler toolExecutionErrorHandler = (error, context) -> {
            assertThat(error).hasMessage(toolErrorMessage);

            assertThat(context.toolExecutionRequest().name()).isEqualTo("getWeatherThrowingException");
            assertThat(context.toolExecutionRequest().arguments()).contains("Munich");
            assertThat(context.memoryId()).isEqualTo("default");

            return ToolErrorHandlerResult.text(customizedErrorMessage);
        };

        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeatherThrowingException")
                .arguments("{\"arg0\":\"Munich\"}")
                .build();

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest),
                AiMessage.from("I was not able to get the weather")
        ));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(spyModel)
                .toolExecutionErrorHandler(toolExecutionErrorHandler);

        configureGetWeatherThrowingExceptionTool(new CustomToolException(toolErrorMessage), assistantBuilder);

        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assistant.chat("What is the weather in Munich?");

        // then
        verify(spyModel).chat(argThat((ChatRequest chatRequest) -> chatRequest.messages().size() == 1));
        verify(spyModel).chat(argThat((ChatRequest chatRequest) -> chatRequest.messages().size() == 3
                && chatRequest.messages().get(2) instanceof ToolExecutionResultMessage toolResult
                && toolResult.text().equals(customizedErrorMessage)));
        ignoreOtherInteractions(spyModel);
        verifyNoMoreInteractions(spyModel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_fail_when_tool_throws_error(boolean executeToolsConcurrently) {

        // given
        String toolErrorMessage = "Weather service is unavailable";
        CustomToolException toolError = new CustomToolException(toolErrorMessage);

        ToolExecutionErrorHandler toolExecutionErrorHandler = (error, context) -> {
            assertThat(error).hasMessage(toolErrorMessage);

            assertThat(context.toolExecutionRequest().name()).isEqualTo("getWeatherThrowingException");
            assertThat(context.toolExecutionRequest().arguments()).contains("Munich");
            assertThat(context.memoryId()).isEqualTo("default");

            throw toolError;
        };

        ToolExecutionRequest toolRequest1 = ToolExecutionRequest.builder()
                .name("getWeatherThrowingException")
                .arguments("{\"arg0\":\"Munich\"}")
                .build();

        ToolExecutionRequest toolRequest2 = ToolExecutionRequest.builder()
                .name("getWeatherThrowingException")
                .arguments("{\"arg0\":\"Paris\"}")
                .build();

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds(AiMessage.from(toolRequest1, toolRequest2)));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(spyModel)
                .toolExecutionErrorHandler(toolExecutionErrorHandler);

        configureGetWeatherThrowingExceptionTool(toolError, assistantBuilder);

        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // asking for 2 tools to make sure tools will be executed concurrently using executor
        String userMessage = "What is the weather in Munich and Paris?";

        // when
        assertThatThrownBy(() -> assistant.chat(userMessage))
                .isSameAs(toolError);

        // then
        verify(spyModel).chat(any(ChatRequest.class));
        ignoreOtherInteractions(spyModel);
        verifyNoMoreInteractions(spyModel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_fail_when_cannot_parse_tool_arguments_by_default(boolean executeToolsConcurrently) {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{ invalid json }")
                .build();

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds(AiMessage.from(toolExecutionRequest)));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(spyModel);

        configureGetWeatherTool(assistantBuilder);

        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assertThatThrownBy(() -> assistant.chat("What is the weather in Munich?"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasCauseExactlyInstanceOf(JsonParseException.class)
                .hasMessageContaining("Unexpected character");

        // then
        verify(spyModel).chat(any(ChatRequest.class));
        ignoreOtherInteractions(spyModel);
        verifyNoMoreInteractions(spyModel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_customize_argument_parsing_error_before_sending_to_LLM(boolean executeToolsConcurrently) {

        // given
        ToolExecutionRequest toolExecutionRequest1 = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{ invalid json }")
                .build();

        ToolExecutionRequest toolExecutionRequest2 = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{\"arg0\":\"Munich\"}")
                .build();

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest1),
                AiMessage.from(toolExecutionRequest2),
                AiMessage.from("sunny")
        ));

        String customizedErrorMessage = "Invalid JSON, try again";

        ToolArgumentsErrorHandler toolArgumentsErrorHandler = (error, context) -> {
            assertThat(error)
                    .isExactlyInstanceOf(JsonParseException.class)
                    .hasMessageContaining("Unexpected character");

            assertThat(context.toolExecutionRequest()).isEqualTo(toolExecutionRequest1);
            assertThat(context.memoryId()).isEqualTo("default");

            return ToolErrorHandlerResult.text(customizedErrorMessage);
        };

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(spyModel)
                .toolArgumentsErrorHandler(toolArgumentsErrorHandler);

        configureGetWeatherTool(assistantBuilder);

        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assistant.chat("What is the weather in Munich?");

        // then
        verify(spyModel).chat(argThat((ChatRequest chatRequest) -> chatRequest.messages().size() == 1));
        verify(spyModel).chat(argThat((ChatRequest chatRequest) -> chatRequest.messages().size() == 3
                && chatRequest.messages().get(2) instanceof ToolExecutionResultMessage toolResult
                && toolResult.text().equals(customizedErrorMessage)));
        verify(spyModel).chat(argThat((ChatRequest chatRequest) -> chatRequest.messages().size() == 5));
        ignoreOtherInteractions(spyModel);
        verifyNoMoreInteractions(spyModel);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_fail_with_custom_exception_when_tool_arguments_cannot_be_parsed(boolean executeToolsConcurrently) {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeather")
                .arguments("{ invalid json }")
                .build();

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds(AiMessage.from(toolExecutionRequest)));

        CustomToolException customException = new CustomToolException("Can't parse JSON arguments");

        ToolArgumentsErrorHandler toolArgumentsErrorHandler = (error, context) -> {
            assertThat(error)
                    .isExactlyInstanceOf(JsonParseException.class)
                    .hasMessageContaining("Unexpected character");

            assertThat(context.toolExecutionRequest()).isEqualTo(toolExecutionRequest);
            assertThat(context.memoryId()).isEqualTo("default");

            throw customException;
        };

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(spyModel)
                .toolArgumentsErrorHandler(toolArgumentsErrorHandler);

        configureGetWeatherTool(assistantBuilder);

        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assertThatThrownBy(() -> assistant.chat("What is the weather in Munich?"))
                .isSameAs(customException);

        // then
        verify(spyModel).chat(any(ChatRequest.class));
        ignoreOtherInteractions(spyModel);
        verifyNoMoreInteractions(spyModel);
    }

    private static void ignoreOtherInteractions(ChatModel model) {
        verify(model, atLeast(0)).doChat(any());
        verify(model, atLeast(0)).defaultRequestParameters();
        verify(model, atLeast(0)).listeners();
        verify(model, atLeast(0)).provider();
        verify(model, atLeast(0)).supportedCapabilities();
    }

    static class CustomToolException extends RuntimeException {

        public CustomToolException(String message) {
            super(message);
        }
    }
}
