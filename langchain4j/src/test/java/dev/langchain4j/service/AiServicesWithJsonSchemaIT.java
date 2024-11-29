package dev.langchain4j.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.internal.Utils.generateUUIDFrom;
import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;
import static dev.langchain4j.service.AiServicesWithJsonSchemaIT.PersonExtractor3.MaritalStatus.SINGLE;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public abstract class AiServicesWithJsonSchemaIT {

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

    @Test
    void should_extract_pojo_with_primitives() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

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
                                            .addStringProperty("name")
                                            .addIntegerProperty("age")
                                            .addNumberProperty("height")
                                            .addBooleanProperty("married")
                                            .required("name", "age", "height", "married")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
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

    @Test
    void should_extract_pojo_with_nested_pojo() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor2 personExtractor = AiServices.create(PersonExtractor2.class, model);

            String text = "Klaus wants a delivery to Langley Falls, but his company is in New York";

            // when
            PersonExtractor2.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.shippingAddress.city).isEqualTo("Langley Falls");
            assertThat(person.billingAddress.city).isEqualTo("New York");

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addProperty("shippingAddress", JsonObjectSchema.builder()
                                                    .addStringProperty("city")
                                                    .required("city")
                                                    .build())
                                            .addProperty("billingAddress", JsonObjectSchema.builder()
                                                    .addStringProperty("city")
                                                    .required("city")
                                                    .build())
                                            .required("name", "shippingAddress", "billingAddress")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor3 {

        class Person {

            String name;
            MaritalStatus maritalStatus;
        }

        enum MaritalStatus {

            SINGLE, MARRIED
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_enum() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

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
                                            .addStringProperty("name")
                                            .addEnumProperty("maritalStatus", List.of("SINGLE", "MARRIED"))
                                            .required("name", "maritalStatus")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor4 {

        class Person {

            String name;
            String[] favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_array_of_primitives() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

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
                                            .addStringProperty("name")
                                            .addProperty("favouriteColors", JsonArraySchema.builder()
                                                    .items(new JsonStringSchema())
                                                    .build())
                                            .required("name", "favouriteColors")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    interface PersonExtractor5 {

        class Person {

            String name;
            List<String> favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_list_of_primitives() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

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
                                            .addStringProperty("name")
                                            .addProperty("favouriteColors", JsonArraySchema.builder()
                                                    .items(new JsonStringSchema())
                                                    .build())
                                            .required("name", "favouriteColors")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor6 {

        class Person {

            String name;
            Set<String> favouriteColors;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_set_of_primitives() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

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
                                            .addStringProperty("name")
                                            .addProperty("favouriteColors", JsonArraySchema.builder()
                                                    .items(new JsonStringSchema())
                                                    .build())
                                            .required("name", "favouriteColors")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
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

    @Test
    void should_extract_pojo_with_array_of_pojos() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

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
                                            .addStringProperty("name")
                                            .addProperty("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .addStringProperty("name")
                                                            .required("name")
                                                            .build())
                                                    .build())
                                            .required("name", "pets")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
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

    @Test
    void should_extract_pojo_with_list_of_pojos() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

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
                                            .addStringProperty("name")
                                            .addProperty("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .addStringProperty("name")
                                                            .required("name")
                                                            .build())
                                                    .build())
                                            .required("name", "pets")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor9 {

        class Person {

            String name;
            Set<Pet> pets;
        }

        class Pet {

            String name;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_set_of_pojos() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor9 personExtractor = AiServices.create(PersonExtractor9.class, model);

            String text = "Klaus has 2 pets: Peanut and Muffin";

            // when
            PersonExtractor9.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.pets).hasSize(2);
            Iterator<PersonExtractor9.Pet> iterator = person.pets.iterator();
            assertThat(iterator.next().name).isEqualTo("Peanut");
            assertThat(iterator.next().name).isEqualTo("Muffin");

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addProperty("pets", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .addStringProperty("name")
                                                            .required("name")
                                                            .build())
                                                    .build())
                                            .required("name", "pets")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor10 {

        class Person {

            String name;
            Group[] groups;
        }

        enum Group {

            A, B, C
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_array_of_enums() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor10 personExtractor = AiServices.create(PersonExtractor10.class, model);

            String text = "Klaus is assigned to groups A and C";

            // when
            PersonExtractor10.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.groups).containsExactly(PersonExtractor10.Group.A, PersonExtractor10.Group.C);


            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                put("name", new JsonStringSchema());
                                                put("groups", JsonArraySchema.builder()
                                                        .items(JsonEnumSchema.builder()
                                                                .enumValues("A", "B", "C")
                                                                .build())
                                                        .build());
                                            }})
                                            .required("name", "groups")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor11 {

        class Person {

            String name;
            List<Group> groups;
        }

        enum Group {

            A, B, C
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_list_of_enums() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor11 personExtractor = AiServices.create(PersonExtractor11.class, model);

            String text = "Klaus is assigned to groups A and C";

            // when
            PersonExtractor11.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.groups).containsExactly(PersonExtractor11.Group.A, PersonExtractor11.Group.C);

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                put("name", new JsonStringSchema());
                                                put("groups", JsonArraySchema.builder()
                                                        .items(JsonEnumSchema.builder()
                                                                .enumValues("A", "B", "C")
                                                                .build())
                                                        .build());
                                            }})
                                            .required("name", "groups")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor12 {

        class Person {

            String name;
            Set<Group> groups;
        }

        enum Group {

            A, B, C
        }

        Person extractPersonFrom(String text);
    }

    @Test
    void should_extract_pojo_with_set_of_enums() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor12 personExtractor = AiServices.create(PersonExtractor12.class, model);

            String text = "Klaus is assigned to groups A and C";

            // when
            PersonExtractor12.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.groups).containsExactly(PersonExtractor12.Group.A, PersonExtractor12.Group.C);

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                put("name", new JsonStringSchema());
                                                put("groups", JsonArraySchema.builder()
                                                        .items(JsonEnumSchema.builder()
                                                                .enumValues("A", "B", "C")
                                                                .build())
                                                        .build());
                                            }})
                                            .required("name", "groups")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
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

    @Test
    void should_extract_pojo_with_local_date_time_fields() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor13 personExtractor = AiServices.create(PersonExtractor13.class, model);

            String text = "Klaus was born at 14:43 on 12th of August 1976";

            // when
            PersonExtractor13.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.birthDate).isEqualTo(LocalDate.of(1976, 8, 12));
            assertThat(person.birthTime).isEqualTo(LocalTime.of(14, 43));
            assertThat(person.birthDateTime)
                    .isEqualTo(LocalDateTime.of(1976, 8, 12, 14, 43));

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                put("name", new JsonStringSchema());
                                                put("birthDate", JsonObjectSchema.builder()
                                                        .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                            put("year", new JsonIntegerSchema());
                                                            put("month", new JsonIntegerSchema());
                                                            put("day", new JsonIntegerSchema());
                                                        }})
                                                        .required("year", "month", "day")
                                                        .build());
                                                put("birthTime", JsonObjectSchema.builder()
                                                        .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                            put("hour", new JsonIntegerSchema());
                                                            put("minute", new JsonIntegerSchema());
                                                            put("second", new JsonIntegerSchema());
                                                            put("nano", new JsonIntegerSchema());
                                                        }})
                                                        .required("hour", "minute", "second", "nano")
                                                        .build());
                                                put("birthDateTime", JsonObjectSchema.builder()
                                                        .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                            put("date", JsonObjectSchema.builder()
                                                                    .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                                        put("year", new JsonIntegerSchema());
                                                                        put("month", new JsonIntegerSchema());
                                                                        put("day", new JsonIntegerSchema());
                                                                    }})
                                                                    .required("year", "month", "day")
                                                                    .build());
                                                            put("time", JsonObjectSchema.builder()
                                                                    .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                                        put("hour", new JsonIntegerSchema());
                                                                        put("minute", new JsonIntegerSchema());
                                                                        put("second", new JsonIntegerSchema());
                                                                        put("nano", new JsonIntegerSchema());
                                                                    }})
                                                                    .required("hour", "minute", "second", "nano")
                                                                    .build());
                                                        }})
                                                        .required("date", "time")
                                                        .build());
                                            }})
                                            .required("name", "birthDate", "birthTime", "birthDateTime")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }


    interface PersonExtractor14 {

        class Person {

            String name;
        }

        Result<Person> extractPersonFrom(String text);
    }

    @Test
    void should_return_result_with_pojo() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor14 personExtractor = AiServices.create(PersonExtractor14.class, model);

            String text = "Klaus";

            // when
            Result<PersonExtractor14.Person> result = personExtractor.extractPersonFrom(text);
            PersonExtractor14.Person person = result.content();

            // then
            assertThat(person.name).isEqualTo("Klaus");

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .properties(new LinkedHashMap<String, JsonSchemaElement>() {{
                                                put("name", new JsonStringSchema());
                                            }})
                                            .required("name")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    interface PersonExtractor15 {

        class Person {

            String name;
            List<Person> children;
        }

        Person extractPersonFrom(String text);
    }

    @Test
    @EnabledIf("supportsRecursion")
    void should_extract_pojo_with_recursion() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor15 personExtractor = AiServices.create(PersonExtractor15.class, model);

            String text = "Francine has 2 children: Steve and Hayley";

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

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Person")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addStringProperty("name")
                                            .addProperty("children", JsonArraySchema.builder()
                                                    .items(JsonReferenceSchema.builder()
                                                            .reference(reference)
                                                            .build())
                                                    .build())
                                            .required("name", "children")
                                            .definitions(Map.of(reference, JsonObjectSchema.builder()
                                                    .addStringProperty("name")
                                                    .addProperty("children", JsonArraySchema.builder()
                                                            .items(JsonReferenceSchema.builder()
                                                                    .reference(reference)
                                                                    .build())
                                                            .build())
                                                    .required("name", "children")
                                                    .build()))
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    protected boolean supportsRecursion() {
        return false;
    }
}
