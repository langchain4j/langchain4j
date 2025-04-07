package dev.langchain4j.service.common;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.service.common.AbstractAiServiceWithJsonSchemaIT.PersonExtractor3.MaritalStatus.SINGLE;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@TestInstance(PER_CLASS)
public abstract class AbstractAiServiceWithJsonSchemaIT {
    // TODO test the same for streaming models

    protected abstract List<ChatLanguageModel> models();


    interface PersonExtractor1 {

        class Person {

            String name;
            int age;
            Double height;
            boolean married;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_primitives(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor1 personExtractor = AiServices.create(PersonExtractor1.class, model);

        String text = "Extract the person's information from the following text: " +
                "Klaus is 37 years old, 1.78m height and single";

        // when
        PersonExtractor1.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.age).isEqualTo(37);
        assertThat(person.height).isEqualTo(1.78);
        assertThat(person.married).isFalse();

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addIntegerProperty("age")
                                                .addNumberProperty("height")
                                                .addBooleanProperty("married")
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_missing_data(ChatLanguageModel model) {

        interface PersonExtractor {

            enum MaritalStatus {
                SINGLE,
                MARRIED
            }

            record Address(String street) {
            }

            class Person {

                String name;
                int age;
                Double height;
                boolean married;
                Map<String, Object> map;
                List<String> list;
                String[] array;
                Address address;
                MaritalStatus maritalStatus;
                LocalDate localDate;
            }

            Person extractPersonFrom(String text);
        }

        // given
        ChatLanguageModel spyModel = spy(model);

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, spyModel);

        String text = "Extract the person's information from the following text. Do not include missing fields! " +
                "Text: 'Klaus'";

        // when
        PersonExtractor.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.age).isEqualTo(0);
        assertThat(person.height).isEqualTo(null);
        assertThat(person.married).isFalse();
        assertThat(person.map).isNullOrEmpty();
        assertThat(person.list).isNullOrEmpty();
        assertThat(person.array).isNullOrEmpty();
        assertThat(person.address).isNull();
        if (!isStrictJsonSchemaEnabled(model)) {
            // LLMs in strict JSON schema mode return enums for some reason, even if it is optional and no data available
            assertThat(person.maritalStatus).isNull();
        }
        assertThat(person.localDate).isNull();

        verify(spyModel)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addIntegerProperty("age")
                                                .addNumberProperty("height")
                                                .addBooleanProperty("married")
                                                .addProperty("map", JsonObjectSchema.builder()
                                                        .build())
                                                .addProperty("list", JsonArraySchema.builder()
                                                        .items(new JsonStringSchema())
                                                        .build())
                                                .addProperty("array", JsonArraySchema.builder()
                                                        .items(new JsonStringSchema())
                                                        .build())
                                                .addProperty("address", JsonObjectSchema.builder()
                                                        .addStringProperty("street")
                                                        .build())
                                                .addEnumProperty("maritalStatus", List.of("SINGLE", "MARRIED"))
                                                .addProperty("localDate", JsonObjectSchema.builder()
                                                        .addIntegerProperty("year")
                                                        .addIntegerProperty("month")
                                                        .addIntegerProperty("day")
                                                        .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(spyModel).supportedCapabilities();
    }

    protected boolean isStrictJsonSchemaEnabled(ChatLanguageModel model) {
        return false;
    }

    interface PersonExtractor2 {

        class Person {

            String name;
            Address shippingAddress;
            Address billingAddress;
        }

        class Address {

