package dev.langchain4j.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.exception.ToolErrorVisibleToLlm;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.tool.ToolExecutionErrorHandler;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class AiServicesWithToolErrorHandlerTest extends AbstractAiServicesWithToolErrorHandlerTest {

    @Override
    protected void configureGetWeatherThrowingExceptionTool(RuntimeException e, AiServices<?> aiServiceBuilder) {
        class Tools {
            @Tool
            String getWeatherThrowingException(String ignored) {
                throw e;
            }
        }
        aiServiceBuilder.tools(new Tools());
    }

    @Override
    protected void configureGetWeatherThrowingExceptionWithoutMessageTool(RuntimeException e, AiServices<?> aiServiceBuilder) {
        class Tools {
            @Tool
            String getWeatherThrowingExceptionWithoutMessage(String ignored) {
                throw e;
            }
        }
        aiServiceBuilder.tools(new Tools());
    }

    @Override
    protected void configureGetWeatherTool(AiServices<?> aiServiceBuilder) {
        class Tools {
            @Tool
            String getWeather(String ignored) {
                return "Sunny";
            }
        }
        aiServiceBuilder.tools(new Tools());
    }

    @Override
    protected void configureGetImageTool(AiServices<?> aiServiceBuilder) {
        class Tools {
            @Tool
            String getImage() {
                throw new RuntimeException("Unsupported content type: \"image\"");
            }
        }
        aiServiceBuilder.tools(new Tools());
    }

    // The tests below need a tool that throws an arbitrary exception, which an MCP server cannot do,
    // so they live here rather than in AbstractAiServicesWithToolErrorHandlerTest

    static class OrderNotFoundException extends RuntimeException implements ToolErrorVisibleToLlm {

        OrderNotFoundException() {
            super("SELECT * FROM orders WHERE id = 42 returned no rows");
        }

        @Override
        public String messageForLlm() {
            return "There is no order with ID 42";
        }
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void should_send_message_for_llm_when_tool_throws_error_visible_to_llm_by_default(
            boolean executeToolsConcurrently, InvocationMode invocationMode) {
        should_send_message_for_llm_when_tool_throws_error_visible_to_llm(
                executeToolsConcurrently, invocationMode, null);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void should_send_message_for_llm_when_tool_throws_error_visible_to_llm_with_fail_invocation_unless_visible_to_llm(
            boolean executeToolsConcurrently, InvocationMode invocationMode) {
        should_send_message_for_llm_when_tool_throws_error_visible_to_llm(
                executeToolsConcurrently,
                invocationMode,
                ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm());
    }

    private void should_send_message_for_llm_when_tool_throws_error_visible_to_llm(
            boolean executeToolsConcurrently,
            InvocationMode invocationMode,
            ToolExecutionErrorHandler toolExecutionErrorHandler) {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeatherThrowingException")
                .arguments("{\"arg0\":\"Munich\"}")
                .build();

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds(
                AiMessage.from(toolExecutionRequest), AiMessage.from("There is no such order")));

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class).chatModel(spyModel);
        if (toolExecutionErrorHandler != null) {
            assistantBuilder.toolExecutionErrorHandler(toolExecutionErrorHandler);
        }
        configureGetWeatherThrowingExceptionTool(new OrderNotFoundException(), assistantBuilder);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        chat(assistant, "What is the status of order 42?", invocationMode);

        // then
        verifyChatRequest(spyModel, invocationMode, chatRequest -> chatRequest.messages().size() == 1);
        verifyChatRequest(spyModel, invocationMode, chatRequest -> chatRequest.messages().size() == 3
                && chatRequest.messages().get(2) instanceof ToolExecutionResultMessage toolResult
                && toolResult.text().equals("There is no order with ID 42"));
        ignoreOtherInteractions(spyModel);
        verifyNoMoreInteractions(spyModel);
    }

    @ParameterizedTest
    @MethodSource("parameters")
    void should_fail_when_tool_throws_error_not_visible_to_llm_with_fail_invocation_unless_visible_to_llm(
            boolean executeToolsConcurrently, InvocationMode invocationMode) {

        // given
        ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                .name("getWeatherThrowingException")
                .arguments("{\"arg0\":\"Munich\"}")
                .build();

        ChatModel spyModel = spy(ChatModelMock.thatAlwaysResponds(AiMessage.from(toolExecutionRequest)));

        RuntimeException toolError = new RuntimeException("Connection refused: db.internal:5432");

        AiServices<Assistant> assistantBuilder = AiServices.builder(Assistant.class)
                .chatModel(spyModel)
                .toolExecutionErrorHandler(ToolExecutionErrorHandler.failInvocationUnlessVisibleToLlm());
        configureGetWeatherThrowingExceptionTool(toolError, assistantBuilder);
        if (executeToolsConcurrently) {
            assistantBuilder.executeToolsConcurrently();
        }
        Assistant assistant = assistantBuilder.build();

        // when
        assertThatThrownBy(() -> chat(assistant, "What is the weather in Munich?", invocationMode))
                .isSameAs(toolError);

        // then
        verifyAnyChatRequest(spyModel, invocationMode);
        ignoreOtherInteractions(spyModel);
        verifyNoMoreInteractions(spyModel);
    }
}
