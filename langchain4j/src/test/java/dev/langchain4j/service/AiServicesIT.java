package dev.langchain4j.service;

import dev.langchain4j.agent.tool.JsonSchemaProperty;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.Builder;
import lombok.ToString;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.agent.tool.JsonSchemaProperty.NUMBER;
import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;
import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.service.AiServicesIT.Sentiment.POSITIVE;
import static java.time.Month.JULY;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiServicesIT {

    private static final List<ToolSpecification> NO_TOOLS = null;

    @Spy
    ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Spy
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    @Spy
    ModerationModel moderationModel = OpenAiModerationModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .build();

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractions(chatLanguageModel);
        verifyNoMoreInteractions(chatMemory);
        verifyNoMoreInteractions(moderationModel);
    }


    interface Humorist {

        @UserMessage("Tell me a joke about {{it}}")
        String joke(String topic);
    }

    @Test
    void test_simple_instruction_with_single_argument() {

        Humorist humorist = AiServices.create(Humorist.class, chatLanguageModel);

        String joke = humorist.joke("AI");

        assertThat(joke).isNotBlank();
        System.out.println(joke);

        verify(chatLanguageModel).generate(singletonList(userMessage("Tell me a joke about AI")), NO_TOOLS);
    }


    interface DateTimeExtractor {

        @UserMessage("Extract date from {{it}}")
        LocalDate extractDateFrom(String text);

        @UserMessage("Extract time from {{it}}")
        LocalTime extractTimeFrom(String text);

        @UserMessage("Extract date and time from {{it}}")
        LocalDateTime extractDateTimeFrom(String text);
    }

    @Test
    void test_extract_date() {

        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatLanguageModel);

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDate date = dateTimeExtractor.extractDateFrom(text);

        assertThat(date).isEqualTo(LocalDate.of(1968, JULY, 4));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract date from " + text + "\n" +
                        "You must answer strictly in the following format: 2023-12-31")), NO_TOOLS);
    }

    @Test
    void test_extract_time() {

        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatLanguageModel);

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalTime time = dateTimeExtractor.extractTimeFrom(text);

        assertThat(time).isEqualTo(LocalTime.of(23, 45, 0));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract time from " + text + "\n" +
                        "You must answer strictly in the following format: 23:59:59")), NO_TOOLS);
    }

    @Test
    void test_extract_date_time() {

        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatLanguageModel);

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDateTime dateTime = dateTimeExtractor.extractDateTimeFrom(text);

        assertThat(dateTime).isEqualTo(LocalDateTime.of(1968, JULY, 4, 23, 45, 0));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract date and time from " + text + "\n" +
                        "You must answer strictly in the following format: 2023-12-31T23:59:59")), NO_TOOLS);
    }


    enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE
    }

    interface SentimentAnalyzer {

        @UserMessage("Analyze sentiment of {{it}}")
        Sentiment analyzeSentimentOf(String text);
    }

    @Test
    void test_extract_enum() {

        SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, chatLanguageModel);

        String customerReview = "This LaptopPro X15 is wicked fast and that 4K screen is a dream.";

        Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf(customerReview);

        assertThat(sentiment).isEqualTo(POSITIVE);

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Analyze sentiment of " + customerReview + "\n" +
                        "You must answer strictly in the following format: one of [POSITIVE, NEUTRAL, NEGATIVE]")), NO_TOOLS);
    }


    static class Person {

        private String firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    interface PersonExtractor {

        @UserMessage("Extract information about a person from {{it}}")
        Person extractPersonFrom(String text);
    }

    @Test
    void test_extract_custom_POJO() {

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, chatLanguageModel);

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey.";

        Person person = personExtractor.extractPersonFrom(text);

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract information about a person from " + text + "\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "}")), NO_TOOLS);
    }


    @ToString
    static class Recipe {

        private String title;
        private String description;
        @Description("each step should be described in 4 words, steps should rhyme")
        private String[] steps;
        private Integer preparationTimeMinutes;
    }

    interface Chef {

        @UserMessage("Create recipe using only {{it}}")
        Recipe createRecipeFrom(String... ingredients);

        Recipe createRecipeFrom(CreateRecipePrompt prompt);

        @SystemMessage("You are very {{character}} chef")
        Recipe createRecipeFrom(@UserMessage CreateRecipePrompt prompt, @V("character") String character);
    }

    @Test
    void test_create_recipe_from_list_of_ingredients() {

        Chef chef = AiServices.create(Chef.class, chatLanguageModel);

        Recipe recipe = chef.createRecipeFrom("cucumber", "tomato", "feta", "onion", "olives");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();
        System.out.println(recipe);

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Create recipe using only [cucumber, tomato, feta, onion, olives]\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"title\": (type: string),\n" +
                        "\"description\": (type: string),\n" +
                        "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n" +
                        "\"preparationTimeMinutes\": (type: integer),\n" +
                        "}")), NO_TOOLS);
    }


    @Builder
    @StructuredPrompt("Create a recipe of a {{dish}} that can be prepared using only {{ingredients}}")
    static class CreateRecipePrompt {

        private String dish;
        private List<String> ingredients;
    }

    @Test
    void test_create_recipe_using_structured_prompt() {

        Chef chef = AiServices.create(Chef.class, chatLanguageModel);

        CreateRecipePrompt prompt = CreateRecipePrompt.builder()
                .dish("salad")
                .ingredients(asList("cucumber", "tomato", "feta", "onion", "olives"))
                .build();

        Recipe recipe = chef.createRecipeFrom(prompt);

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();
        System.out.println(recipe);

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"title\": (type: string),\n" +
                        "\"description\": (type: string),\n" +
                        "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n" +
                        "\"preparationTimeMinutes\": (type: integer),\n" +
                        "}")), NO_TOOLS);
    }

    @Test
    void test_create_recipe_using_structured_prompt_and_system_message() {

        Chef chef = AiServices.create(Chef.class, chatLanguageModel);

        CreateRecipePrompt prompt = CreateRecipePrompt
                .builder()
                .dish("salad")
                .ingredients(asList("cucumber", "tomato", "feta", "onion", "olives"))
                .build();

        Recipe recipe = chef.createRecipeFrom(prompt, "funny");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();
        System.out.println(recipe);

        verify(chatLanguageModel).generate(asList(
                systemMessage("You are very funny chef"),
                userMessage("Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"title\": (type: string),\n" +
                        "\"description\": (type: string),\n" +
                        "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n" +
                        "\"preparationTimeMinutes\": (type: integer),\n" +
                        "}")
        ), NO_TOOLS);
    }

    interface ProfessionalChef {

        @SystemMessage("You are a professional chef. You are friendly, polite and concise.")
        String answer(String question);
    }

    @Test
    void test_with_system_message() {

        ProfessionalChef chef = AiServices.create(ProfessionalChef.class, chatLanguageModel);

        String question = "How long should I grill chicken?";

        String answer = chef.answer(question);

        assertThat(answer).isNotBlank();
        System.out.println(answer);

        verify(chatLanguageModel).generate(asList(
                systemMessage("You are a professional chef. You are friendly, polite and concise."),
                userMessage(question)
        ), NO_TOOLS);
    }


    interface Translator {

        @SystemMessage("You are a professional translator into {{language}}")
        @UserMessage("Translate the following text: {{text}}")
        String translate(@V("text") String text, @V("language") String language);
    }

    @Test
    void test_with_system_and_user_messages() {

        Translator translator = AiServices.create(Translator.class, chatLanguageModel);

        String text = "Hello, how are you?";

        String translation = translator.translate(text, "german");

        assertThat(translation).isEqualTo("Hallo, wie geht es dir?");

        verify(chatLanguageModel).generate(asList(
                systemMessage("You are a professional translator into german"),
                userMessage("Translate the following text: Hello, how are you?")
        ), NO_TOOLS);
    }

    interface Summarizer {

        @SystemMessage("Summarize every message from user in {{n}} bullet points. Provide only bullet points.")
        List<String> summarize(@UserMessage String text, @V("n") int n);
    }

    @Test
    void test_with_system_message_and_user_message_as_argument() {

        Summarizer summarizer = AiServices.create(Summarizer.class, chatLanguageModel);

        String text = "AI, or artificial intelligence, is a branch of computer science that aims to create " +
                "machines that mimic human intelligence. This can range from simple tasks such as recognizing " +
                "patterns or speech to more complex tasks like making decisions or predictions.";

        List<String> bulletPoints = summarizer.summarize(text, 3);

        assertThat(bulletPoints).hasSize(3);
        System.out.println(bulletPoints);

        verify(chatLanguageModel).generate(asList(
                systemMessage("Summarize every message from user in 3 bullet points. Provide only bullet points."),
                userMessage(text + "\nYou must put every item on a separate line.")
        ), NO_TOOLS);
    }


    interface ChatWithModeration {

        @Moderate
        String chat(String message);
    }

    @Test
    void should_throw_when_text_is_flagged() {

        ChatWithModeration chatWithModeration = AiServices.builder(ChatWithModeration.class)
                .chatLanguageModel(chatLanguageModel)
                .moderationModel(moderationModel)
                .build();

        String message = "I WILL KILL YOU!!!";

        assertThatThrownBy(() -> chatWithModeration.chat(message))
                .isExactlyInstanceOf(ModerationException.class)
                .hasMessage("Text \"" + message + "\" violates content policy");

        verify(chatLanguageModel).generate(singletonList(userMessage(message)), NO_TOOLS);
        verify(moderationModel).moderate(singletonList(userMessage(message)));
    }

    @Test
    void should_not_throw_when_text_is_not_flagged() {

        ChatWithModeration chatWithModeration = AiServices.builder(ChatWithModeration.class)
                .chatLanguageModel(chatLanguageModel)
                .moderationModel(moderationModel)
                .build();

        String message = "I will hug them!";

        String response = chatWithModeration.chat(message);

        assertThat(response).isNotBlank();

        verify(chatLanguageModel).generate(singletonList(userMessage(message)), NO_TOOLS);
        verify(moderationModel).moderate(singletonList(userMessage(message)));
    }


    interface ChatWithMemory {

        String chatWithoutSystemMessage(String userMessage);

        @SystemMessage("You are helpful assistant")
        String chatWithSystemMessage(String userMessage);

        @SystemMessage("You are funny assistant")
        String chatWithAnotherSystemMessage(String userMessage);
    }

    @Test
    void should_keep_chat_memory() {

        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String firstUserMessage = "Hello, my name is Klaus";
        String firstAiMessage = chatWithMemory.chatWithoutSystemMessage(firstUserMessage);

        verify(chatMemory).add(userMessage(firstUserMessage));
        verify(chatLanguageModel).generate(singletonList(userMessage(firstUserMessage)), NO_TOOLS);
        verify(chatMemory).add(aiMessage(firstAiMessage));

        String secondUserMessage = "What is my name?";
        String secondAiMessage = chatWithMemory.chatWithoutSystemMessage(secondUserMessage);
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatMemory).add(userMessage(secondUserMessage));
        verify(chatLanguageModel).generate(asList(
                userMessage(firstUserMessage),
                aiMessage(firstAiMessage),
                userMessage(secondUserMessage)
        ), NO_TOOLS);
        verify(chatMemory).add(aiMessage(secondAiMessage));
        verify(chatMemory, times(6)).messages();
    }

    @Test
    void should_keep_chat_memory_and_not_duplicate_system_message() {

        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String systemMessage = "You are helpful assistant";
        String firstUserMessage = "Hello, my name is Klaus";
        String firstAiMessage = chatWithMemory.chatWithSystemMessage(firstUserMessage);

        verify(chatLanguageModel).generate(asList(
                systemMessage(systemMessage),
                userMessage(firstUserMessage)
        ), NO_TOOLS);

        String secondUserMessage = "What is my name?";
        String secondAiMessage = chatWithMemory.chatWithSystemMessage(secondUserMessage);
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatLanguageModel).generate(asList(
                systemMessage(systemMessage),
                userMessage(firstUserMessage),
                aiMessage(firstAiMessage),
                userMessage(secondUserMessage)
        ), NO_TOOLS);

        verify(chatMemory, times(2)).add(systemMessage(systemMessage));
        verify(chatMemory).add(userMessage(firstUserMessage));
        verify(chatMemory).add(aiMessage(firstAiMessage));
        verify(chatMemory).add(userMessage(secondUserMessage));
        verify(chatMemory).add(aiMessage(secondAiMessage));
        verify(chatMemory, times(8)).messages();
    }

    @Test
    void should_keep_chat_memory_and_add_new_system_message() {

        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String firstSystemMessage = "You are helpful assistant";
        String firstUserMessage = "Hello, my name is Klaus";
        String firstAiMessage = chatWithMemory.chatWithSystemMessage(firstUserMessage);

        verify(chatLanguageModel).generate(asList(
                systemMessage(firstSystemMessage),
                userMessage(firstUserMessage)
        ), NO_TOOLS);


        String secondSystemMessage = "You are funny assistant";
        String secondUserMessage = "What is my name?";
        String secondAiMessage = chatWithMemory.chatWithAnotherSystemMessage(secondUserMessage);
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatLanguageModel).generate(asList(
                userMessage(firstUserMessage),
                aiMessage(firstAiMessage),
                systemMessage(secondSystemMessage),
                userMessage(secondUserMessage)
        ), NO_TOOLS);

        verify(chatMemory).add(systemMessage(firstSystemMessage));
        verify(chatMemory).add(userMessage(firstUserMessage));
        verify(chatMemory).add(aiMessage(firstAiMessage));
        verify(chatMemory).add(aiMessage(secondAiMessage));
        verify(chatMemory).add(systemMessage(secondSystemMessage));
        verify(chatMemory).add(userMessage(secondUserMessage));
        verify(chatMemory, times(8)).messages();
    }


    interface ChatWithSeparateMemoryForEachUser {

        String chat(@MemoryId int memoryId, @UserMessage String userMessage);
    }

    @Test
    void should_keep_separate_chat_memory_for_each_user_in_store() {

        // emulating persistent storage
        Map</* memoryId */ Object, String> persistentStorage = new HashMap<>();

        ChatMemoryStore store = new ChatMemoryStore() {

            @Override
            public List<ChatMessage> getMessages(Object memoryId) {
                return messagesFromJson(persistentStorage.get(memoryId));
            }

            @Override
            public void updateMessages(Object memoryId, List<ChatMessage> messages) {
                persistentStorage.put(memoryId, messagesToJson(messages));
            }

            @Override
            public void deleteMessages(Object memoryId) {
                persistentStorage.remove(memoryId);
            }
        };

        ChatMemoryProvider chatMemoryProvider = memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(10)
                .chatMemoryStore(store)
                .build();

        int firstMemoryId = 1;
        int secondMemoryId = 2;

        ChatWithSeparateMemoryForEachUser chatWithMemory = AiServices.builder(ChatWithSeparateMemoryForEachUser.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemoryProvider(chatMemoryProvider)
                .build();

        String firstMessageFromFirstUser = "Hello, my name is Klaus";
        String firstAiResponseToFirstUser = chatWithMemory.chat(firstMemoryId, firstMessageFromFirstUser);
        verify(chatLanguageModel).generate(singletonList(userMessage(firstMessageFromFirstUser)), NO_TOOLS);

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        String firstAiResponseToSecondUser = chatWithMemory.chat(secondMemoryId, firstMessageFromSecondUser);
        verify(chatLanguageModel).generate(singletonList(userMessage(firstMessageFromSecondUser)), NO_TOOLS);

        String secondMessageFromFirstUser = "What is my name?";
        String secondAiResponseToFirstUser = chatWithMemory.chat(firstMemoryId, secondMessageFromFirstUser);
        assertThat(secondAiResponseToFirstUser).contains("Klaus");
        verify(chatLanguageModel).generate(asList(
                userMessage(firstMessageFromFirstUser),
                aiMessage(firstAiResponseToFirstUser),
                userMessage(secondMessageFromFirstUser)
        ), NO_TOOLS);

        String secondMessageFromSecondUser = "What is my name?";
        String secondAiResponseToSecondUser = chatWithMemory.chat(secondMemoryId, secondMessageFromSecondUser);
        assertThat(secondAiResponseToSecondUser).contains("Francine");
        verify(chatLanguageModel).generate(asList(
                userMessage(firstMessageFromSecondUser),
                aiMessage(firstAiResponseToSecondUser),
                userMessage(secondMessageFromSecondUser)
        ), NO_TOOLS);

        assertThat(persistentStorage).containsOnlyKeys(firstMemoryId, secondMemoryId);

        List<ChatMessage> persistedMessagesOfFirstUser = messagesFromJson(persistentStorage.get(firstMemoryId));
        assertThat(persistedMessagesOfFirstUser).containsExactly(
                userMessage(firstMessageFromFirstUser),
                aiMessage(firstAiResponseToFirstUser),
                userMessage(secondMessageFromFirstUser),
                aiMessage(secondAiResponseToFirstUser)
        );

        List<ChatMessage> persistedMessagesOfSecondUser = messagesFromJson(persistentStorage.get(secondMemoryId));
        assertThat(persistedMessagesOfSecondUser).containsExactly(
                userMessage(firstMessageFromSecondUser),
                aiMessage(firstAiResponseToSecondUser),
                userMessage(secondMessageFromSecondUser),
                aiMessage(secondAiResponseToSecondUser)
        );
    }


    interface Assistant {

        String chat(String userMessage);
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            return Math.sqrt(number);
        }
    }

    @Test
    void should_execute_tool_then_answer() {

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        String answer = assistant.chat(userMessage);

        assertThat(answer).contains("6.97");


        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.toolExecutionRequest().name()).isEqualTo("squareRoot");
        assertThat(aiMessage.toolExecutionRequest().arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");
        assertThat(messages.get(1).text()).isNull();

        assertThat(messages.get(2)).isInstanceOf(ToolExecutionResultMessage.class);
        assertThat(messages.get(2).text()).isEqualTo("6.97070153193991E8");

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3).text()).contains("6.97");

        verify(chatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(ToolSpecification.builder()
                        .name("squareRoot")
                        .description("calculates the square root of the provided number")
                        .addParameter("arg0", NUMBER, JsonSchemaProperty.description("number to operate on"))
                        .build())
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(ToolSpecification.builder()
                        .name("squareRoot")
                        .description("calculates the square root of the provided number")
                        .addParameter("arg0", NUMBER, JsonSchemaProperty.description("number to operate on"))
                        .build())
        );
    }
}
