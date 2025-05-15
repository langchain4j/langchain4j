package dev.langchain4j.service;

import static dev.langchain4j.service.AiServicesIT.chatRequest;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.service.tool.HallucinatedToolNameStrategy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServicesUserMessageConfigTest {

    @Spy
    ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Berlin");

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(chatModel);
    }

    interface AiService {

        String chat1(String userMessage);

        String chat2(@UserMessage String userMessage);

        String chat3(@UserMessage String userMessage, @V("country") String country);

        @UserMessage("What is the capital of Germany?")
        String chat4();

        @UserMessage("What is the capital of {{it}}?")
        String chat5(String country);

        @UserMessage("What is the capital of {{country}}?")
        String chat6(@V("country") String country);

        @UserMessage("What is the {{it}} of {{country}}?")
        String chat7(@V("it") String it, @V("country") String country);

        @UserMessage("What is the capital of {{arg0}}?")
        String chat8(String country);

        // illegal configuration

        String illegalChat1();

        String illegalChat2(@V("country") String country);

        String illegalChat3(String userMessage, String country);

        String illegalChat4(@UserMessage String userMessage, String country);

        @UserMessage
        String illegalChat5();

        @UserMessage("Hello")
        String illegalChat6(@UserMessage String userMessage);

        // TODO more tests with @UserName, @V, @MemoryId
    }

    @Test
    void user_message_configuration_1() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat1("What is the capital of Germany?")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_2() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat2("What is the capital of Germany?")).containsIgnoringCase("Berlin");

        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_3() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat3("What is the capital of {{country}}?", "Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_4() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat4()).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_5() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat5("Germany")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_6() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat6("Germany")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_7() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat7("capital", "Germany")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_8() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat8("Germany")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void illegal_user_message_configuration_1() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThatThrownBy(aiService::illegalChat1)
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Error: The method 'illegalChat1' does not have a user message defined.");
    }

    @Test
    void illegal_user_message_configuration_2() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat2("Germany"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Error: The method 'illegalChat2' does not have a user message defined.");
    }

    @Test
    void illegal_user_message_configuration_3() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat3("What is the capital of {{it}}?", "Germany"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Parameter 'arg0' of method 'illegalChat3' should be annotated "
                        + "with @V or @UserMessage or @UserName or @MemoryId");
    }

    @Test
    void illegal_user_message_configuration_4() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat4("What is the capital of {{it}}?", "Germany"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Parameter 'arg1' of method 'illegalChat4' should be annotated "
                        + "with @V or @UserMessage or @UserName or @MemoryId");
    }

    @Test
    void illegal_user_message_configuration_5() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThatThrownBy(aiService::illegalChat5)
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("@UserMessage's template cannot be empty");
    }

    @Test
    void illegal_user_message_configuration_6() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat6("Hello"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage(
                        "Error: The method 'illegalChat6' has multiple @UserMessage annotations. Please use only one.");
    }

    interface AssistantHallucinatedTool {
        Result<AiMessage> chat(String userMessage);
    }

    static class HelloWorld {

        @Tool("Say hello")
        String hello(String name) {
            return "Hello " + name + "!";
        }
    }

    @Test
    void should_fail_on_hallucinated_tool_execution() {

        ChatModel chatModel = new ChatModelMock(ignore -> AiMessage.from(
                ToolExecutionRequest.builder().id("id").name("unknown").build()));

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        AssistantHallucinatedTool assistant = AiServices.builder(AssistantHallucinatedTool.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .tools(new HelloWorld())
                .hallucinatedToolNameStrategy(HallucinatedToolNameStrategy.THROW_EXCEPTION)
                .build();

        assertThatThrownBy(() -> assistant.chat("hi"))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasMessageContaining("unknown");

        validateChatMemory(chatMemory);
    }

    @Test
    void should_retry_on_hallucinated_tool_execution() {

        ChatModel chatModel = new ChatModelMock(chatRequest -> {
            List<ToolExecutionResultMessage> toolResults = chatRequest.messages().stream()
                    .filter(ToolExecutionResultMessage.class::isInstance)
                    .map(ToolExecutionResultMessage.class::cast)
                    .toList();
            if (toolResults.isEmpty()) {
                return AiMessage.from(
                        ToolExecutionRequest.builder().id("id").name("unknown").build());
            }
            ToolExecutionResultMessage lastToolResult = toolResults.get(toolResults.size() - 1);
            String text = lastToolResult.text();
            if (text.contains("Error")) {
                // The LLM is supposed to understand the error and retry with the correct tool name
                return AiMessage.from(ToolExecutionRequest.builder()
                        .id("id")
                        .name("hello")
                        .arguments("{\"arg0\": \"Mario\"}")
                        .build());
            }
            return AiMessage.from(text);
        });

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        AssistantHallucinatedTool assistant = AiServices.builder(AssistantHallucinatedTool.class)
                .chatModel(chatModel)
                .chatMemory(chatMemory)
                .tools(new HelloWorld())
                .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(
                        toolExecutionRequest, "Error: there is no tool called " + toolExecutionRequest.name()))
                .build();

        Result<AiMessage> result = assistant.chat("hi");
        assertThat(result.content().text()).isEqualTo("Hello Mario!");

        validateChatMemory(chatMemory);
    }

    private static void validateChatMemory(ChatMemory chatMemory) {
        List<ChatMessage> messages = chatMemory.messages();
        Class<?> expectedMessageType = dev.langchain4j.data.message.UserMessage.class;
        for (ChatMessage message : messages) {
            assertThat(message).isInstanceOf(expectedMessageType);
            expectedMessageType = nextExpectedMessageType(message);
        }
    }

    private static Class<?> nextExpectedMessageType(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage) {
            return AiMessage.class;
        } else if (message instanceof AiMessage aiMessage) {
            if (aiMessage.hasToolExecutionRequests()) {
                return ToolExecutionResultMessage.class;
            } else {
                return dev.langchain4j.data.message.UserMessage.class;
            }
        } else if (message instanceof ToolExecutionResultMessage) {
            return AiMessage.class;
        }
        throw new UnsupportedOperationException(
                "Unsupported message type: " + message.getClass().getName());
    }
}
