package dev.langchain4j.service;

import dev.langchain4j.agent.tool.*;
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
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
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
import static dev.langchain4j.model.openai.OpenAiModelName.GPT_3_5_TURBO_1106;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static dev.langchain4j.service.AiServicesIT.ChatWithMemory.ANOTHER_SYSTEM_MESSAGE;
import static dev.langchain4j.service.AiServicesIT.ChatWithMemory.SYSTEM_MESSAGE;
import static dev.langchain4j.service.AiServicesIT.Sentiment.POSITIVE;
import static java.time.Month.JULY;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Percentage.withPercentage;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AiServicesIT {

    @Spy
    ChatLanguageModel chatLanguageModel = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Spy
    ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    @Spy
    ModerationModel moderationModel = OpenAiModerationModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .build();

    ToolSpecification calculatorSpecification = ToolSpecification.builder()
            .name("squareRoot")
            .description("calculates the square root of the provided number")
            .addParameter("arg0", NUMBER, JsonSchemaProperty.description("number to operate on"))
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
        System.out.println(joke);

        assertThat(joke).isNotBlank();

        verify(chatLanguageModel).generate(singletonList(userMessage("Tell me a joke about AI")));
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
        System.out.println(date);

        assertThat(date).isEqualTo(LocalDate.of(1968, JULY, 4));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract date from " + text + "\n" +
                        "You must answer strictly in the following format: 2023-12-31")));
    }

    @Test
    void test_extract_time() {

        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatLanguageModel);

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalTime time = dateTimeExtractor.extractTimeFrom(text);
        System.out.println(time);

        assertThat(time).isEqualTo(LocalTime.of(23, 45, 0));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract time from " + text + "\n" +
                        "You must answer strictly in the following format: 23:59:59")));
    }

    @Test
    void test_extract_date_time() {

        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatLanguageModel);

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDateTime dateTime = dateTimeExtractor.extractDateTimeFrom(text);
        System.out.println(dateTimeExtractor);

        assertThat(dateTime).isEqualTo(LocalDateTime.of(1968, JULY, 4, 23, 45, 0));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract date and time from " + text + "\n" +
                        "You must answer strictly in the following format: 2023-12-31T23:59:59")));
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
        System.out.println(sentiment);

        assertThat(sentiment).isEqualTo(POSITIVE);

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Analyze sentiment of " + customerReview + "\n" +
                        "You must answer strictly in the following format: one of [POSITIVE, NEUTRAL, NEGATIVE]")));
    }


    @ToString
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
    void should_extract_custom_POJO() {

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, chatLanguageModel);

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey.";

        Person person = personExtractor.extractPersonFrom(text);
        System.out.println(person);

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract information about a person from " + text + "\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "}")));
    }

    @Test
    void should_extract_custom_POJO_with_explicit_json_response_format() {

        ChatLanguageModel chatLanguageModel = spy(OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_3_5_TURBO_1106) // supports response_format = 'json_object'
                .responseFormat("json_object")
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, chatLanguageModel);

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey.";

        Person person = personExtractor.extractPersonFrom(text);
        System.out.println(person);

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Extract information about a person from " + text + "\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"firstName\": (type: string),\n" +
                        "\"lastName\": (type: string),\n" +
                        "\"birthDate\": (type: date string (2023-12-31)),\n" +
                        "}")));
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
        System.out.println(recipe);

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Create recipe using only [cucumber, tomato, feta, onion, olives]\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"title\": (type: string),\n" +
                        "\"description\": (type: string),\n" +
                        "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n" +
                        "\"preparationTimeMinutes\": (type: integer),\n" +
                        "}")));
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
        System.out.println(recipe);

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatLanguageModel).generate(singletonList(userMessage(
                "Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"title\": (type: string),\n" +
                        "\"description\": (type: string),\n" +
                        "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n" +
                        "\"preparationTimeMinutes\": (type: integer),\n" +
                        "}")));
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
        System.out.println(recipe);

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();

        verify(chatLanguageModel).generate(asList(
                systemMessage("You are very funny chef"),
                userMessage("Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"title\": (type: string),\n" +
                        "\"description\": (type: string),\n" +
                        "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n" +
                        "\"preparationTimeMinutes\": (type: integer),\n" +
                        "}")
        ));
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
        System.out.println(answer);

        assertThat(answer).isNotBlank();

        verify(chatLanguageModel).generate(asList(
                systemMessage("You are a professional chef. You are friendly, polite and concise."),
                userMessage(question)
        ));
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
        System.out.println(translation);

        assertThat(translation).isEqualTo("Hallo, wie geht es dir?");

        verify(chatLanguageModel).generate(asList(
                systemMessage("You are a professional translator into german"),
                userMessage("Translate the following text: Hello, how are you?")
        ));
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
        System.out.println(bulletPoints);

        assertThat(bulletPoints).hasSize(3);

        verify(chatLanguageModel).generate(asList(
                systemMessage("Summarize every message from user in 3 bullet points. Provide only bullet points."),
                userMessage(text + "\nYou must put every item on a separate line.")
        ));
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

        verify(chatLanguageModel).generate(singletonList(userMessage(message)));
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

        verify(chatLanguageModel).generate(singletonList(userMessage(message)));
        verify(moderationModel).moderate(singletonList(userMessage(message)));
    }


    interface ChatWithMemory {

        String SYSTEM_MESSAGE = "You are helpful assistant";
        String ANOTHER_SYSTEM_MESSAGE = "You are funny assistant";

        String chat(String userMessage);

        @SystemMessage(SYSTEM_MESSAGE)
        String chatWithSystemMessage(String userMessage);

        @SystemMessage(ANOTHER_SYSTEM_MESSAGE)
        String chatWithAnotherSystemMessage(String userMessage);
    }

    @Test
    void should_keep_chat_memory() {

        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String firstUserMessage = "Hello, my name is Klaus";
        String firstAiMessage = chatWithMemory.chat(firstUserMessage);

        verify(chatMemory).add(userMessage(firstUserMessage));
        verify(chatLanguageModel).generate(singletonList(userMessage(firstUserMessage)));
        verify(chatMemory).add(aiMessage(firstAiMessage));

        String secondUserMessage = "What is my name?";
        String secondAiMessage = chatWithMemory.chat(secondUserMessage);
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatMemory).add(userMessage(secondUserMessage));
        verify(chatLanguageModel).generate(asList(
                userMessage(firstUserMessage),
                aiMessage(firstAiMessage),
                userMessage(secondUserMessage)
        ));
        verify(chatMemory).add(aiMessage(secondAiMessage));
        verify(chatMemory, times(6)).messages();
    }

    @Test
    void should_keep_chat_memory_and_not_duplicate_system_message() {

        ChatWithMemory chatWithMemory = AiServices.builder(ChatWithMemory.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .build();

        String firstUserMessage = "Hello, my name is Klaus";
        String firstAiMessage = chatWithMemory.chatWithSystemMessage(firstUserMessage);

        verify(chatLanguageModel).generate(asList(
                systemMessage(SYSTEM_MESSAGE),
                userMessage(firstUserMessage)
        ));

        String secondUserMessage = "What is my name?";
        String secondAiMessage = chatWithMemory.chatWithSystemMessage(secondUserMessage);
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatLanguageModel).generate(asList(
                systemMessage(SYSTEM_MESSAGE),
                userMessage(firstUserMessage),
                aiMessage(firstAiMessage),
                userMessage(secondUserMessage)
        ));

        verify(chatMemory, times(2)).add(systemMessage(SYSTEM_MESSAGE));
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

        String firstUserMessage = "Hello, my name is Klaus";
        String firstAiMessage = chatWithMemory.chatWithSystemMessage(firstUserMessage);

        verify(chatLanguageModel).generate(asList(
                systemMessage(SYSTEM_MESSAGE),
                userMessage(firstUserMessage)
        ));


        String secondUserMessage = "What is my name?";
        String secondAiMessage = chatWithMemory.chatWithAnotherSystemMessage(secondUserMessage);
        assertThat(secondAiMessage).contains("Klaus");

        verify(chatLanguageModel).generate(asList(
                userMessage(firstUserMessage),
                aiMessage(firstAiMessage),
                systemMessage(ANOTHER_SYSTEM_MESSAGE),
                userMessage(secondUserMessage)
        ));

        verify(chatMemory).add(systemMessage(SYSTEM_MESSAGE));
        verify(chatMemory).add(userMessage(firstUserMessage));
        verify(chatMemory).add(aiMessage(firstAiMessage));
        verify(chatMemory).add(aiMessage(secondAiMessage));
        verify(chatMemory).add(systemMessage(ANOTHER_SYSTEM_MESSAGE));
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
        verify(chatLanguageModel).generate(singletonList(userMessage(firstMessageFromFirstUser)));

        String firstMessageFromSecondUser = "Hello, my name is Francine";
        String firstAiResponseToSecondUser = chatWithMemory.chat(secondMemoryId, firstMessageFromSecondUser);
        verify(chatLanguageModel).generate(singletonList(userMessage(firstMessageFromSecondUser)));

        String secondMessageFromFirstUser = "What is my name?";
        String secondAiResponseToFirstUser = chatWithMemory.chat(firstMemoryId, secondMessageFromFirstUser);
        assertThat(secondAiResponseToFirstUser).contains("Klaus");
        verify(chatLanguageModel).generate(asList(
                userMessage(firstMessageFromFirstUser),
                aiMessage(firstAiResponseToFirstUser),
                userMessage(secondMessageFromFirstUser)
        ));

        String secondMessageFromSecondUser = "What is my name?";
        String secondAiResponseToSecondUser = chatWithMemory.chat(secondMemoryId, secondMessageFromSecondUser);
        assertThat(secondAiResponseToSecondUser).contains("Francine");
        verify(chatLanguageModel).generate(asList(
                userMessage(firstMessageFromSecondUser),
                aiMessage(firstAiResponseToSecondUser),
                userMessage(secondMessageFromSecondUser)
        ));

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

        Response<AiMessage> chat(String userMessage);
    }

    static class Calculator {

        @Tool("calculates the square root of the provided number")
        double squareRoot(@P("number to operate on") double number) {
            return Math.sqrt(number);
        }
    }

    @Test
    void should_execute_a_tool_then_answer() {

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(72 + 110);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(21 + 28, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(4);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        assertThat(messages.get(3)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(3).text()).contains("6.97");


        verify(chatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(calculatorSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(calculatorSpecification)
        );
    }

    @Test
    void should_execute_multiple_tools_sequentially_then_answer() {

        Calculator calculator = spy(new Calculator());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97", "9.89");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(79 + 117 + 152);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(21 + 20 + 53, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(6);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest toolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(toolExecutionRequest.id()).isNotBlank();
        assertThat(toolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(toolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionResultMessage toolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(toolExecutionResultMessage.id()).isEqualTo(toolExecutionRequest.id());
        assertThat(toolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(toolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        AiMessage secondAiMessage = (AiMessage) messages.get(3);
        assertThat(secondAiMessage.text()).isNull();
        assertThat(secondAiMessage.toolExecutionRequests()).hasSize(1);
        ToolExecutionRequest secondToolExecutionRequest = secondAiMessage.toolExecutionRequests().get(0);
        assertThat(secondToolExecutionRequest.id()).isNotBlank();
        assertThat(secondToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 97866249624785}");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(4);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("9892737.215997653");

        assertThat(messages.get(5)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(5).text()).contains("6.97", "9.89");


        verify(chatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(calculatorSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2)),
                singletonList(calculatorSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2), messages.get(3), messages.get(4)),
                singletonList(calculatorSpecification)
        );
    }

    @Test
    void should_execute_multiple_tools_in_parallel_then_answer() {

        Calculator calculator = spy(new Calculator());

        ChatLanguageModel chatLanguageModel = spy(OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_3_5_TURBO_1106)
                .temperature(0.0)
                .logRequests(true)
                .logResponses(true)
                .build());

        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

        Assistant assistant = AiServices.builder(Assistant.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(chatMemory)
                .tools(calculator)
                .build();

        String userMessage = "What is the square root of 485906798473894056 and 97866249624785 in scientific notation?";

        Response<AiMessage> response = assistant.chat(userMessage);

        assertThat(response.content().text()).contains("6.97", "9.89");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(79 + 160);
        assertThat(tokenUsage.outputTokenCount()).isCloseTo(54 + 58, withPercentage(5));
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);


        verify(calculator).squareRoot(485906798473894056.0);
        verify(calculator).squareRoot(97866249624785.0);
        verifyNoMoreInteractions(calculator);


        List<ChatMessage> messages = chatMemory.messages();
        assertThat(messages).hasSize(5);

        assertThat(messages.get(0)).isInstanceOf(dev.langchain4j.data.message.UserMessage.class);
        assertThat(messages.get(0).text()).isEqualTo(userMessage);

        AiMessage aiMessage = (AiMessage) messages.get(1);
        assertThat(aiMessage.text()).isNull();
        assertThat(aiMessage.toolExecutionRequests()).hasSize(2);

        ToolExecutionRequest firstToolExecutionRequest = aiMessage.toolExecutionRequests().get(0);
        assertThat(firstToolExecutionRequest.id()).isNotBlank();
        assertThat(firstToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(firstToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 485906798473894056}");

        ToolExecutionRequest secondToolExecutionRequest = aiMessage.toolExecutionRequests().get(1);
        assertThat(secondToolExecutionRequest.id()).isNotBlank();
        assertThat(secondToolExecutionRequest.name()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionRequest.arguments())
                .isEqualToIgnoringWhitespace("{\"arg0\": 97866249624785}");

        ToolExecutionResultMessage firstToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(2);
        assertThat(firstToolExecutionResultMessage.id()).isEqualTo(firstToolExecutionRequest.id());
        assertThat(firstToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(firstToolExecutionResultMessage.text()).isEqualTo("6.97070153193991E8");

        ToolExecutionResultMessage secondToolExecutionResultMessage = (ToolExecutionResultMessage) messages.get(3);
        assertThat(secondToolExecutionResultMessage.id()).isEqualTo(secondToolExecutionRequest.id());
        assertThat(secondToolExecutionResultMessage.toolName()).isEqualTo("squareRoot");
        assertThat(secondToolExecutionResultMessage.text()).isEqualTo("9892737.215997653");

        assertThat(messages.get(4)).isInstanceOf(AiMessage.class);
        assertThat(messages.get(4).text()).contains("6.97", "9.89");


        verify(chatLanguageModel).generate(
                singletonList(messages.get(0)),
                singletonList(calculatorSpecification)
        );

        verify(chatLanguageModel).generate(
                asList(messages.get(0), messages.get(1), messages.get(2), messages.get(3)),
                singletonList(calculatorSpecification)
        );
    }
}
