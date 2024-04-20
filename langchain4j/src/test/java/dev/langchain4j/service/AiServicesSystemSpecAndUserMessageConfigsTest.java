package dev.langchain4j.service;

import dev.langchain4j.exception.IllegalConfigurationException;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.service.SystemSpecService.MULTI_PROMPT_ROUTER_TEMPLATE;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AiServicesSystemSpecAndUserMessageConfigsTest {

    @Spy
    ChatLanguageModel chatLanguageModel;
    public static final String ANSWER_INSTRUCTIONS = "When you don't know the answer to a question you admit that you don't know.";
    public static final String COUNTRY = "United States";
    static final String PROMPT_PHYSICS = "What is the speed of light?";
    static final String PROMPT_MATHS = "What is the derivative of x^2?";
    static final String PROMPT_HISTORY = "Who was the first president of the United States?";
    static final String PROMPT_SYSTEM_MESSAGE = "Who is it ?";
    static final String RESPONSE_PHYSICS = "299,792 kilometers per second";
    static final String RESPONSE_MATHS = "2x";
    static final String RESPONSE_HISTORY = "George Washington";
    static final String RESPONSE_SYSTEM_MESSAGE = "it's system message";
    static final String PROMPT_NAMES = "physics : Good for answering questions about physics\n" +
            "math : Good for answering math questions\n" +
            "history : Good for answering questions about history\n";
    static final String KEY_MULTI_PROMPT_ROUTER_PHYSICS = String.format(MULTI_PROMPT_ROUTER_TEMPLATE, PROMPT_NAMES, PROMPT_PHYSICS);
    static final String KEY_MULTI_PROMPT_ROUTER_MATHS = String.format(MULTI_PROMPT_ROUTER_TEMPLATE, PROMPT_NAMES, PROMPT_MATHS);
    static final String KEY_MULTI_PROMPT_ROUTER_HISTORY = String.format(MULTI_PROMPT_ROUTER_TEMPLATE, PROMPT_NAMES, PROMPT_HISTORY);
    static final String TEMPLATE_PHYSICS = "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
            "\n" +
            "Here is a question:";
    static final String TEMPLATE_MATH = "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
            "\n" +
            "Here is a question:";
    static final String TEMPLATE_HISTORY = "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
            "\n" +
            "Here is a question:";

    static final String TEMPLATE_PHYSICS_WITH_ANSWER_INSTRUCTION = "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
            "\n" +
            "Here is a question:";
    static final String TEMPLATE_HISTORY_WITH_ANSWER_INSTRUCTION = "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
            "\n" +
            "Here is a question:";
    static final String TEMPLATE_SYSTEM_MESSAGE = "Act like system message\n" +
            "\n" +
            "Here is a question:";
    List<dev.langchain4j.model.SystemSpec> systemSpecProvider;
    List<dev.langchain4j.model.SystemSpec> systemSpecProviderWithAnswerInstruction;

    {
        List<dev.langchain4j.model.SystemSpec> responseList= new ArrayList<>();
        responseList.add(dev.langchain4j.model.SystemSpec.builder()
                .name("physics")
                .description("Good for answering questions about physics")
                .template(new String[]{TEMPLATE_PHYSICS})
                .build());
        responseList.add(dev.langchain4j.model.SystemSpec.builder()
                .name("math")
                .description("Good for answering math questions")
                .template(new String[]{TEMPLATE_MATH})
                .build());
        responseList.add(dev.langchain4j.model.SystemSpec.builder()
                .name("history")
                .description("Good for answering questions about history")
                .template(new String[]{TEMPLATE_HISTORY})
                .build());
        systemSpecProvider = responseList;

        List<dev.langchain4j.model.SystemSpec> responseListWithAnswerInstruction = new ArrayList<>();
        responseListWithAnswerInstruction.add(dev.langchain4j.model.SystemSpec.builder()
                .name("physics")
                .description("Good for answering questions about physics")
                .template(new String[]{TEMPLATE_PHYSICS_WITH_ANSWER_INSTRUCTION})
                .build());
        responseListWithAnswerInstruction.add(dev.langchain4j.model.SystemSpec.builder()
                .name("math")
                .description("Good for answering math questions")
                .template(new String[]{TEMPLATE_MATH})
                .build());
        responseListWithAnswerInstruction.add(dev.langchain4j.model.SystemSpec.builder()
                .name("history")
                .description("Good for answering questions about history")
                .template(new String[]{TEMPLATE_HISTORY_WITH_ANSWER_INSTRUCTION})
                .build());
        systemSpecProviderWithAnswerInstruction = responseListWithAnswerInstruction;

        Map<String, String> responseMap = new java.util.HashMap<>();
        responseMap.put(KEY_MULTI_PROMPT_ROUTER_PHYSICS,
                "{" +
                        "\"destination\": \"physics\", " +
                        "\"nextInputs\": \"What is the speed of light?\"" +
                        "}");
        responseMap.put(KEY_MULTI_PROMPT_ROUTER_MATHS,
                "{" +
                        "\"destination\": \"math\", " +
                        "\"nextInputs\": \"What is the derivative of x^2?\"" +
                        "}");
        responseMap.put(KEY_MULTI_PROMPT_ROUTER_HISTORY,
                "{" +
                        "\"destination\": \"history\", " +
                        "\"nextInputs\": \"Who was the first president of the United States?\"" +
                        "}");
        responseMap.put(TEMPLATE_PHYSICS + '\n' + PROMPT_PHYSICS, RESPONSE_PHYSICS);
        responseMap.put(TEMPLATE_MATH + '\n' + PROMPT_MATHS, RESPONSE_MATHS);
        responseMap.put(TEMPLATE_HISTORY + '\n' + PROMPT_HISTORY, RESPONSE_HISTORY);
        responseMap.put(TEMPLATE_SYSTEM_MESSAGE + '\n' + PROMPT_SYSTEM_MESSAGE, RESPONSE_SYSTEM_MESSAGE);
        chatLanguageModel = ChatModelMock.thatAlwaysRespondsMap(responseMap);
    }

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractions(chatLanguageModel);
    }

    interface AiService {

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        String chat1(String userMessage);

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        String chat2(@UserMessage String userMessage);

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        String chat3(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage);

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        String chat4(@UserMessage String userMessage, @V("equation") String equation);

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        String chat5(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage, @V("country") String country);

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        @UserMessage(PROMPT_PHYSICS)
        String chat6();

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        @UserMessage(PROMPT_PHYSICS)
        String chat7(@V("answerInstructions") String answerInstructions);

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        @UserMessage("Who was the first president of the {{it}}?")
        String chat8(String country);

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        @UserMessage("Who was the first president of the {{country}}?")
        String chat9(@V("country") String country);

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        @UserMessage("Who was the first president of the {{country}}?")
        String chat10(@V("answerInstructions") String answerInstructions, @V("country") String country);

        // with SystemSpecProvider
        String chat11(String userMessage);

        // with SystemSpecProvider
        String chat12(@UserMessage String userMessage);

        // with SystemSpecProvider
        String chat13(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage);

        // with SystemSpecProvider
        String chat14(@UserMessage String userMessage, @V("country") String country);

        // with SystemSpecProvider
        String chat15(@V("answerInstructions") String answerInstructions, @UserMessage String userMessage, @V("country") String country);

        // with SystemSpecProvider
        @UserMessage(PROMPT_HISTORY)
        String chat16();

        // with SystemSpecProvider
        @UserMessage(PROMPT_HISTORY)
        String chat17(@V("answerInstructions") String answerInstructions);

        // with SystemSpecProvider
        @UserMessage("Who was the first president of the {{it}}?")
        String chat18(String country);

        // with SystemSpecProvider
        @UserMessage("Who was the first president of the {{country}}?")
        String chat19(@V("country") String country);

        // with SystemSpecProvider
        @UserMessage("Who was the first president of the {{country}}?")
        String chat20(@V("answerInstructions") String answerInstructions, @V("country") String country);

        // with SystemSpecProvider
        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        }) // This message should take precedence over the one provided by SystemSpecProvider
        String chat21(String userMessage);

        // System spec ignored when System message exists

        @SystemMessage(TEMPLATE_SYSTEM_MESSAGE)
        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart phys                                \"\\n\" +\nics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        String chat22(String userMessage);

        // with SystemMessageProvider : system spec should be ignored
        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart phys                                \"\\n\" +\nics professor. You are great at answering questions about physics in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. When you don't know the answer to a question you admit that you don't know.\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        String chat23(String userMessage);

        @SystemMessage(TEMPLATE_SYSTEM_MESSAGE)
            // with SystemSpecProvider : system spec should be ignored
        String chat24(String userMessage);

        // with SystemMessageProvider and SystemSpecProvider: system spec should be ignored
        String chat25(String userMessage);

        // illegal

        @RegisterSystemSpecs({
                @SystemSpec(name = "physics", description = "Good for answering questions about physics", template = {
                        "You are a very smart physics professor. You are great at answering questions about physics in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "math", description = "Good for answering math questions", template = {
                        "You are a very good mathematician. You are great at answering math questions. You are so good because you are able to break down hard problems into their component parts, answer the component parts, and then put them together to answer the broader question.\n" +
                                "\n" +
                                "Here is a question:"
                }),
                @SystemSpec(name = "history", description = "Good for answering questions about history", template = {
                        "You are a very smart history professor. You are great at answering questions about history in a concise and easy to understand manner. {{answerInstructions}}\n" +
                                "\n" +
                                "Here is a question:"
                })
        })
        String illegalChat1(@V("answerInstructions") String answerInstructions, String userMessage);

        // with SystemSpecProvider
        String illegalChat2(@V("answerInstructions") String answerInstructions, String userMessage);

    }

    @Test
    void test_system_spec_configuration_1() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat1(PROMPT_PHYSICS))
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_configuration_2() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat2(PROMPT_PHYSICS))
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_configuration_3() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat3(ANSWER_INSTRUCTIONS, PROMPT_PHYSICS))
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_configuration_4() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat4("What is the derivative of {{equation}}?", "x^2"))
                .containsIgnoringCase(RESPONSE_MATHS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_MATHS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_MATH),
                userMessage(PROMPT_MATHS)
        ));
    }

    @Test
    void test_system_spec_configuration_5() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat5(ANSWER_INSTRUCTIONS, "Who was the first president of the {{country}}?", COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_6() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat6())
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_configuration_7() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat7(ANSWER_INSTRUCTIONS))
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_configuration_8() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat8(COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_9() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat9(COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_10() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat10(ANSWER_INSTRUCTIONS, COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_11() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThat(aiService.chat11(PROMPT_PHYSICS))
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_configuration_12() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThat(aiService.chat12(PROMPT_PHYSICS))
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_configuration_13() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProviderWithAnswerInstruction)
                .build();

        // when-then
        assertThat(aiService.chat13(ANSWER_INSTRUCTIONS, PROMPT_PHYSICS))
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_configuration_14() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThat(aiService.chat14("Who was the first president of the {{country}}?", COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_15() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProviderWithAnswerInstruction)
                .build();

        // when-then
        assertThat(aiService.chat15(ANSWER_INSTRUCTIONS, "Who was the first president of the {{country}}?", COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_16() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThat(aiService.chat16())
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_17() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProviderWithAnswerInstruction)
                .build();

        // when-then
        assertThat(aiService.chat17(ANSWER_INSTRUCTIONS))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_18() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThat(aiService.chat18(COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_19() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThat(aiService.chat19(COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_20() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProviderWithAnswerInstruction)
                .build();

        // when-then
        assertThat(aiService.chat20(ANSWER_INSTRUCTIONS, COUNTRY))
                .containsIgnoringCase(RESPONSE_HISTORY);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_HISTORY)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_HISTORY),
                userMessage(PROMPT_HISTORY)
        ));
    }

    @Test
    void test_system_spec_configuration_21() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> new ArrayList<>()) // "This message should be ignored"
                .build();

        // when-then
        assertThat(aiService.chat21(PROMPT_PHYSICS))
                .containsIgnoringCase(RESPONSE_PHYSICS);
        verify(chatLanguageModel, times(2)).generate(anyList());

        verify(chatLanguageModel).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_PHYSICS),
                userMessage(PROMPT_PHYSICS)
        ));
    }

    @Test
    void test_system_spec_ignored_when_system_message_exists_configuration_22() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThat(aiService.chat22(PROMPT_SYSTEM_MESSAGE))
                .containsIgnoringCase(RESPONSE_SYSTEM_MESSAGE);
        verify(chatLanguageModel, times(1)).generate(anyList());

        verify(chatLanguageModel, times(0)).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_SYSTEM_MESSAGE),
                userMessage(PROMPT_SYSTEM_MESSAGE)
        ));
    }


    @Test
    void test_system_spec_ignored_when_system_message_exists_configuration_23() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> TEMPLATE_SYSTEM_MESSAGE)
                .build();

        // when-then
        assertThat(aiService.chat23(PROMPT_SYSTEM_MESSAGE))
                .containsIgnoringCase(RESPONSE_SYSTEM_MESSAGE);
        verify(chatLanguageModel, times(1)).generate(anyList());

        verify(chatLanguageModel, times(0)).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_SYSTEM_MESSAGE),
                userMessage(PROMPT_SYSTEM_MESSAGE)
        ));
    }

    @Test
    void test_system_spec_ignored_when_system_message_exists_configuration_24() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThat(aiService.chat24(PROMPT_SYSTEM_MESSAGE))
                .containsIgnoringCase(RESPONSE_SYSTEM_MESSAGE);
        verify(chatLanguageModel, times(1)).generate(anyList());

        verify(chatLanguageModel, times(0)).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_SYSTEM_MESSAGE),
                userMessage(PROMPT_SYSTEM_MESSAGE)
        ));
    }


    @Test
    void test_system_spec_ignored_when_system_message_exists_configuration_25() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemMessageProvider(chatMemoryId -> TEMPLATE_SYSTEM_MESSAGE)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThat(aiService.chat25(PROMPT_SYSTEM_MESSAGE))
                .containsIgnoringCase(RESPONSE_SYSTEM_MESSAGE);
        verify(chatLanguageModel, times(1)).generate(anyList());

        verify(chatLanguageModel, times(0)).generate(Collections.singletonList(
                userMessage(KEY_MULTI_PROMPT_ROUTER_PHYSICS)
        ));

        verify(chatLanguageModel).generate(asList(
                systemMessage(TEMPLATE_SYSTEM_MESSAGE),
                userMessage(PROMPT_SYSTEM_MESSAGE)
        ));
    }

    @Test
    void test_illegal_system_spec_configuration_1() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat1(ANSWER_INSTRUCTIONS, COUNTRY))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Parameter 'arg1' of method 'illegalChat1' should be annotated " +
                        "with @V or @UserMessage or @UserName or @MemoryId");
    }

    @Test
    void test_illegal_system_spec_configuration_2() {

        // given
        AiService aiService = AiServices.builder(AiService.class)
                .chatLanguageModel(chatLanguageModel)
                .systemSpecProvider(chatMemoryId -> systemSpecProvider)
                .build();

        // when-then
        assertThatThrownBy(() -> aiService.illegalChat2(ANSWER_INSTRUCTIONS, COUNTRY))
                .isExactlyInstanceOf(IllegalConfigurationException.class)
                .hasMessage("Parameter 'arg1' of method 'illegalChat2' should be annotated " +
                        "with @V or @UserMessage or @UserName or @MemoryId");
    }
}
