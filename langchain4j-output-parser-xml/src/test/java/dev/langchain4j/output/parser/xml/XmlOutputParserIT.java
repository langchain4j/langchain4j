package dev.langchain4j.output.parser.xml;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class XmlOutputParserIT {

    ChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    StreamingChatModel streamingModel = OpenAiStreamingChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    static class Person {
        public String name;
        public int age;
        public String occupation;

        public Person() {}
    }

    static class Book {
        public String title;
        public String author;
        public int year;

        public Book() {}
    }

    static class Address {
        public String street;
        public String city;
        public String country;

        public Address() {}
    }

    static class Company {
        public String name;
        public String industry;
        public Address headquarters;

        public Company() {}
    }

    @Test
    void should_parse_simple_object_from_llm_response() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);
        String prompt =
                """
                %s

                Extract the person information from this text:
                John Smith is a 35-year-old software engineer.
                """
                        .formatted(parser.formatInstructions());

        String llmResponse = model.chat(prompt);
        Person person = parser.parse(llmResponse);

        assertThat(person.name).containsIgnoringCase("John");
        assertThat(person.age).isEqualTo(35);
        assertThat(person.occupation).containsIgnoringCase("engineer");
    }

    @Test
    void should_parse_book_information() {
        XmlOutputParser<Book> parser = new XmlOutputParser<>(Book.class);
        String prompt =
                """
                %s

                Extract the book information:
                The novel '1984' was written by George Orwell and published in 1949.
                """
                        .formatted(parser.formatInstructions());

        String llmResponse = model.chat(prompt);
        Book book = parser.parse(llmResponse);

        assertThat(book.title).contains("1984");
        assertThat(book.author).containsIgnoringCase("Orwell");
        assertThat(book.year).isEqualTo(1949);
    }

    @Test
    void should_parse_nested_objects() {
        XmlOutputParser<Company> parser = new XmlOutputParser<>(Company.class);
        String prompt =
                """
                %s

                Extract company information:
                Acme Corp is a technology company headquartered at 123 Main Street, San Francisco, USA.
                """
                        .formatted(parser.formatInstructions());

        String llmResponse = model.chat(prompt);
        Company company = parser.parse(llmResponse);

        assertThat(company.name).containsIgnoringCase("Acme");
        assertThat(company.industry).containsIgnoringCase("tech");
        assertThat(company.headquarters).isNotNull();
        assertThat(company.headquarters.city).containsIgnoringCase("San Francisco");
        assertThat(company.headquarters.country).containsIgnoringCase("US");
    }

    @Test
    void should_handle_llm_response_with_explanation_text() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);
        String prompt =
                """
                %s

                Briefly explain what you're doing, then provide the XML.
                Extract: Maria Garcia is a 42-year-old architect.
                """
                        .formatted(parser.formatInstructions());

        String llmResponse = model.chat(prompt);
        Person person = parser.parse(llmResponse);

        assertThat(person.name).containsIgnoringCase("Maria");
        assertThat(person.age).isEqualTo(42);
        assertThat(person.occupation).containsIgnoringCase("architect");
    }

    @Test
    void should_handle_missing_optional_fields() {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);
        String prompt =
                """
                %s

                Extract person info. Only name and age are mentioned:
                Bob is 25 years old.
                """
                        .formatted(parser.formatInstructions());

        String llmResponse = model.chat(prompt);
        Person person = parser.parse(llmResponse);

        assertThat(person.name).containsIgnoringCase("Bob");
        assertThat(person.age).isEqualTo(25);
    }

    @Test
    void should_work_with_format_instructions_guiding_llm() {
        XmlOutputParser<Address> parser = new XmlOutputParser<>(Address.class);

        String instructions = parser.formatInstructions();
        assertThat(instructions)
                .contains("XML")
                .contains("street")
                .contains("city")
                .contains("country");

        String prompt =
                """
                %s

                Extract the address:
                221B Baker Street, London, United Kingdom
                """
                        .formatted(instructions);

        String llmResponse = model.chat(prompt);
        Address address = parser.parse(llmResponse);

        assertThat(address.street).containsIgnoringCase("Baker");
        assertThat(address.city).containsIgnoringCase("London");
        assertThat(address.country).containsIgnoringCase("Kingdom");
    }

    @Test
    void should_parse_from_streaming_model_response() throws Exception {
        XmlOutputParser<Person> parser = new XmlOutputParser<>(Person.class);
        String prompt =
                """
                %s

                Extract the person information from this text:
                Emma Watson is a 33-year-old actress.
                """
                        .formatted(parser.formatInstructions());

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        StringBuilder streamedTokens = new StringBuilder();

        streamingModel.chat(prompt, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                streamedTokens.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(30, SECONDS);
        Person person = parser.parse(response.aiMessage().text());

        assertThat(person.name).containsIgnoringCase("Emma");
        assertThat(person.age).isEqualTo(33);
        assertThat(person.occupation).containsIgnoringCase("actress");
        assertThat(streamedTokens).hasToString(response.aiMessage().text());
    }
}
