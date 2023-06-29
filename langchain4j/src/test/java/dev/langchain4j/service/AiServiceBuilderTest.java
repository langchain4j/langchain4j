package dev.langchain4j.service;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.moderation.ModerationModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.output.structured.Description;
import lombok.Builder;
import lombok.ToString;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.service.AiServiceBuilderTest.Sentiment.POSITIVE;
import static java.time.Month.JULY;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AiServiceBuilderTest {

    @Spy
    ChatLanguageModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @Spy
    ModerationModel moderationModel = OpenAiModerationModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .build();


    interface Humorist {

        @UserMessage("Tell me a joke about {{it}}")
        String joke(String topic);
    }

    @Test
    void test_simple_instruction_with_single_argument() {

        Humorist humorist = AiServiceBuilder.forClass(Humorist.class)
                .chatLanguageModel(chatModel)
                .build();

        String joke = humorist.joke("AI");

        assertThat(joke).isNotBlank();
        System.out.println(joke);

        verify(chatModel).sendMessages(singletonList(userMessage("Tell me a joke about AI")));
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

        DateTimeExtractor dateTimeExtractor = AiServiceBuilder.forClass(DateTimeExtractor.class)
                .chatLanguageModel(chatModel)
                .build();

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDate date = dateTimeExtractor.extractDateFrom(text);

        assertThat(date).isEqualTo(LocalDate.of(1968, JULY, 4));

        verify(chatModel).sendMessages(singletonList(userMessage(
                "Extract date from " + text + "\n" +
                        "You must answer strictly in the following format: 2023-12-31")));
    }

    @Test
    void test_extract_time() {

        DateTimeExtractor dateTimeExtractor = AiServiceBuilder.forClass(DateTimeExtractor.class)
                .chatLanguageModel(chatModel)
                .build();

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalTime time = dateTimeExtractor.extractTimeFrom(text);

        assertThat(time).isEqualTo(LocalTime.of(23, 45, 0));

        verify(chatModel).sendMessages(singletonList(userMessage(
                "Extract time from " + text + "\n" +
                        "You must answer strictly in the following format: 23:59:59")));
    }

    @Test
    void test_extract_date_time() {

        DateTimeExtractor dateTimeExtractor = AiServiceBuilder.forClass(DateTimeExtractor.class)
                .chatLanguageModel(chatModel)
                .build();

        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDateTime dateTime = dateTimeExtractor.extractDateTimeFrom(text);

        assertThat(dateTime).isEqualTo(LocalDateTime.of(1968, JULY, 4, 23, 45, 0));

        verify(chatModel).sendMessages(singletonList(userMessage(
                "Extract date and time from " + text + "\n" +
                        "You must answer strictly in the following format: 2023-12-31T23:59:59")));
    }


    enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE;
    }

    interface SentimentAnalyzer {

        @UserMessage("Analyze sentiment of {{it}}")
        Sentiment analyzeSentimentOf(String text);
    }

    @Test
    void test_extract_enum() {

        SentimentAnalyzer sentimentAnalyzer = AiServiceBuilder.forClass(SentimentAnalyzer.class)
                .chatLanguageModel(chatModel)
                .build();

        String customerReview = "This LaptopPro X15 is wicked fast and that 4K screen is a dream.";

        Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf(customerReview);

        assertThat(sentiment).isEqualTo(POSITIVE);

        verify(chatModel).sendMessages(singletonList(userMessage(
                "Analyze sentiment of " + customerReview + "\n" +
                        "You must answer strictly in the following format: one of [POSITIVE, NEUTRAL, NEGATIVE]")));
    }


    static class Person {

        private String firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    interface PersonExtractor {

        @UserMessage("Extract information about person from {{it}}")
        Person extractPersonFrom(String text);
    }

    @Test
    void test_extract_custom_POJO() {

        PersonExtractor personExtractor = AiServiceBuilder.forClass(PersonExtractor.class)
                .chatLanguageModel(chatModel)
                .build();

        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey.";

        Person person = personExtractor.extractPersonFrom(text);

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));

        verify(chatModel).sendMessages(singletonList(userMessage(
                "Extract information about person from " + text + "\n" +
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
    }

    @Test
    void test_create_recipe_from_list_of_ingredients() {

        Chef chef = AiServiceBuilder.forClass(Chef.class)
                .chatLanguageModel(chatModel)
                .build();

        Recipe recipe = chef.createRecipeFrom("cucumber", "tomato", "feta", "onion", "olives");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();
        System.out.println(recipe);

        verify(chatModel).sendMessages(singletonList(userMessage(
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

        Chef chef = AiServiceBuilder.forClass(Chef.class)
                .chatLanguageModel(chatModel)
                .build();

        CreateRecipePrompt prompt = CreateRecipePrompt
                .builder()
                .dish("salad")
                .ingredients(Arrays.asList("cucumber", "tomato", "feta", "onion", "olives"))
                .build();

        Recipe recipe = chef.createRecipeFrom(prompt);

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();
        System.out.println(recipe);

        verify(chatModel).sendMessages(singletonList(userMessage(
                "Create a recipe of a salad that can be prepared using only [cucumber, tomato, feta, onion, olives]\n" +
                        "You must answer strictly in the following JSON format: {\n" +
                        "\"title\": (type: string),\n" +
                        "\"description\": (type: string),\n" +
                        "\"steps\": (each step should be described in 4 words, steps should rhyme; type: array of string),\n" +
                        "\"preparationTimeMinutes\": (type: integer),\n" +
                        "}")));
    }


    interface ProfessionalChef {

        @SystemMessage("You are a professional chef. You are friendly, polite and concise.")
        String answer(String question);
    }

    @Test
    void test_with_system_message() {

        ProfessionalChef chef = AiServiceBuilder.forClass(ProfessionalChef.class)
                .chatLanguageModel(chatModel)
                .build();

        String question = "How long should I grill chicken?";

        String answer = chef.answer(question);

        assertThat(answer).isNotBlank();
        System.out.println(answer);

        verify(chatModel).sendMessages(asList(
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

        Translator translator = AiServiceBuilder.forClass(Translator.class)
                .chatLanguageModel(chatModel)
                .build();

        String text = "Hello, how are you?";

        String translation = translator.translate(text, "german");

        assertThat(translation).isEqualTo("Hallo, wie geht es dir?");

        verify(chatModel).sendMessages(asList(
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

        Summarizer summarizer = AiServiceBuilder.forClass(Summarizer.class)
                .chatLanguageModel(chatModel)
                .build();

        String text = "AI, or artificial intelligence, is a branch of computer science that aims to create " +
                "machines that mimic human intelligence. This can range from simple tasks such as recognizing " +
                "patterns or speech to more complex tasks like making decisions or predictions.";

        List<String> bulletPoints = summarizer.summarize(text, 3);

        assertThat(bulletPoints).hasSize(3);
        System.out.println(bulletPoints);

        verify(chatModel).sendMessages(asList(
                systemMessage("Summarize every message from user in 3 bullet points. Provide only bullet points."),
                userMessage(text + "\nYou must put every item on a separate line.")
        ));
    }


    interface Chat {

        @Moderate
        String chat(String message);
    }

    @Test
    void should_throw_when_text_is_flagged() { // TODO measure performance

        Chat chat = AiServiceBuilder.forClass(Chat.class)
                .chatLanguageModel(chatModel)
                .moderationModel(moderationModel)
                .build();

        assertThatThrownBy(() -> chat.chat("I will kill them!"))
                .isExactlyInstanceOf(ModerationException.class)
                .hasMessage("Text \"I will kill them!\" violates content policy");

        verify(moderationModel).moderate(asList(userMessage("I will kill them!")));
    }

    @Test
    void should_not_throw_when_text_is_not_flagged() {

        Chat chat = AiServiceBuilder.forClass(Chat.class)
                .chatLanguageModel(chatModel)
                .moderationModel(moderationModel)
                .build();

        String response = chat.chat("I will hug them!");

        assertThat(response).isNotBlank();
    }

    interface ChatWithHistory {

        // no annotations, single argument -> argument becomes a user message
        // multiple arguments -> ALL arguments should be annotated either @V or @UserMessage (can be only one)
        // multiple methods with @SystemMessage? if method with different system message is invoked, new system message will be put to the end of the memory
        // TODO test every use case here in this list!!!
        // TODO simplify for users with "-parameters"


        String chat(String message);
        // [user] {{message}}

        String chat(Object structuredPrompt);
        // [user] {{structuredPrompt}}

        @UserMessage("Tell me a joke about {{it}}")
        String joke(String topic);
        // [user] Tell me a joke about {{topic}}

        @UserMessage("Tell me a joke about {{topic}}")
        String joke2(@V("topic") String topic);
        // [user] Tell me a joke about {{topic}}

        @SystemMessage("Translate each message from user into Italian")
        String translate(String text);
        // [system] Translate each message from user into Italian
        // [user] {{text}}

        @SystemMessage("Translate each message from user into {{language}}")
        String translate2(@UserMessage String text, @V("language") String language);
        // [system] Translate each message from user into {{language}}
        // [user] {{text}}

        @SystemMessage("You are a professional translator")
        @UserMessage("Translate into {{language}}: {{text}}")
        String translate3(@V("text") String text, @V("language") String language);
        // [system] You are a professional translator
        // [user] Translate into {{language}}: {{text}}

        @SystemMessage("You are a professional assistant")
        String assist(Object structuredPrompt);
        // [system] You are a professional assistant
        // [user] {{structuredPrompt}}
    }

    @Test
    void should_keep_chat_history() {

        ChatWithHistory chatWithHistory = AiServiceBuilder.forClass(ChatWithHistory.class)
                .chatLanguageModel(chatModel)
                .chatMemory(MessageWindowChatMemory.builder()
                        .capacityInMessages(10)
//                        .systemMessage("") // TODO needed??? will be default in case method doesnt have one?
                        .build())
                .build();

        chatWithHistory.chat("Hello, my name is Klaus.");

        String answer = chatWithHistory.chat("What is my name?");

        assertThat(answer).contains("Klaus");
    }

    private static List<ChatMessage> asList(ChatMessage... messages) {
        return new ArrayList<>(Arrays.asList(messages));
    }
}
