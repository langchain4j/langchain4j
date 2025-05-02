package dev.langchain4j.service.common;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatModel;
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
import dev.langchain4j.service.UserMessage;
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

    protected abstract List<ChatModel> models();


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
    protected void should_extract_pojo_with_primitives(ChatModel model) {

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
    protected void should_extract_pojo_with_missing_data(ChatModel model) {

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
        ChatModel spyModel = spy(model);

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

    protected boolean isStrictJsonSchemaEnabled(ChatModel model) {
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
    protected void should_extract_pojo_with_nested_pojo(ChatModel model) {

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
    protected void should_extract_pojo_with_enum(ChatModel model) {

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
    protected void should_extract_pojo_with_array_of_primitives(ChatModel model) {

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
    protected void should_extract_pojo_with_list_of_primitives(ChatModel model) {

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
    protected void should_extract_pojo_with_set_of_primitives(ChatModel model) {

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
    protected void should_extract_pojo_with_array_of_pojos(ChatModel model) {

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
    protected void should_extract_pojo_with_list_of_pojos(ChatModel model) {

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
    protected void should_extract_pojo_with_set_of_pojos(ChatModel model) {

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
    protected void should_extract_pojo_with_array_of_enums(ChatModel model) {

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
    protected void should_extract_pojo_with_list_of_enums(ChatModel model) {

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
    protected void should_extract_pojo_with_set_of_enums(ChatModel model) {

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
    protected void should_extract_pojo_with_local_date_time_fields(ChatModel model) {

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

    @ParameterizedTest
    @MethodSource("models")
    protected void should_return_result_with_pojo(ChatModel model) {

        // given
        interface PersonExtractor {

            class Person {

                String name;
            }

            Result<Person> extractPersonFrom(String text);
        }

        model = spy(model);

        PersonExtractor personExtractor = AiServices.create(PersonExtractor.class, model);

        String text = "Extract the person's information from the following text: Klaus";

        // when
        Result<PersonExtractor.Person> result = personExtractor.extractPersonFrom(text);
        PersonExtractor.Person person = result.content();

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
    void should_extract_pojo_with_recursion(ChatModel model) {

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
    protected void should_extract_pojo_with_uuid(ChatModel model) {

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


    // Primitives

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_boolean_primitive(ChatModel model) {

        // given
        interface BooleanExtractor {

            @UserMessage("Extract if the person from the following text is a man: {{it}}")
            boolean isPersonAMan(String text);
        }

        model = spy(model);

        BooleanExtractor booleanExtractor = AiServices.create(BooleanExtractor.class, model);

        String text = "Klaus is a 37-year-old man, 1.78 meters tall, and single.";

        // when
        boolean isAMan = booleanExtractor.isPersonAMan(text);

        // then
        assertThat(isAMan).isTrue();

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract if the person from the following text is a man: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("boolean")
                                .rootElement(JsonObjectSchema.builder()
                                        .addBooleanProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_boolean_boxed(ChatModel model) {

        // given
        interface BooleanExtractor {

            @UserMessage("Extract if the person from the following text is a man: {{it}}")
            Boolean isPersonAMan(String text);
        }

        model = spy(model);

        BooleanExtractor booleanExtractor = AiServices.create(BooleanExtractor.class, model);

        String text = "Klaus is a 37-year-old man, 1.78 meters tall, and single.";

        // when
        Boolean isAMan = booleanExtractor.isPersonAMan(text);

        // then
        assertThat(isAMan).isTrue();

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract if the person from the following text is a man: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("boolean")
                                .rootElement(JsonObjectSchema.builder()
                                        .addBooleanProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_int_primitive(ChatModel model) {

        // given
        interface IntExtractor {

            @UserMessage("Extract number of people mentioned in the following text: {{it}}")
            int extractNumberOfPeople(String text);
        }

        model = spy(model);

        IntExtractor intExtractor = AiServices.create(IntExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married.";

        // when
        int numberOfPeople = intExtractor.extractNumberOfPeople(text);

        // then
        assertThat(numberOfPeople).isEqualTo(2);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract number of people mentioned in the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("integer")
                                .rootElement(JsonObjectSchema.builder()
                                        .addIntegerProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_int_boxed(ChatModel model) {

        // given
        interface IntegerExtractor {

            @UserMessage("Extract number of people mentioned in the following text: {{it}}")
            Integer extractNumberOfPeople(String text);
        }

        model = spy(model);

        IntegerExtractor intExtractor = AiServices.create(IntegerExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married.";

        // when
        Integer numberOfPeople = intExtractor.extractNumberOfPeople(text);

        // then
        assertThat(numberOfPeople).isEqualTo(2);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract number of people mentioned in the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("integer")
                                .rootElement(JsonObjectSchema.builder()
                                        .addIntegerProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_long_primitive(ChatModel model) {

        // given
        interface LongExtractor {

            @UserMessage("Extract number of people mentioned in the following text: {{it}}")
            long extractNumberOfPeople(String text);
        }

        model = spy(model);

        LongExtractor intExtractor = AiServices.create(LongExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married.";

        // when
        long numberOfPeople = intExtractor.extractNumberOfPeople(text);

        // then
        assertThat(numberOfPeople).isEqualTo(2);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract number of people mentioned in the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("integer")
                                .rootElement(JsonObjectSchema.builder()
                                        .addIntegerProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_long_boxed(ChatModel model) {

        // given
        interface LongExtractor {

            @UserMessage("Extract number of people mentioned in the following text: {{it}}")
            Long extractNumberOfPeople(String text);
        }

        model = spy(model);

        LongExtractor intExtractor = AiServices.create(LongExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married.";

        // when
        Long numberOfPeople = intExtractor.extractNumberOfPeople(text);

        // then
        assertThat(numberOfPeople).isEqualTo(2);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract number of people mentioned in the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("integer")
                                .rootElement(JsonObjectSchema.builder()
                                        .addIntegerProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_float_primitive(ChatModel model) {

        // given
        interface FloatExtractor {

            @UserMessage("Extract temperature in Munich from the following text: {{it}}")
            float extractTemperatureInMunich(String text);
        }

        model = spy(model);

        FloatExtractor doubleExtractor = AiServices.create(FloatExtractor.class, model);

        String text = "The average temperature of the coldest month is of -0.5 °C";

        // when
        float temperatureInMunich = doubleExtractor.extractTemperatureInMunich(text);

        // then
        assertThat(temperatureInMunich).isEqualTo(-0.5f);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract temperature in Munich from the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("number")
                                .rootElement(JsonObjectSchema.builder()
                                        .addNumberProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_float_boxed(ChatModel model) {

        // given
        interface FloatExtractor {

            @UserMessage("Extract temperature in Munich from the following text: {{it}}")
            Float extractTemperatureInMunich(String text);
        }

        model = spy(model);

        FloatExtractor doubleExtractor = AiServices.create(FloatExtractor.class, model);

        String text = "The average temperature of the coldest month is of -0.5 °C";

        // when
        Float temperatureInMunich = doubleExtractor.extractTemperatureInMunich(text);

        // then
        assertThat(temperatureInMunich).isEqualTo(-0.5f);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract temperature in Munich from the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("number")
                                .rootElement(JsonObjectSchema.builder()
                                        .addNumberProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_double_primitive(ChatModel model) {

        // given
        interface DoubleExtractor {

            @UserMessage("Extract temperature in Munich from the following text: {{it}}")
            double extractTemperatureInMunich(String text);
        }

        model = spy(model);

        DoubleExtractor doubleExtractor = AiServices.create(DoubleExtractor.class, model);

        String text = "The average temperature of the coldest month is of -0.5 °C";

        // when
        double temperatureInMunich = doubleExtractor.extractTemperatureInMunich(text);

        // then
        assertThat(temperatureInMunich).isEqualTo(-0.5);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract temperature in Munich from the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("number")
                                .rootElement(JsonObjectSchema.builder()
                                        .addNumberProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_double_boxed(ChatModel model) {

        // given
        interface DoubleExtractor {

            @UserMessage("Extract temperature in Munich from the following text: {{it}}")
            Double extractTemperatureInMunich(String text);
        }

        model = spy(model);

        DoubleExtractor doubleExtractor = AiServices.create(DoubleExtractor.class, model);

        String text = "The average temperature of the coldest month is of -0.5 °C";

        // when
        Double temperatureInMunich = doubleExtractor.extractTemperatureInMunich(text);

        // then
        assertThat(temperatureInMunich).isEqualTo(-0.5);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract temperature in Munich from the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("number")
                                .rootElement(JsonObjectSchema.builder()
                                        .addNumberProperty("value")
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    // Lists

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_list_of_pojo(ChatModel model) {

        // given
        interface PeopleExtractor {

            class Person {

                String name;
                int age;
                Double height;
                boolean married;
            }

            List<Person> extractPeopleFrom(String text);
        }

        model = spy(model);

        PeopleExtractor peopleExtractor = AiServices.create(PeopleExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married.";

        // when
        List<PeopleExtractor.Person> people = peopleExtractor.extractPeopleFrom(text);

        // then
        assertThat(people.get(0).name).isEqualTo("Klaus");
        assertThat(people.get(0).age).isEqualTo(37);
        assertThat(people.get(0).height).isEqualTo(1.78);
        assertThat(people.get(0).married).isFalse();

        assertThat(people.get(1).name).isEqualTo("Franny");
        assertThat(people.get(1).age).isEqualTo(35);
        assertThat(people.get(1).height).isEqualTo(1.65);
        assertThat(people.get(1).married).isTrue();

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("List_of_Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperty("values", JsonArraySchema.builder()
                                                .items(JsonObjectSchema.builder()
                                                        .addStringProperty("name")
                                                        .addIntegerProperty("age")
                                                        .addNumberProperty("height")
                                                        .addBooleanProperty("married")
                                                        .build())
                                                .build())
                                        .required("values")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_return_result_with_list_of_pojo(ChatModel model) {

        // given
        interface PeopleExtractor {

            class Person {

                String name;
            }

            Result<List<Person>> extractPeopleFrom(String text);
        }

        model = spy(model);

        PeopleExtractor personExtractor = AiServices.create(PeopleExtractor.class, model);

        String text = "Extract the person's information from the following text: Klaus and Francine";

        // when
        Result<List<PeopleExtractor.Person>> result = personExtractor.extractPeopleFrom(text);
        List<PeopleExtractor.Person> people = result.content();

        // then
        assertThat(people).hasSize(2);
        assertThat(people.get(0).name).isEqualTo("Klaus");
        assertThat(people.get(1).name).isEqualTo("Francine");

        verify(model).chat(ChatRequest.builder()
                .messages(List.of(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("List_of_Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperty("values", JsonArraySchema.builder()
                                                .items(JsonObjectSchema.builder()
                                                        .addStringProperty("name")
                                                        .build())
                                                .build())
                                        .required("values")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_list_of_strings(ChatModel model) {

        // given
        interface ListOfStringsExtractor {

            @UserMessage("Extract names of people from the following text: {{it}}")
            List<String> extractPeopleNames(String text);
        }

        model = spy(model);

        ListOfStringsExtractor listOfStringsExtractor = AiServices.create(ListOfStringsExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married.";

        // when
        List<String> names = listOfStringsExtractor.extractPeopleNames(text);

        // then
        assertThat(names.size()).isEqualTo(2);
        assertThat(names.get(0)).contains("Klaus");
        assertThat(names.get(1)).contains("Franny");

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract names of people from the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("List_of_String")
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperty("values", JsonArraySchema.builder()
                                                .items(JsonStringSchema.builder()
                                                        .build())
                                                .build())
                                        .required("values")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }


    // Sets

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_set_of_strings(ChatModel model) {

        // given
        interface SetOfStringsExtractor {

            @UserMessage("Extract names of people from the following text: {{it}}")
            Set<String> extractSetOfPeopleNames(String text);
        }

        model = spy(model);

        SetOfStringsExtractor setOfStringsExtractor = AiServices.create(SetOfStringsExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married.";

        // when
        Set<String> names = setOfStringsExtractor.extractSetOfPeopleNames(text);

        // then
        assertThat(names.size()).isEqualTo(2);
        assertThat(names).contains("Klaus");
        assertThat(names).contains("Franny");

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage("Extract names of people from the following text: " + text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Set_of_String")
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperty("values", JsonArraySchema.builder()
                                                .items(JsonStringSchema.builder()
                                                        .build())
                                                .build())
                                        .required("values")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_set_of_pojo(ChatModel model) {

        // given
        interface PojoSetExtractor {

            class Person {

                String name;
                int age;
                Double height;
                boolean married;
            }

            Set<Person> extractSetOfPojoFrom(String text);
        }

        model = spy(model);

        PojoSetExtractor pojoSetExtractor = AiServices.create(PojoSetExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married.";

        // when
        Set<PojoSetExtractor.Person> people = pojoSetExtractor.extractSetOfPojoFrom(text);

        // then
        assertThat(people).hasSize(2);

        assertThat(people).anyMatch(person ->
                person.name.equals("Klaus") &&
                        person.age == 37 &&
                        person.height.equals(1.78) &&
                        !person.married
        );

        assertThat(people).anyMatch(person ->
                person.name.equals("Franny") &&
                        person.age == 35 &&
                        person.height.equals(1.65) &&
                        person.married
        );

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Set_of_Person")
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperty("values", JsonArraySchema.builder()
                                                .items(JsonObjectSchema.builder()
                                                        .addStringProperty("name")
                                                        .addIntegerProperty("age")
                                                        .addNumberProperty("height")
                                                        .addBooleanProperty("married")
                                                        .build())
                                                .build())
                                        .required("values")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }


    // Enums

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_enum(ChatModel model) {

        // given
        enum MaritalStatus {
            SINGLE, MARRIED
        }

        interface EnumExtractor {

            MaritalStatus extractEnumFrom(String text);
        }

        model = spy(model);

        EnumExtractor enumExtractor = AiServices.create(EnumExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single.";

        // when
        MaritalStatus maritalStatus = enumExtractor.extractEnumFrom(text);

        // then
        assertThat(maritalStatus).isEqualTo(MaritalStatus.SINGLE);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("MaritalStatus")
                                .rootElement(JsonObjectSchema.builder()
                                        .addEnumProperty("value", List.of("SINGLE", "MARRIED"))
                                        .required("value")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_list_of_enums(ChatModel model) {

        // given
        enum MaritalStatus {
            SINGLE, MARRIED
        }

        interface EnumListExtractor {

            List<MaritalStatus> extractListOfEnumsFrom(String text);
        }

        model = spy(model);

        EnumListExtractor enumListExtractor = AiServices.create(EnumListExtractor.class, model);

        String text = "Klaus is 37 years old, 1.78m height and single. " +
                "Franny is 35 years old, 1.65m height and married." +
                "Staniel is 33 years old, 1.70m height and married.";

        // when
        List<MaritalStatus> maritalStatuses = enumListExtractor.extractListOfEnumsFrom(text);

        // then
        assertThat(maritalStatuses)
                .containsExactly(MaritalStatus.SINGLE, MaritalStatus.MARRIED, MaritalStatus.MARRIED);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("List_of_MaritalStatus")
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperty("values", JsonArraySchema.builder()
                                                .items(JsonEnumSchema.builder()
                                                        .enumValues("SINGLE", "MARRIED")
                                                        .build())
                                                .build())
                                        .required("values")
                                        .build())
                                .build())
                        .build())
                .build());
        verify(model).supportedCapabilities();
    }

    @ParameterizedTest
    @MethodSource("models")
    protected void should_extract_set_of_enums(ChatModel model) {

        // given
        enum Weather {
            SUNNY, RAINY, CLOUDY, WINDY
        }

        interface EnumSetExtractor {

            Set<Weather> extractSetOfEnumsFrom(String text);
        }

        model = spy(model);

        EnumSetExtractor enumSetExtractor = AiServices.create(EnumSetExtractor.class, model);

        String text = "The weather in Berlin was sunny and windy." +
                " Paris experienced rainy and cloudy weather." +
                " New York had cloudy and windy weather.";

        // when
        Set<Weather> weatherCharacteristics = enumSetExtractor.extractSetOfEnumsFrom(text);

        // then
        assertThat(weatherCharacteristics)
                .containsExactlyInAnyOrder(Weather.SUNNY, Weather.WINDY, Weather.RAINY, Weather.CLOUDY);

        verify(model).chat(ChatRequest.builder()
                .messages(singletonList(userMessage(text)))
                .responseFormat(ResponseFormat.builder()
                        .type(JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name("Set_of_Weather")
                                .rootElement(JsonObjectSchema.builder()
                                        .addProperty("values", JsonArraySchema.builder()
                                                .items(JsonEnumSchema.builder()
                                                        .enumValues("SUNNY", "RAINY", "CLOUDY", "WINDY")
                                                        .build())
                                                .build())
                                        .required("values")
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
