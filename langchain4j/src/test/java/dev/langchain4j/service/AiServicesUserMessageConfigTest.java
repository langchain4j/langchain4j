package dev.langchain4j.service;

import static dev.langchain4j.data.message.UserMessage.*;
import static dev.langchain4j.service.AiServicesIT.chatRequest;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.tool.HallucinatedToolNameStrategy;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServicesUserMessageConfigTest {

    private static final Image image = Image.builder()
            .url("https://en.wikipedia.org/wiki/Llama#/media/File:Llamas,_Vernagt-Stausee,_Italy.jpg")
            .build();
    private static final ImageContent imageContent = ImageContent.from(image);

    @Spy
    ChatModel chatModel = ChatModelMock.thatAlwaysResponds("Berlin");

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(chatModel);
    }

    static class MyInvocationParameters extends InvocationParameters {}

    interface AiService {

        String chat1(String userMessage);

        String chat2(@UserMessage String userMessage);

        String chat2_1(@UserMessage String userMessage, InvocationParameters invocationParameters);

        String chat2_2(@UserMessage String userMessage, MyInvocationParameters invocationParameters);

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

        String chat9(@UserMessage String userMessage, @UserMessage ImageContent image);

        @UserMessage("How many lamas are there in this image?")
        String chat10(@UserMessage List<ImageContent> images);

        String chat11(@UserMessage ImageContent image1, @UserMessage String text, @UserMessage ImageContent image2);

        String chat12(MyObject myObject);

        String chat13(@UserMessage MyObject myObject);

        String chat14(@UserMessage MyObject myObject, @UserMessage ImageContent image);

        String chat15(@UserMessage String userMessage, @UserMessage Content content);

        // illegal configuration

        String illegalChat1();

        String illegalChat2(@V("country") String country);

        String illegalChat3(String userMessage, String country);

        String illegalChat4(@UserMessage String userMessage, String country);

        @UserMessage
        String illegalChat5();

        @UserMessage("Hello")
        String illegalChat6(@UserMessage String userMessage);

        String illegalChat7(String userMessage, InvocationParameters invocationParameters);

        String illegalChat8(@UserMessage String userMessage, InvocationParameters ip1, InvocationParameters ip2);

        // TODO more tests with @UserName, @V, @MemoryId
    }

    class MyObject {

        private final String value;

        MyObject(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }
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
    void user_message_configuration_2_1() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat2_1("What is the capital of Germany?", new InvocationParameters()))
                .containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_2_1_when_invocation_parameters_are_null() {

        // given
        InvocationParameters invocationParameters = null;

        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.chat2_1("does not matter", invocationParameters))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("InvocationParameters cannot be null");
    }

    @Test
    void user_message_configuration_2_2() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThat(aiService.chat2_2("What is the capital of Germany?", new MyInvocationParameters()))
                .containsIgnoringCase("Berlin");
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
    void user_message_configuration_9() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when
        aiService.chat9("Count the number of lamas in this image", imageContent);

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(userMessage(TextContent.from("Count the number of lamas in this image"), imageContent))
                .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_10() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when
        aiService.chat10(List.of(imageContent));

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(userMessage(TextContent.from("How many lamas are there in this image?"), imageContent))
                .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_11() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when
        aiService.chat11(imageContent, "Count the number of lamas in this image", imageContent);

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(userMessage(imageContent, TextContent.from("Count the number of lamas in this image"), imageContent))
                .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_12() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when
        aiService.chat12(new MyObject("test123"));

        // then
        verify(chatModel).chat(chatRequest("test123"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_13() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when
        aiService.chat13(new MyObject("test123"));

        // then
        verify(chatModel).chat(chatRequest("test123"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_14() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when
        aiService.chat14(new MyObject("Count the number of lamas in this image"), imageContent);

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(userMessage(TextContent.from("Count the number of lamas in this image"), imageContent))
                .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_15() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when
        aiService.chat15("Hello!", TextContent.from("How are you?"));

        // then
        verify(chatModel).chat(ChatRequest.builder()
                .messages(userMessage(TextContent.from("Hello!"), TextContent.from("How are you?")))
                .build());
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
                .hasMessage("The parameter 'arg0' in the method 'illegalChat3' of the class dev.langchain4j.service.AiServicesUserMessageConfigTest$AiService must be annotated with either dev.langchain4j.service.UserMessage, dev.langchain4j.service.V, dev.langchain4j.service.MemoryId, or dev.langchain4j.service.UserName, or it should be of type dev.langchain4j.invocation.InvocationParameters");
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
                .hasMessage("The parameter 'arg1' in the method 'illegalChat4' of the class dev.langchain4j.service.AiServicesUserMessageConfigTest$AiService must be annotated with either dev.langchain4j.service.UserMessage, dev.langchain4j.service.V, dev.langchain4j.service.MemoryId, or dev.langchain4j.service.UserName, or it should be of type dev.langchain4j.invocation.InvocationParameters");
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

    @Test
    void illegal_user_message_configuration_7() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat7("Hello", new InvocationParameters()))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("The parameter 'arg0' in the method 'illegalChat7' of the class dev.langchain4j.service.AiServicesUserMessageConfigTest$AiService must be annotated with either dev.langchain4j.service.UserMessage, dev.langchain4j.service.V, dev.langchain4j.service.MemoryId, or dev.langchain4j.service.UserName, or it should be of type dev.langchain4j.invocation.InvocationParameters");
    }

    @Test
    void illegal_user_message_configuration_8() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatModel(chatModel)
                .build();

        InvocationParameters invocationParameters = new InvocationParameters();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat8("Hello", invocationParameters, invocationParameters))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("There can be at most one parameter of type dev.langchain4j.invocation.InvocationParameters");
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
