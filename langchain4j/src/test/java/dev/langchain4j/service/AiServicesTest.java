package dev.langchain4j.service;

import dev.langchain4j.model.input.structured.StructuredPrompt;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.structured.Description;
import lombok.Builder;
import lombok.ToString;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static dev.langchain4j.service.AiServicesTest.Sentiment.POSITIVE;
import static java.time.Month.JULY;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class AiServicesTest {

    OpenAiChatModel chatModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();


    interface Humorist {

        String tellMeJoke();

        String tellMeJokeAbout(String topic);

        boolean isItFunny(String joke);
    }

    @Test
    void test_simple_instruction() {

        Humorist humorist = AiServices.create(Humorist.class, chatModel);

        String joke = humorist.tellMeJoke();

        assertThat(joke).isNotBlank();
        System.out.println(joke);
    }

    @Test
    void test_simple_instruction_with_single_argument() {

        Humorist humorist = AiServices.create(Humorist.class, chatModel);

        String joke = humorist.tellMeJokeAbout("AI");

        assertThat(joke).isNotBlank();
        System.out.println(joke);
    }

    @Test
    void test_simple_instruction_with_boolean_return_type() {

        Humorist humorist = AiServices.create(Humorist.class, chatModel);

        boolean funny = humorist.isItFunny("Why did the tomato turn red? Because it saw the salad dressing!");
        assertThat(funny).isTrue();
    }


    interface DateTimeExtractor {

        LocalDate extractDateFrom(String text);

        LocalTime extractTimeFrom(String text);

        LocalDateTime extractDateTimeFrom(String text);
    }

    @Test
    void test_extract_date() {
        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatModel);
        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDate date = dateTimeExtractor.extractDateFrom(text);

        assertThat(date).isEqualTo(LocalDate.of(1968, JULY, 4));
    }

    @Test
    void test_extract_time() {
        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatModel);
        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalTime time = dateTimeExtractor.extractTimeFrom(text);

        assertThat(time).isEqualTo(LocalTime.of(23, 45, 0));
    }

    @Test
    void test_extract_date_time() {
        DateTimeExtractor dateTimeExtractor = AiServices.create(DateTimeExtractor.class, chatModel);
        String text = "The tranquility pervaded the evening of 1968, just fifteen minutes shy of midnight, following the celebrations of Independence Day.";

        LocalDateTime dateTime = dateTimeExtractor.extractDateTimeFrom(text);

        assertThat(dateTime).isEqualTo(LocalDateTime.of(1968, JULY, 4, 23, 45, 0));
    }


    enum Sentiment {
        POSITIVE, NEUTRAL, NEGATIVE;
    }

    interface SentimentAnalyzer {

        Sentiment analyzeSentimentOf(String text);
    }

    @Test
    void test_extract_enum() {
        SentimentAnalyzer sentimentAnalyzer = AiServices.create(SentimentAnalyzer.class, chatModel);
        String customerReview = "This LaptopPro X15 is wicked fast and that 4K screen is a dream.";

        Sentiment sentiment = sentimentAnalyzer.analyzeSentimentOf(customerReview);

        assertThat(sentiment).isEqualTo(POSITIVE);
    }


    static class Person {

        private String firstName;
        private String lastName;
        private LocalDate birthDate;
    }

    interface PersonExtractor {

        Person extractPersonFrom(String text);
    }

    @Test
    void test_extract_custom_object() {
        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, chatModel);
        String text = "In 1968, amidst the fading echoes of Independence Day, "
                + "a child named John arrived under the calm evening sky. "
                + "This newborn, bearing the surname Doe, marked the start of a new journey.";

        Person person = personExtractor.extractPersonFrom(text);

        assertThat(person.firstName).isEqualTo("John");
        assertThat(person.lastName).isEqualTo("Doe");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1968, JULY, 4));
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

        Recipe createRecipeFrom(String... ingredients);

        Recipe createRecipeFrom(CreateRecipePrompt prompt);
    }

    @Test
    void test_create_recipe_from_list_of_ingredients() {
        Chef chef = AiServices.create(Chef.class, chatModel);

        Recipe recipe = chef.createRecipeFrom("cucumber", "tomato", "feta", "onion", "olives");

        assertThat(recipe.title).isNotBlank();
        assertThat(recipe.description).isNotBlank();
        assertThat(recipe.steps).isNotEmpty();
        assertThat(recipe.preparationTimeMinutes).isPositive();
        System.out.println(recipe);
    }


    @Builder
    @StructuredPrompt("Create a recipe of a {{dish}} that can be prepared using only {{ingredients}}")
    static class CreateRecipePrompt {

        private String dish;
        private List<String> ingredients;
    }

    @Test
    void test_create_recipe_using_structured_prompt() {
        Chef chef = AiServices.create(Chef.class, chatModel);
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
    }


    interface ProfessionalChef {

        @SystemMessage("You are a professional chef. You are friendly, polite and concise.")
        String answer(String question);
    }

    @Test
    void test_with_system_message() {
        ProfessionalChef chef = AiServices.create(ProfessionalChef.class, chatModel);
        String question = "How long should I grill chicken?";

        String answer = chef.answer(question);

        assertThat(answer).isNotBlank();
        System.out.println(answer);
    }


    interface TextUtils {

        @SystemMessage("You are a professional translator into {{language}}")
        @UserMessage("Translate the following text: {{text}}")
        String translate(@V("text") String text, @V("language") String language);

        @SystemMessage("Summarize every message from user in {{n}} bullet points. Provide only bullet points.")
        List<String> summarize(@UserMessage String text, @V("n") int n);
    }

    @Test
    void test_with_system_and_user_messages() {
        TextUtils utils = AiServices.create(TextUtils.class, chatModel);
        String text = "Hello, how are you?";

        String translation = utils.translate(text, "german");

        assertThat(translation).isEqualTo("Hallo, wie geht es dir?");
    }

    @Test
    void test_with_system_message_and_user_message_as_argument() {
        TextUtils utils = AiServices.create(TextUtils.class, chatModel);
        String text = "AI, or artificial intelligence, is a branch of computer science that aims to create " +
                "machines that mimic human intelligence. This can range from simple tasks such as recognizing " +
                "patterns or speech to more complex tasks like making decisions or predictions.";

        List<String> bulletPoints = utils.summarize(text, 3);

        assertThat(bulletPoints).hasSize(3);
        System.out.println(bulletPoints);
    }
}
