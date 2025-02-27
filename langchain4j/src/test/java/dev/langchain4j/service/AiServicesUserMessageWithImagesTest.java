package dev.langchain4j.service;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;

import static dev.langchain4j.service.AiServicesIT.chatRequest;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AiServicesUserMessageWithImagesTest {

    @Spy
    ChatLanguageModel chatLanguageModel = ChatModelMock.thatAlwaysResponds("Berlin");

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
            if (aiMessage.toolExecutionRequests() == null
                    || aiMessage.toolExecutionRequests().isEmpty()) {
                return dev.langchain4j.data.message.UserMessage.class;
            } else {
                return ToolExecutionResultMessage.class;
            }
        } else if (message instanceof ToolExecutionResultMessage) {
            return AiMessage.class;
        }
        throw new UnsupportedOperationException(
                "Unsupported message type: " + message.getClass().getName());
    }

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(chatLanguageModel);
    }

    @Test
    void user_message_configuration_1() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        URI imageUri = URI.create("https://www.google.com/images/branding/googlelogo/2x/googlelogo_light_color_272x92dp.png");
        dev.langchain4j.data.message.ImageContent content = dev.langchain4j.data.message.ImageContent.from(imageUri);
        assertThat(aiService.chatOneImage("What is the capital of Germany?", imageUri))
                .isEqualTo("Berlin");
        verify(chatLanguageModel).chat(chatRequest("What is the capital of Germany?", List.of(content)));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_2() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        URI imageUri = URI.create("https://www.google.com/images/branding/googlelogo/2x/googlelogo_light_color_272x92dp.png");
        dev.langchain4j.data.message.ImageContent content = dev.langchain4j.data.message.ImageContent.from(imageUri);
        assertThat(aiService.chatTwoImage("What is the capital of Germany?", "Germany", imageUri))
                .containsIgnoringCase("Berlin");

        verify(chatLanguageModel).chat(chatRequest("What is the capital of Germany?", List.of(content)));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_3() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();
        dev.langchain4j.data.message.ImageContent content =
                dev.langchain4j.data.message.ImageContent.from("base64Data", "image/jpeg");

        // when-then
        assertThat(aiService.chatThreeImage("What is the capital of {{country}}?", "Germany", "base64Data"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).chat(chatRequest("What is the capital of Germany?", List.of(content)));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_4() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        URI imageUrl = URI.create("http://germany.de");
        dev.langchain4j.data.message.ImageContent imageContentOne = dev.langchain4j.data.message.ImageContent
                .from(imageUrl);
        String base64 = "base64";
        dev.langchain4j.data.message.ImageContent imageContentTwo = dev.langchain4j.data.message.ImageContent
                .from(base64, "image/png");

        assertThat(aiService.chatFourImage("Come on!", "Germany", imageUrl, base64))
                .containsIgnoringCase("Berlin");

        verify(chatLanguageModel).chat(chatRequest("Come on!", List.of(imageContentOne, imageContentTwo)));
        verify(chatLanguageModel).supportedCapabilities();
    }

    interface AiService {

        String chatOneImage(@UserMessage String userMessage, @ImageContent URI image);

        String chatTwoImage(@UserMessage String userMessage, @V("country") String country, @ImageContent URI image);

        String chatThreeImage(@UserMessage String userMessage, @V("country") String country,
                              @ImageContent(mimeType = "image/jpeg") String base64Data);

        String chatFourImage(@UserMessage String userMessage, @V("country") String country,
                             @ImageContent URI imageUrl,
                             @ImageContent(mimeType = "image/png") String base64Data);
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
}
