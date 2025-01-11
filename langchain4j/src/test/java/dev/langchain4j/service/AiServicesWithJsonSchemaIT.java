package dev.langchain4j.service;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonReferenceSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT.EnumListExtractor.MaritalStatus;
import dev.langchain4j.service.AiServicesWithJsonSchemaIT.EnumSetExtractor.WeatherCharacteristic;
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
import static dev.langchain4j.service.AiServicesWithJsonSchemaIT.EnumExtractor.MaritalStatus.SINGLE;
import static dev.langchain4j.service.AiServicesWithJsonSchemaIT.EnumListExtractor.MaritalStatus.MARRIED;
import static dev.langchain4j.service.AiServicesWithJsonSchemaIT.EnumSetExtractor.WeatherCharacteristic.CLOUDY;
import static dev.langchain4j.service.AiServicesWithJsonSchemaIT.EnumSetExtractor.WeatherCharacteristic.RAINY;
import static dev.langchain4j.service.AiServicesWithJsonSchemaIT.EnumSetExtractor.WeatherCharacteristic.SUNNY;
import static dev.langchain4j.service.AiServicesWithJsonSchemaIT.EnumSetExtractor.WeatherCharacteristic.WINDY;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public abstract class AiServicesWithJsonSchemaIT {
    // TODO move to common, use parameterized tests
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

    @Test
    protected void should_extract_pojo_with_primitives() {

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

    interface PojoListExtractor {

        class Person {

            String name;
            int age;
            Double height;
            boolean married;
        }

        List<Person> extractListOfPojoFrom(String text);
    }

    @Test
    protected void should_extract_list_of_pojo() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PojoListExtractor pojoListExtractor = AiServices.create(PojoListExtractor.class, model);

            String text = "Klaus is 37 years old, 1.78m height and single. " +
                    "Franny is 35 years old, 1.65m height and married.";

            // when
            List<PojoListExtractor.Person> people = pojoListExtractor.extractListOfPojoFrom(text);

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
                                            .addProperty("items", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .addStringProperty("name")
                                                            .addIntegerProperty("age")
                                                            .addNumberProperty("height")
                                                            .addBooleanProperty("married")
                                                            .required("name", "age", "height", "married")
                                                            .build())
                                                    .build())
                                            .required("items")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    interface ListOfStringsExtractor {
        @UserMessage("Extract names of people from the following text: {{it}}")
        List<String> extractOnlyListOfPeopleNames(String text);
    }

    @Test
    protected void should_extract_list_of_strings() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            ListOfStringsExtractor listOfStringsExtractor = AiServices.create(ListOfStringsExtractor.class, model);

            String text = "Klaus is 37 years old, 1.78m height and single. " +
                    "Franny is 35 years old, 1.65m height and married.";

            // when
            List<String> names = listOfStringsExtractor.extractOnlyListOfPeopleNames(text);

            names.forEach(System.out::println);

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
                                            .addProperty("items", JsonArraySchema.builder()
                                                    .items(JsonStringSchema.builder()
                                                            .build())
                                                    .build())
                                            .required("items")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    interface SetOfStringsExtractor {
        @UserMessage("Extract names of people from the following text: {{it}}")
        Set<String> extractOnlySetOfPeopleNames(String text);
    }

    @Test
    protected void should_extract_set_of_strings() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            SetOfStringsExtractor setOfStringsExtractor = AiServices.create(SetOfStringsExtractor.class, model);

            String text = "Klaus is 37 years old, 1.78m height and single. " +
                    "Franny is 35 years old, 1.65m height and married.";

            // when
            Set<String> names = setOfStringsExtractor.extractOnlySetOfPeopleNames(text);

            names.forEach(System.out::println);

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
                                            .addProperty("items", JsonArraySchema.builder()
                                                    .items(JsonStringSchema.builder()
                                                            .build())
                                                    .build())
                                            .required("items")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    interface BooleanExtractor {
        @UserMessage("Extract if the person from the following text is a man: {{it}}")
        Boolean isPersonAMan(String text);
    }

    @Test
    protected void should_extract_boolean() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            BooleanExtractor booleanExtractor = AiServices.create(BooleanExtractor.class, model);

            String text = "Klaus is 37 years old, 1.78m height and single. ";

            // when
            boolean isAMan = booleanExtractor.isPersonAMan(text);

            // then
            assertThat(isAMan).isTrue();

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage("Extract if the person from the following text is a man: " + text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Boolean")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addProperty("boolean", JsonBooleanSchema.builder().build())
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    interface PojoSetExtractor {

        class Person {

            String name;
            int age;
            Double height;
            boolean married;
        }

        Set<Person> extractSetOfPojoFrom(String text);
    }

    @Test
    protected void should_extract_set_of_pojo() {

        for (ChatLanguageModel model : models()) {

            // given
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
                                            .addProperty("items", JsonArraySchema.builder()
                                                    .items(JsonObjectSchema.builder()
                                                            .addStringProperty("name")
                                                            .addIntegerProperty("age")
                                                            .addNumberProperty("height")
                                                            .addBooleanProperty("married")
                                                            .required("name", "age", "height", "married")
                                                            .build())
                                                    .build())
                                            .required("items")
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
    protected void should_extract_pojo_with_nested_pojo() {

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
    protected void should_extract_pojo_with_enum() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            PersonExtractor3 personExtractor = AiServices.create(PersonExtractor3.class, model);

            String text = "Klaus is single";

            // when
            PersonExtractor3.Person person = personExtractor.extractPersonFrom(text);

            // then
            assertThat(person.name).isEqualTo("Klaus");
            assertThat(person.maritalStatus).isEqualTo(PersonExtractor3.MaritalStatus.SINGLE);

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
    protected void should_extract_pojo_with_array_of_primitives() {

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
    protected void should_extract_pojo_with_list_of_primitives() {

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
    protected void should_extract_pojo_with_set_of_primitives() {

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
    protected void should_extract_pojo_with_array_of_pojos() {

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
    protected void should_extract_pojo_with_list_of_pojos() {

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
    protected void should_extract_pojo_with_set_of_pojos() {

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
    protected void should_extract_pojo_with_array_of_enums() {

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
    protected void should_extract_pojo_with_list_of_enums() {

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
    protected void should_extract_pojo_with_set_of_enums() {

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
    protected void should_extract_pojo_with_local_date_time_fields() {

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
    protected void should_return_result_with_pojo() {

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
    protected void should_extract_pojo_with_recursion() {

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


    // Enums

    interface EnumExtractor {

        enum MaritalStatus {

            SINGLE, MARRIED
        }

        MaritalStatus extractEnumFrom(String text);
    }

    @Test
    protected void should_extract_enum() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            EnumExtractor enumExtractor = AiServices.create(EnumExtractor.class, model);

            String text = "Klaus is 37 years old, 1.78m height and single.";

            // when
            EnumExtractor.MaritalStatus maritalStatus = enumExtractor.extractEnumFrom(text);

            // then
            assertThat(maritalStatus).isEqualTo(SINGLE);

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("MaritalStatus")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addEnumProperty("value", List.of("SINGLE", "MARRIED"))
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    interface EnumListExtractor {

        enum MaritalStatus {

            SINGLE, MARRIED
        }

        List<MaritalStatus> extractListOfEnumsFrom(String text);
    }

    @Test
    protected void should_extract_list_of_enums() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            EnumListExtractor enumListExtractor = AiServices.create(EnumListExtractor.class, model);

            String text = "Klaus is 37 years old, 1.78m height and single. " +
                    "Franny is 35 years old, 1.65m height and married." +
                    "Staniel is 33 years old, 1.70m height and married.";

            // when
            List<MaritalStatus> maritalStatuses = enumListExtractor.extractListOfEnumsFrom(text);

            // then
            assertThat(maritalStatuses).containsExactly(MaritalStatus.SINGLE, MARRIED, MARRIED);

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("List_of_MaritalStatus")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addProperty("items", JsonArraySchema.builder()
                                                    .items(JsonEnumSchema.builder()
                                                            .enumValues("SINGLE", "MARRIED")
                                                            .build())
                                                    .build())
                                            .required("items")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }

    interface EnumSetExtractor {

        enum WeatherCharacteristic {

            SUNNY, RAINY, CLOUDY, WINDY
        }

        Set<WeatherCharacteristic> extractSetOfEnumsFrom(String text);
    }

    @Test
    protected void should_extract_set_of_enums() {

        for (ChatLanguageModel model : models()) {

            // given
            model = spy(model);

            EnumSetExtractor enumSetExtractor = AiServices.create(EnumSetExtractor.class, model);

            String text = "The weather in Berlin was sunny and windy." +
                    " Paris experienced rainy and cloudy weather." +
                    " New York had cloudy and windy weather.";

            // when
            Set<WeatherCharacteristic> weatherCharacteristics = enumSetExtractor.extractSetOfEnumsFrom(text);

            // then
            assertThat(weatherCharacteristics).containsExactlyInAnyOrder(SUNNY, WINDY, RAINY, CLOUDY);

            verify(model).chat(ChatRequest.builder()
                    .messages(singletonList(userMessage(text)))
                    .responseFormat(ResponseFormat.builder()
                            .type(JSON)
                            .jsonSchema(JsonSchema.builder()
                                    .name("Set_of_WeatherCharacteristic")
                                    .rootElement(JsonObjectSchema.builder()
                                            .addProperty("items", JsonArraySchema.builder()
                                                    .items(JsonEnumSchema.builder()
                                                            .enumValues("SUNNY", "RAINY", "CLOUDY", "WINDY")
                                                            .build())
                                                    .build())
                                            .required("items")
                                            .build())
                                    .build())
                            .build())
                    .build());
            verify(model).supportedCapabilities();
        }
    }
}
