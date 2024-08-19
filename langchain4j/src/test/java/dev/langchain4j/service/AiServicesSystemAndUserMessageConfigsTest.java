package dev.langchain4j.service;

import dev.langchain4j.exception.IllegalConfigurationException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AiServicesSystemAndUserMessageConfigsTest {

    @Spy
    ChatLanguageModel chatLanguageModel = ChatModelMock.thatAlwaysResponds("Berlin");

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractions(chatLanguageModel);
    }

    interface AiService {

        @SystemMessage("Given a name of a country, answer with a name of it's capital")
        String chat1(String userMessage);

        @SystemMessage("Given a name of a country, answer with a name of it's capital")
        String chat2(@UserMessage String userMessage);

        @SystemMessage("Given a name of a country, answer with {{answerInstructions}}")
        String chat3(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage);

        @SystemMessage("Given a name of a country, answer with a name of it's capital")
        String chat4(@UserMessage String userMessage, @V("country") String country);

        @SystemMessage("Given a name of a country, answer with {{answerInstructions}}")
        String chat5(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage, @V("country") String country);

        @SystemMessage("Given a name of a country, answer with a name of it's capital")
        @UserMessage("Country: Germany")
        String chat6();

        @SystemMessage("Given a name of a country, answer with {{answerInstructions}}")
        @UserMessage("Country: Germany")
        String chat7(@V("answerInstructions") String answerInstructions);

        @SystemMessage("Given a name of a country, answer with a name of it's capital")
        @UserMessage("Country: {{it}}")
        String chat8(String country);

        @SystemMessage("Given a name of a country, answer with a name of it's capital")
        @UserMessage("Country: {{country}}")
        String chat9(@V("country") String country);

        @SystemMessage("Given a name of a country, answer with {{answerInstructions}}")
        @UserMessage("Country: {{country}}")
        String chat10(@V("answerInstructions") String answerInstructions, @V("country") String country);

        // with systemMessageProvider
        String chat11(String userMessage);

        // with systemMessageProvider
        String chat12(@UserMessage String userMessage);

        // with systemMessageProvider
        String chat13(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage);

        // with systemMessageProvider
        String chat14(@UserMessage String userMessage, @V("country") String country);

        // with systemMessageProvider
        String chat15(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage, @V("country") String country);

        // with systemMessageProvider
        @UserMessage("Country: Germany")
        String chat16();

        // with systemMessageProvider
        @UserMessage("Country: Germany")
        String chat17(@V("answerInstructions") String answerInstructions);

        // with systemMessageProvider
        @UserMessage("Country: {{it}}")
        String chat18(String country);

        // with systemMessageProvider
        @UserMessage("Country: {{country}}")
        String chat19(@V("country") String country);

        // with systemMessageProvider
        @UserMessage("Country: {{country}}")
        String chat20(@V("answerInstructions") String answerInstructions, @V("country") String country);

        // with systemMessageProvider
        @SystemMessage("This message should take precedence over the one provided by systemMessageProvider")
        String chat21(String userMessage);


        // illegal

        @SystemMessage("Given a name of a country, answer with {{answerInstructions}}")
        String illegalChat1(@V("answerInstructions") String answerInstructions, String userMessage);

        // with systemMessageProvider
        String illegalChat2(@V("answerInstructions") String answerInstructions, String userMessage);
    }

    @Test
    void test_system_message_configuration_1() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat1("Country: Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_2() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat2("Country: Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_3() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat3("a name of it's capital", "Country: Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_4() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat4("Country: {{country}}", "Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_5() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat5("a name of it's capital", "Country: {{country}}", "Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_6() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat6())
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_7() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat7("a name of it's capital"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_8() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat8("Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_9() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat9("Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_10() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat10("a name of it's capital", "Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_11() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with a name of it's capital")
                .build();

        // when-then
        assertThat(aiService.chat11("Country: Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_12() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with a name of it's capital")
                .build();

        // when-then
        assertThat(aiService.chat12("Country: Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_13() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with {{answerInstructions}}")
                .build();

        // when-then
        assertThat(aiService.chat13("a name of it's capital", "Country: Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_14() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with a name of it's capital")
                .build();

        // when-then
        assertThat(aiService.chat14("Country: {{country}}", "Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_15() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with {{answerInstructions}}")
                .build();

        // when-then
        assertThat(aiService.chat15("a name of it's capital", "Country: {{country}}", "Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_16() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with a name of it's capital")
                .build();

        // when-then
        assertThat(aiService.chat16())
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_17() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with {{answerInstructions}}")
                .build();

        // when-then
        assertThat(aiService.chat17("a name of it's capital"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_18() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with a name of it's capital")
                .build();

        // when-then
        assertThat(aiService.chat18("Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_19() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with a name of it's capital")
                .build();

        // when-then
        assertThat(aiService.chat19("Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_20() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with {{answerInstructions}}")
                .build();

        // when-then
        assertThat(aiService.chat20("a name of it's capital", "Germany"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("Given a name of a country, answer with a name of it's capital"),
                userMessage("Country: Germany")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_system_message_configuration_21() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "This message should be ignored")
                .build();

        // when-then
        assertThat(aiService.chat21("What is the capital of Germany?"))
                .containsIgnoringCase("Berlin");
        verify(chatLanguageModel).generate(asList(
                systemMessage("This message should take precedence over the one provided by systemMessageProvider"),
                userMessage("What is the capital of Germany?")
        ));
        verify(chatLanguageModel).supportedCapabilities();
    }

    @Test
    void test_illegal_system_message_configuration_1() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat1("a name of it's capital", "Country: Germany"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Parameter 'arg1' of method 'illegalChat1' should be annotated " +
                        "with @V or @UserMessage or @UserName or @MemoryId");
    }

    @Test
    void test_illegal_system_message_configuration_2() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> "Given a name of a country, answer with {{answerInstructions}}")
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat2("a name of it's capital", "Country: Germany"))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Parameter 'arg1' of method 'illegalChat2' should be annotated " +
                        "with @V or @UserMessage or @UserName or @MemoryId");
    }
}
