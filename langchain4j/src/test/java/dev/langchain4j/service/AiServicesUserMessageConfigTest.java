package dev.langchain4j.service;

import static dev.langchain4j.data.message.UserMessage.*;
import static dev.langchain4j.service.AiServicesIT.chatRequest;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.NotExtensible;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServicesUserMessageConfigTest {

    static final String VALIDATION_ERROR_MESSAGE_SUFFIX = " must be annotated with either "
            + UserMessage.class.getName() + ", " + V.class.getName() + ", " + MemoryId.class.getName()
            + ", or " + UserName.class.getName() + ", or it should be of type " + InvocationParameters.class.getName()
            + " or " + ChatRequestParameters.class.getName();

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

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface ExternalAnnotation1 {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    private @interface ExternalAnnotation2 {}

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

        String chat16(@NotExtensible String msg);

        String chat17(@ExternalAnnotation1 @ExternalAnnotation2 String msg);

        String chat18_1(Content content);

        String chat18_2(AudioContent audioContent);

        String chat19_1(@UserMessage Content content);

        String chat19_2(@UserMessage AudioContent audioContent);

        String chat20_1(List<Content> contents);

        String chat20_2(List<AudioContent> audioContents);

        String chat21_1(@UserMessage List<Content> contents);

        String chat21_2(@UserMessage List<AudioContent> audioContents);

        String chat22_1(@UserMessage Content content1, @UserMessage Content content2);

        String chat22_2(@UserMessage AudioContent audio, @UserMessage ImageContent image);

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

        String illegalChat9(@UserMessage String userMessage, ChatRequestParameters cp1, ChatRequestParameters cp2);

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
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat1("What is the capital of Germany?")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_2() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat2("What is the capital of Germany?")).containsIgnoringCase("Berlin");

        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_2_1() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

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

        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThatThrownBy(() -> aiService.chat2_1("does not matter", invocationParameters))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("InvocationParameters cannot be null");
    }

    @Test
    void user_message_configuration_2_2() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat2_2("What is the capital of Germany?", new MyInvocationParameters()))
                .containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_3() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat3("What is the capital of {{country}}?", "Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_4() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat4()).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_5() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat5("Germany")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_6() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat6("Germany")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_7() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat7("capital", "Germany")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_8() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThat(aiService.chat8("Germany")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_9() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when
        aiService.chat9("Count the number of lamas in this image", imageContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                userMessage(TextContent.from("Count the number of lamas in this image"), imageContent))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_10() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when
        aiService.chat10(List.of(imageContent));

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                userMessage(TextContent.from("How many lamas are there in this image?"), imageContent))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_11() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when
        aiService.chat11(imageContent, "Count the number of lamas in this image", imageContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(userMessage(
                                imageContent,
                                TextContent.from("Count the number of lamas in this image"),
                                imageContent))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_12() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when
        aiService.chat12(new MyObject("test123"));

        // then
        verify(chatModel).chat(chatRequest("test123"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_13() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when
        aiService.chat13(new MyObject("test123"));

        // then
        verify(chatModel).chat(chatRequest("test123"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_14() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when
        aiService.chat14(new MyObject("Count the number of lamas in this image"), imageContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                userMessage(TextContent.from("Count the number of lamas in this image"), imageContent))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_15() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when
        aiService.chat15("Hello!", TextContent.from("How are you?"));

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(userMessage(TextContent.from("Hello!"), TextContent.from("How are you?")))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    /**
     * Regression test for https://github.com/langchain4j/langchain4j/issues/3091
     * Verifies that single-argument defaulting still works when a non-langchain4j
     * annotation is present on the parameter.
     *
     *  Using @NotExtensible here on purpose as a non-langchain4j annotation.
     * It is already available on the classpath; no additional dependency is added for this test.
     */
    @Test
    void user_message_configuration_16() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        assertThat(aiService.chat16("What is the capital of Germany?")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_17() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        assertThat(aiService.chat17("What is the capital of Germany?")).containsIgnoringCase("Berlin");
        verify(chatModel).chat(chatRequest("What is the capital of Germany?"));
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_18_1() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        AudioContent audioContent = AudioContent.from(base64Data);

        // when
        aiService.chat18_1(audioContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder().messages(userMessage(audioContent)).build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_18_2() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        AudioContent audioContent = AudioContent.from(base64Data);

        // when
        aiService.chat18_2(audioContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder().messages(userMessage(audioContent)).build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_19_1() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        AudioContent audioContent = AudioContent.from(base64Data);

        // when
        aiService.chat19_1(audioContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder().messages(userMessage(audioContent)).build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_19_2() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        AudioContent audioContent = AudioContent.from(base64Data);

        // when
        aiService.chat19_2(audioContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder().messages(userMessage(audioContent)).build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_20_1() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        List<Content> contents = List.of(TextContent.from("Analyze this audio:"), AudioContent.from(base64Data));

        // when
        aiService.chat20_1(contents);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(userMessage(contents.get(0), contents.get(1)))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_20_2() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        List<AudioContent> contents = List.of(AudioContent.from(base64Data));

        // when
        aiService.chat20_2(contents);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(userMessage(contents.get(0)))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_21_1() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        List<Content> contents = List.of(TextContent.from("Analyze this audio:"), AudioContent.from(base64Data));

        // when
        aiService.chat21_1(contents);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(userMessage(contents.get(0), contents.get(1)))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_21_2() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        List<AudioContent> contents = List.of(AudioContent.from(base64Data));

        // when
        aiService.chat21_2(contents);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(userMessage(contents.get(0)))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_22_1() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        AudioContent audioContent = AudioContent.from(base64Data);
        ImageContent imageContent = ImageContent.from("https://example.com/image.png");

        // when
        aiService.chat22_1(audioContent, imageContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(userMessage(audioContent, imageContent))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void user_message_configuration_22_2() {
        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        String base64Data = "AAECAw==";
        AudioContent audioContent = AudioContent.from(base64Data);
        ImageContent imageContent = ImageContent.from("https://example.com/image.png");

        // when
        aiService.chat22_2(audioContent, imageContent);

        // then
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(userMessage(audioContent, imageContent))
                        .build());
        verify(chatModel).supportedCapabilities();
    }

    @Test
    void illegal_user_message_configuration_1() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThatThrownBy(aiService::illegalChat1)
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Error: The method 'illegalChat1' does not have a user message defined.");
    }

    @Test
    void illegal_user_message_configuration_2() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat2("Germany"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Error: The method 'illegalChat2' does not have a user message defined.");
    }

    @Test
    void illegal_user_message_configuration_3() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat3("What is the capital of {{it}}?", "Germany"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage(
                        "The parameter 'arg0' in the method 'illegalChat3' of the class dev.langchain4j.service.AiServicesUserMessageConfigTest$AiService"
                                + VALIDATION_ERROR_MESSAGE_SUFFIX);
    }

    @Test
    void illegal_user_message_configuration_4() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat4("What is the capital of {{it}}?", "Germany"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage(
                        "The parameter 'arg1' in the method 'illegalChat4' of the class dev.langchain4j.service.AiServicesUserMessageConfigTest$AiService"
                                + VALIDATION_ERROR_MESSAGE_SUFFIX);
    }

    @Test
    void illegal_user_message_configuration_5() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThatThrownBy(aiService::illegalChat5)
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("@UserMessage's template cannot be empty");
    }

    @Test
    void illegal_user_message_configuration_6() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat6("Hello"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage(
                        "Error: The method 'illegalChat6' has multiple @UserMessage annotations. Please use only one.");
    }

    @Test
    void illegal_user_message_configuration_7() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat7("Hello", new InvocationParameters()))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("The parameter 'arg0' in the method 'illegalChat7' of the class "
                        + AiService.class.getName() + VALIDATION_ERROR_MESSAGE_SUFFIX);
    }

    @Test
    void illegal_user_message_configuration_8() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        InvocationParameters invocationParameters = new InvocationParameters();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat8("Hello", invocationParameters, invocationParameters))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("The method 'illegalChat8' of the class " + AiService.class.getName()
                        + " has more than one parameter of type " + InvocationParameters.class.getName());
    }

    @Test
    void illegal_user_message_configuration_9() {

        // given
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(chatModel).build();

        ChatRequestParameters chatRequestParameters =
                ChatRequestParameters.builder().build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat9("Hello", chatRequestParameters, chatRequestParameters))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("The method 'illegalChat9' of the class " + AiService.class.getName()
                        + " has more than one parameter of type " + ChatRequestParameters.class.getName());
    }
}