            String city;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_nested_pojo(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor2 personExtractor = AiServices.create(PersonExtractor2.class, model);

        String text = "Extract the person's information from the following text. " +
                "Fill in all the fields where the information is available! " +
                "Text: 'Klaus wants a delivery to Langley Falls, but billing address should be New York'";

        // when
        PersonExtractor2.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.shippingAddress.city).isEqualTo("Langley Falls");
        assertThat(person.billingAddress.city).isEqualTo("New York");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "shippingAddress",
                                                        JsonObjectSchema.builder()
                                                                .addStringProperty("city")
                                                                .build())
                                                .addProperty(
                                                        "billingAddress",
                                                        JsonObjectSchema.builder()
                                                                .addStringProperty("city")
                                                                .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor3 {

        class Person {

            String name;
            MaritalStatus maritalStatus;
        }

        enum MaritalStatus {
            SINGLE,
            MARRIED
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_enum(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor3 personExtractor = AiServices.create(PersonExtractor3.class, model);

        String text = "Extract the person's information from the following text: Klaus is single";

        // when
        PersonExtractor3.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.maritalStatus).isEqualTo(SINGLE);

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addEnumProperty("maritalStatus", List.of("SINGLE", "MARRIED"))
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor4 {

        class Person {

            String name;
            String[] favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_array_of_primitives(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor4 personExtractor = AiServices.create(PersonExtractor4.class, model);

        String text = "Extract the person's information from the following text: Klaus likes orange and green";

        // when
        PersonExtractor4.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.favouriteColors).containsExactly("orange", "green");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "favouriteColors",
                                                        JsonArraySchema.builder()
                                                                .items(new JsonStringSchema())
                                                                .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor5 {

        class Person {

            String name;
            List<String> favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_list_of_primitives(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor5 personExtractor = AiServices.create(PersonExtractor5.class, model);

        String text = "Extract the person's information from the following text: Klaus likes orange and green";

        // when
        PersonExtractor5.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.favouriteColors).containsExactly("orange", "green");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "favouriteColors",
                                                        JsonArraySchema.builder()
                                                                .items(new JsonStringSchema())
                                                                .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor6 {

        class Person {

            String name;
            Set<String> favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_set_of_primitives(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor6 personExtractor = AiServices.create(PersonExtractor6.class, model);

        String text = "Extract the person's information from the following text: Klaus likes orange and green";

        // when
        PersonExtractor6.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.favouriteColors).containsExactly("orange", "green");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "favouriteColors",
                                                        JsonArraySchema.builder()
                                                                .items(new JsonStringSchema())
                                                                .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor7 {

        class Person {

            String name;
            Pet[] pets;
        }

        class Pet {

            String name;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_array_of_pojos(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor7 personExtractor = AiServices.create(PersonExtractor7.class, model);

        String text = "Extract the person's information from the following text: Klaus has 2 pets: Peanut and Muffin";

        // when
        PersonExtractor7.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.pets).hasSize(2);
        assertThat(person.pets[0].name).isEqualTo("Peanut");
        assertThat(person.pets[1].name).isEqualTo("Muffin");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "pets",
                                                        JsonArraySchema.builder()
                                                                .items(JsonObjectSchema.builder()
                                                                        .addStringProperty("name")
                                                                        .build())
                                                                .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor8 {

        class Person {

            String name;
            List<Pet> pets;
        }

        class Pet {

            String name;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_list_of_pojos(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor8 personExtractor = AiServices.create(PersonExtractor8.class, model);

        String text = "Extract the person's information from the following text: Klaus has 2 pets: Peanut and Muffin";

        // when
        PersonExtractor8.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.pets).hasSize(2);
        assertThat(person.pets.get(0).name).isEqualTo("Peanut");
        assertThat(person.pets.get(1).name).isEqualTo("Muffin");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "pets",
                                                        JsonArraySchema.builder()
                                                                .items(JsonObjectSchema.builder()
                                                                        .addStringProperty("name")
                                                                        .build())
                                                                .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_set_of_pojos(ChatLanguageModel model) {

        record Pet(String name) {
        }

        record Person(String name, Set<Pet> pets) {
        }

        interface PersonExtractor {

            Person extractPersonFrom(String text);
        }

        // given
        model = spy(model);

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, model);

        String text = "Extract the person's information from the following text: Klaus has 2 pets: Peanut and Muffin";

        // when
        Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person).isEqualTo(new Person("Klaus", Set.of(
                new Pet("Peanut"),
                new Pet("Muffin")
        )));

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "pets",
                                                        JsonArraySchema.builder()
                                                                .items(JsonObjectSchema.builder()
                                                                        .addStringProperty("name")
                                                                        .build())
                                                                .build())
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor10 {

        class Person {

            String name;
            Group[] groups;
        }

        enum Group {
            A,
            B,
            C
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_array_of_enums(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor10 personExtractor = AiServices.create(PersonExtractor10.class, model);

        String text = "Extract the person's information from the following text: Klaus is assigned to groups A and C";

        // when
        PersonExtractor10.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.groups).containsExactlyInAnyOrder(PersonExtractor10.Group.A, PersonExtractor10.Group.C);

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperties(new LinkedHashMap<>() {
                                                    {
                                                        put("name", new JsonStringSchema());
                                                        put(
                                                                "groups",
                                                                JsonArraySchema.builder()
                                                                        .items(JsonEnumSchema.builder()
                                                                                .enumValues("A", "B", "C")
                                                                                .build())
                                                                        .build());
                                                    }
                                                })
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor11 {

        class Person {

            String name;
            List<Group> groups;
        }

        enum Group {
            A,
            B,
            C
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_list_of_enums(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor11 personExtractor = AiServices.create(PersonExtractor11.class, model);

        String text = "Extract the person's information from the following text: Klaus is assigned to groups A and C";

        // when
        PersonExtractor11.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.groups).containsExactlyInAnyOrder(PersonExtractor11.Group.A, PersonExtractor11.Group.C);

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperties(new LinkedHashMap<>() {
                                                    {
                                                        put("name", new JsonStringSchema());
                                                        put(
                                                                "groups",
                                                                JsonArraySchema.builder()
                                                                        .items(JsonEnumSchema.builder()
                                                                                .enumValues("A", "B", "C")
                                                                                .build())
                                                                        .build());
                                                    }
                                                })
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor12 {

        class Person {

            String name;
            Set<Group> groups;
        }

        enum Group {
            A,
            B,
            C
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_set_of_enums(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor12 personExtractor = AiServices.create(PersonExtractor12.class, model);

        String text = "Extract the person's information from the following text: Klaus is assigned to groups A and C";

        // when
        PersonExtractor12.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.groups).containsExactlyInAnyOrder(PersonExtractor12.Group.A, PersonExtractor12.Group.C);

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperties(new LinkedHashMap<>() {
                                                    {
                                                        put("name", new JsonStringSchema());
                                                        put(
                                                                "groups",
                                                                JsonArraySchema.builder()
                                                                        .items(JsonEnumSchema.builder()
                                                                                .enumValues("A", "B", "C")
                                                                                .build())
                                                                        .build());
                                                    }
                                                })
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor13 {

        class Person {

            String name;
            LocalDate birthDate;
            LocalTime birthTime;
            LocalDateTime birthDateTime;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_local_date_time_fields(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor13 personExtractor = AiServices.create(PersonExtractor13.class, model);

        String text = "Extract the person's information from the following text." +
                "Fill in all the fields where the information is available! " +
                "Text: 'Klaus was born at 14:43 on 12th of August 1976'";

        // when
        PersonExtractor13.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.birthDate).isEqualTo(LocalDate.of(1976, 8, 12));
        assertThat(person.birthTime).isEqualTo(LocalTime.of(14, 43));

        assertThat(person.birthDateTime).isEqualTo(LocalDateTime.of(1976, 8, 12, 14, 43));

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperties(new LinkedHashMap<>() {
                                                    {
                                                        put("name", new JsonStringSchema());
                                                        put(
                                                                "birthDate",
                                                                JsonObjectSchema.builder()
                                                                        .addProperties(
                                                                                new LinkedHashMap<>() {
                                                                                    {
                                                                                        put(
                                                                                                "year",
                                                                                                new JsonIntegerSchema());
                                                                                        put(
                                                                                                "month",
                                                                                                new JsonIntegerSchema());
                                                                                        put(
                                                                                                "day",
                                                                                                new JsonIntegerSchema());
                                                                                    }
                                                                                })
                                                                        .build());
                                                        put(
                                                                "birthTime",
                                                                JsonObjectSchema.builder()
                                                                        .addProperties(
                                                                                new LinkedHashMap<>() {
                                                                                    {
                                                                                        put(
                                                                                                "hour",
                                                                                                new JsonIntegerSchema());
                                                                                        put(
                                                                                                "minute",
                                                                                                new JsonIntegerSchema());
                                                                                        put(
                                                                                                "second",
                                                                                                new JsonIntegerSchema());
                                                                                        put(
                                                                                                "nano",
                                                                                                new JsonIntegerSchema());
                                                                                    }
                                                                                })
                                                                        .build());
                                                        put(
                                                                "birthDateTime",
                                                                JsonObjectSchema.builder()
                                                                        .addProperties(
                                                                                new LinkedHashMap<>() {
                                                                                    {
                                                                                        put(
                                                                                                "date",
                                                                                                JsonObjectSchema
                                                                                                        .builder()
                                                                                                        .addProperties(
                                                                                                                new LinkedHashMap<>() {
                                                                                                                    {
                                                                                                                        put(
                                                                                                                                "year",
                                                                                                                                new JsonIntegerSchema());
                                                                                                                        put(
                                                                                                                                "month",
                                                                                                                                new JsonIntegerSchema());
                                                                                                                        put(
                                                                                                                                "day",
                                                                                                                                new JsonIntegerSchema());
                                                                                                                    }
                                                                                                                })
                                                                                                        .build());
                                                                                        put(
                                                                                                "time",
                                                                                                JsonObjectSchema
                                                                                                        .builder()
                                                                                                        .addProperties(
                                                                                                                new LinkedHashMap<>() {
                                                                                                                    {
                                                                                                                        put(
                                                                                                                                "hour",
                                                                                                                                new JsonIntegerSchema());
                                                                                                                        put(
                                                                                                                                "minute",
                                                                                                                                new JsonIntegerSchema());
                                                                                                                        put(
                                                                                                                                "second",
                                                                                                                                new JsonIntegerSchema());
                                                                                                                        put(
                                                                                                                                "nano",
                                                                                                                                new JsonIntegerSchema());
                                                                                                                    }
                                                                                                                })
                                                                                                        .build());
                                                                                    }
                                                                                })
                                                                        .build());
                                                    }
                                                })
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }


    interface PersonExtractor14 {

        class Person {

            String name;
        }

        Result<Person> extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_return_result_with_pojo(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor14 personExtractor = AiServices.create(PersonExtractor14.class, model);

        String text = "Extract the person's information from the following text: Klaus";

        // when
        Result<PersonExtractor14.Person> result = personExtractor.extractPersonFrom(text);
        PersonExtractor14.Person person = result.content();

        // then
        assertThat(person.name).isEqualTo("Klaus");

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addProperties(new LinkedHashMap<>() {
                                                    {
                                                        put("name", new JsonStringSchema());
                                                    }
                                                })
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor15 {

        class Person {

            String name;
            List<Person> children;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    @EnabledIf("supportsRecursion")
    void should_extract_pojo_with_recursion(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor15 personExtractor = AiServices.create(PersonExtractor15.class, model);

        String text = "Extract the person's information from the following text: " +
                "Francine has 2 children: Steve and Hayley";

        // when
        PersonExtractor15.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Francine");
        assertThat(person.children).hasSize(2);
        assertThat(person.children.get(0).name).isEqualTo("Steve");
        assertThat(person.children.get(0).children).isNullOrEmpty();
        assertThat(person.children.get(1).name).isEqualTo("Hayley");
        assertThat(person.children.get(1).children).isNullOrEmpty();

        String reference = generateUUIDFrom(PersonExtractor15.Person.class.getName());

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("name")
                                                .addProperty(
                                                        "children",
                                                        JsonArraySchema.builder()
                                                                .items(JsonReferenceSchema.builder()
                                                                        .reference(reference)
                                                                        .build())
                                                                .build())
                                                .definitions(Map.of(
                                                        reference,
                                                        JsonObjectSchema.builder()
                                                                .addStringProperty("name")
                                                                .addProperty(
                                                                        "children",
                                                                        JsonArraySchema.builder()
                                                                                .items(
                                                                                        JsonReferenceSchema
                                                                                                .builder()
                                                                                                .reference(
                                                                                                        reference)
                                                                                                .build())
                                                                                .build())
                                                                .build()))
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    interface PersonExtractor16 {

        class Person {

            UUID id;
            String name;
        }

        Person extractPersonFrom(String text);
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_pojo_with_uuid(ChatLanguageModel model) {

        // given
        model = spy(model);

        PersonExtractor16 personExtractor = AiServices.create(PersonExtractor16.class, model);

        String text = """
                Klaus can be identified by the following IDs:
                - 12345
                - 567b229a-6b0a-4f1e-9006-448cd9dfbfda
                - Klaus12345
                """;

        // when
        PersonExtractor16.Person person = personExtractor.extractPersonFrom(text);

        // then
        assertThat(person.name).isEqualTo("Klaus");
        assertThat(person.id).isEqualTo(UUID.fromString("567b229a-6b0a-4f1e-9006-448cd9dfbfda"));

        verify(model)
                .chat(ChatRequest.builder()
                        .messages(singletonList(userMessage(text)))
                        .responseFormat(ResponseFormat.builder()
                                .type(JSON)
                                .jsonSchema(JsonSchema.builder()
                                        .name("Person")
                                        .rootElement(JsonObjectSchema.builder()
                                                .addStringProperty("id", "String in a UUID format")
                                                .addStringProperty("name")
                                                .build())
                                        .build())
                                .build())
                        .build());
        verify(model).supportedCapabilities();
    }

    protected boolean supportsRecursion() {
        return false;
    }
}
