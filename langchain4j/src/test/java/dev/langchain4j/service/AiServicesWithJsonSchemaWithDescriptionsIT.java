package dev.langchain4j.service;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.structured.Description;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.service.AiServicesIT.verifyNoMoreInteractionsFor;
import static dev.langchain4j.service.AiServicesWithJsonSchemaWithDescriptionsIT.PersonExtractor3.MaritalStatus.SINGLE;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class AiServicesWithJsonSchemaWithDescriptionsIT {

    @Spy
    ChatModel model = OpenAiChatModel.builder()
            .baseUrl(System.getenv("OPENAI_BASE_URL"))
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
            .modelName(GPT_4_O_MINI)
            .supportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA)
            .strictJsonSchema(true)
            .temperature(0.0)
            .logRequests(true)
            .logResponses(true)
            .build();

    @AfterEach
    void afterEach() {
        verifyNoMoreInteractionsFor(model);
    }


    interface PersonExtractor1 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("an age")
            int age;

            @Description("a height")
            Double height;

            @Description("married or not")
            boolean married;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_primitives() {

        // given
        PersonExtractor1 personExtractor = AiServices.create(PersonExtractor1.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single";

        // when
        PersonExtractor1.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.age).isEqualTo(37);
        assertThat(person.height).isEqualTo(1.78);
        assertThat(person.married).isFalse();

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("age", JsonIntegerSchema.builder()
                                                    .description("an age")
                                                    .build());
                                            put("height", JsonNumberSchema.builder()
                                                    .description("a height")
                                                    .build());
                                            put("married", JsonBooleanSchema.builder()
                                                    .description("married or not")
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor2 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("an address override")
            Address address;
        }

        @Description("an address")
        class Address {

            @Description("a city")
            String city;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_nested_pojo() {

        // given
        PersonExtractor2 personExtractor = AiServices.create(PersonExtractor2.class, model);

        String text = "Klaus lives in Langley Falls";

        // when
        PersonExtractor2.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.address.city).isEqualTo("Langley Falls");

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("address", JsonObjectSchema.builder()
                                                    .description("an address override")
                                                    .addProperties(new LinkedHashMap<>() {{
                                                        put("city", JsonStringSchema.builder()
                                                                .description("a city")
                                                                .build());
                                                    }})
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor3 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("marital status override")
            MaritalStatus maritalStatus;
        }

        @Description("marital status")
        enum MaritalStatus {

            SINGLE, MARRIED
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_enum() {

        // given
        PersonExtractor3 personExtractor = AiServices.create(PersonExtractor3.class, model);

        String text = "Klaus is single";

        // when
        PersonExtractor3.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.maritalStatus).isEqualTo(SINGLE);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("maritalStatus", JsonEnumSchema.builder()
                                                    .enumValues("SINGLE", "MARRIED")
                                                    .description("marital status override")
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor4 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("favourite colors")
            String[] favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_array_of_primitives() {

        // given
        PersonExtractor4 personExtractor = AiServices.create(PersonExtractor4.class, model);

        String text = "Klaus likes orange and green";

        // when
        PersonExtractor4.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.favouriteColors).containsExactly("orange", "green");

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("favouriteColors", JsonArraySchema.builder()
                                                    .items(new JsonStringSchema())
                                                    .description("favourite colors")
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor5 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("favourite colors")
            List<String> favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_list_of_primitives() {

        // given
        PersonExtractor5 personExtractor = AiServices.create(PersonExtractor5.class, model);

        String text = "Klaus likes orange and green";

        // when
        PersonExtractor5.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.favouriteColors).containsExactly("orange", "green");

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("favouriteColors", JsonArraySchema.builder()
                                                    .items(new JsonStringSchema())
                                                    .description("favourite colors")
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor6 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("favourite colors")
            Set<String> favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_set_of_primitives() {

        // given
        PersonExtractor6 personExtractor = AiServices.create(PersonExtractor6.class, model);

        String text = "Klaus likes orange and green";

        // when
        PersonExtractor6.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.favouriteColors).containsExactly("orange", "green");

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("favouriteColors", JsonArraySchema.builder()
                                                    .items(new JsonStringSchema())
                                                    .description("favourite colors")
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor7 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("pets of a person")
            Pet[] pets;
        }

        @Description("a pet")
        class Pet {

            @Description("a name of a pet")
            String name;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_array_of_pojos() {

        // given
        PersonExtractor7 personExtractor = AiServices.create(PersonExtractor7.class, model);

        String text = "Klaus has 2 pets: Peanut and Muffin";

        // when
        PersonExtractor7.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.pets).hasSize(2);
        assertThat(person.pets[0].name).isEqualTo("Peanut");
        assertThat(person.pets[1].name).isEqualTo("Muffin");

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .description("a pet")
                                                            .addProperties(new LinkedHashMap<>() {{
                                                                put("name", JsonStringSchema.builder()
                                                                        .description("a name of a pet")
                                                                        .build());
                                                            }})
                                                            .build())
                                                    .description("pets of a person")
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor8 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("pets of a person")
            List<Pet> pets;
        }

        @Description("a pet")
        class Pet {

            @Description("a name of a pet")
            String name;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_list_of_pojos() {

        // given
        PersonExtractor8 personExtractor = AiServices.create(PersonExtractor8.class, model);

        String text = "Klaus has 2 pets: Peanut and Muffin";

        // when
        PersonExtractor8.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.pets).hasSize(2);
        assertThat(person.pets.get(0).name).isEqualTo("Peanut");
        assertThat(person.pets.get(1).name).isEqualTo("Muffin");

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .description("a pet")
                                                            .addProperties(new LinkedHashMap<>() {{
                                                                put("name", JsonStringSchema.builder()
                                                                        .description("a name of a pet")
                                                                        .build());
                                                            }})
                                                            .build())
                                                    .description("pets of a person")
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    @Test
    void should_extract_pojo_with_set_of_pojos() {

        @Description("a pet")
        record Pet(@Description("a name of a pet") String name) {
        }

        @Description("a person")
        record Person(@Description("a name") String name, @Description("pets of a person") Set<Pet> pets) {
        }

        interface PersonExtractor {

            Person extractPersonFrom(String text);
        }

        // given
        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, model);

        String text = "Klaus has 2 pets: Peanut and Muffin";

        // when
        Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person).isEqualTo(new Person("Klaus", Set.of(
                new Pet("Peanut"),
                new Pet("Muffin")
        )));

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .description("a pet")
                                                            .addProperties(new LinkedHashMap<>() {{
                                                                put("name", JsonStringSchema.builder()
                                                                        .description("a name of a pet")
                                                                        .build());
                                                            }})
                                                            .build())
                                                    .description("pets of a person")
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor10 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("groups")
            Group[] groups;
        }

        @Description("a group")
        enum Group {

            A, B, C
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_array_of_enums() {

        // given
        PersonExtractor10 personExtractor = AiServices.create(PersonExtractor10.class, model);

        String text = "Klaus is assigned to groups A and C";

        // when
        PersonExtractor10.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.groups).containsExactlyInAnyOrder(PersonExtractor10.Group.A, PersonExtractor10.Group.C);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("groups", JsonArraySchema.builder()
                                                    .description("groups")
                                                    .items(JsonEnumSchema.builder()
                                                            .description("a group")
                                                            .enumValues("A", "B", "C")
                                                            .build())
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor11 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("groups")
            List<Group> groups;
        }

        @Description("a group")
        enum Group {

            A, B, C
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_list_of_enums() {

        // given
        PersonExtractor11 personExtractor = AiServices.create(PersonExtractor11.class, model);

        String text = "Klaus is assigned to groups A and C";

        // when
        PersonExtractor11.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.groups).containsExactlyInAnyOrder(PersonExtractor11.Group.A, PersonExtractor11.Group.C);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("groups", JsonArraySchema.builder()
                                                    .description("groups")
                                                    .items(JsonEnumSchema.builder()
                                                            .description("a group")
                                                            .enumValues("A", "B", "C")
                                                            .build())
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor12 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("groups")
            Set<Group> groups;
        }

        @Description("a group")
        enum Group {

            A, B, C
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_set_of_enums() {

        // given
        PersonExtractor12 personExtractor = AiServices.create(PersonExtractor12.class, model);

        String text = "Klaus is assigned to groups A and C";

        // when
        PersonExtractor12.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.groups).containsExactlyInAnyOrder(PersonExtractor12.Group.A, PersonExtractor12.Group.C);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("groups", JsonArraySchema.builder()
                                                    .description("groups")
                                                    .items(JsonEnumSchema.builder()
                                                            .description("a group")
                                                            .enumValues("A", "B", "C")
                                                            .build())
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }


    interface PersonExtractor13 {

        @Description("a person")
        class Person {

            @Description("a name")
            String name;

            @Description("a birth date")
            LocalDate birthDate;

            @Description("a birth time")
            LocalTime birthTime;

            @Description("a birth date and time")
            LocalDateTime birthDateTime;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_local_date_time_fields() {

        // given
        PersonExtractor13 personExtractor = AiServices.create(PersonExtractor13.class, model);

        String text = "Klaus was born at 14:43:26 on 12th of August 1976";

        // when
        PersonExtractor13.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1976, 8, 12));
        assertThat(person.birthTime).isEqualTo(LocalTime.of(14, 43, 26));
        assertThat(person.birthDateTime)
                .isEqualTo(LocalDateTime.of(1976, 8, 12, 14, 43, 26));

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .description("a person")
                                        .addProperties(new LinkedHashMap<>() {{
                                            put("name", JsonStringSchema.builder()
                                                    .description("a name")
                                                    .build());
                                            put("birthDate", JsonObjectSchema.builder()
                                                    .description("a birth date")
                                                    .addProperties(new LinkedHashMap<>() {{
                                                        put("year", new JsonIntegerSchema());
                                                        put("month", new JsonIntegerSchema());
                                                        put("day", new JsonIntegerSchema());
                                                    }})
                                                    .build());
                                            put("birthTime", JsonObjectSchema.builder()
                                                    .description("a birth time")
                                                    .addProperties(new LinkedHashMap<>() {{
                                                        put("hour", new JsonIntegerSchema());
                                                        put("minute", new JsonIntegerSchema());
                                                        put("second", new JsonIntegerSchema());
                                                        put("nano", new JsonIntegerSchema());
                                                    }})
                                                    .build());
                                            put("birthDateTime", JsonObjectSchema.builder()
                                                    .description("a birth date and time")
                                                    .addProperties(new LinkedHashMap<>() {{
                                                        put("date", JsonObjectSchema.builder()
                                                                .addProperties(new LinkedHashMap<>() {{
                                                                    put("year", new JsonIntegerSchema());
                                                                    put("month", new JsonIntegerSchema());
                                                                    put("day", new JsonIntegerSchema());
                                                                }})
                                                                .build());
                                                        put("time", JsonObjectSchema.builder()
                                                                .addProperties(new LinkedHashMap<>() {{
                                                                    put("hour", new JsonIntegerSchema());
                                                                    put("minute", new JsonIntegerSchema());
                                                                    put("second", new JsonIntegerSchema());
                                                                    put("nano", new JsonIntegerSchema());
                                                                }})
                                                                .build());
                                                    }})
                                                    .build());
                                        }})
                                        .build())
                                .build())
                        .build())
                .build());
    }
}
