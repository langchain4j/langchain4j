package dev.langchain4j.service.output;

import com.google.gson.reflect.TypeToken;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static dev.langchain4j.service.output.JsonSchemas.isEnum;
import static dev.langchain4j.service.output.JsonSchemas.jsonSchemaFrom;
import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemasTest {

    class Pojo {

        String field;
    }

    @Test
    void should_return_json_schema_for_pojos() {
        assertThat(jsonSchemaFrom(Pojo.class)).isPresent();
        assertThat(jsonSchemaFrom(new TypeToken<Result<Pojo>>() {
        }.getType())).isPresent();
    }

    @Test
    void should_return_empty_for_not_pojos() {
        assertThat(jsonSchemaFrom(String.class)).isEmpty();
        assertThat(jsonSchemaFrom(AiMessage.class)).isEmpty();
        assertThat(jsonSchemaFrom(Response.class)).isEmpty();
        assertThat(jsonSchemaFrom(Integer.class)).isEmpty();
        assertThat(jsonSchemaFrom(LocalDate.class)).isEmpty();
        assertThat(jsonSchemaFrom(new TypeToken<Result<String>>() {
        }.getType())).isEmpty();
    }


    // POJO

    @Test
    void should_take_pojo_description_from_the_field() {

        // given
        class Address {

            String street;
            String city;
        }

        class Person {

            @Description("an address")
            Address address;
        }

        // when
        Optional<JsonSchema> jsonSchema = jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema rootElement = (JsonObjectSchema) jsonSchema.get().rootElement();
        JsonObjectSchema addressSchema = (JsonObjectSchema) rootElement.properties().get("address");
        assertThat(addressSchema.description()).isEqualTo("an address");
    }

    @Test
    void should_take_pojo_description_from_the_class() {

        // given
        @Description("an address")
        class Address {

            String street;
            String city;
        }

        class Person {

            Address address;
        }

        // when
        Optional<JsonSchema> jsonSchema = jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema rootElement = (JsonObjectSchema) jsonSchema.get().rootElement();
        JsonObjectSchema addressSchema = (JsonObjectSchema) rootElement.properties().get("address");
        assertThat(addressSchema.description()).isEqualTo("an address");
    }

    @Test
    void pojo_field_description_should_override_class_description() {

        // given
        @Description("an address")
        class Address {

            String street;
            String city;
        }

        class Person {

            @Description("an address 2")
            Address address;
        }

        // when
        Optional<JsonSchema> jsonSchema = jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema rootElement = (JsonObjectSchema) jsonSchema.get().rootElement();
        JsonObjectSchema addressSchema = (JsonObjectSchema) rootElement.properties().get("address");
        assertThat(addressSchema.description()).isEqualTo("an address 2");
    }


    // ENUM

    enum MaritalStatus {

        SINGLE, MARRIED
    }

    @Test
    void should_take_enum_description_from_the_field() {

        // given
        class Person {

            @Description("marital status")
            MaritalStatus maritalStatus;
        }

        // when
        Optional<JsonSchema> jsonSchema = jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema rootElement = (JsonObjectSchema) jsonSchema.get().rootElement();
        JsonEnumSchema maritalStatusSchema = (JsonEnumSchema) rootElement.properties().get("maritalStatus");
        assertThat(maritalStatusSchema.description()).isEqualTo("marital status");
    }


    @Description("marital status")
    enum MaritalStatus2 {

        SINGLE, MARRIED
    }

    @Test
    void should_take_enum_description_from_the_enum() {

        // given
        class Person {

            MaritalStatus2 maritalStatus;
        }

        // when
        Optional<JsonSchema> jsonSchema = jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema rootElement = (JsonObjectSchema) jsonSchema.get().rootElement();
        JsonEnumSchema maritalStatusSchema = (JsonEnumSchema) rootElement.properties().get("maritalStatus");
        assertThat(maritalStatusSchema.description()).isEqualTo("marital status");
    }


    @Description("marital status")
    enum MaritalStatus3 {

        SINGLE, MARRIED
    }

    @Test
    void enum_field_description_should_override_class_description() {

        // given
        class Person {

            @Description("marital status 2")
            MaritalStatus3 maritalStatus;
        }

        // when
        Optional<JsonSchema> jsonSchema = jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema rootElement = (JsonObjectSchema) jsonSchema.get().rootElement();
        JsonEnumSchema maritalStatusSchema = (JsonEnumSchema) rootElement.properties().get("maritalStatus");
        assertThat(maritalStatusSchema.description()).isEqualTo("marital status 2");
    }

    // ARRAY

    @Test
    void should_take_array_description_from_the_field() {

        // given
        class Pet {

            String name;
        }

        class Person {

            @Description("pets of a person")
            Pet[] pets;
        }

        // when
        Optional<JsonSchema> jsonSchema = jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema rootElement = (JsonObjectSchema) jsonSchema.get().rootElement();
        JsonArraySchema petsSchema = (JsonArraySchema) rootElement.properties().get("pets");
        assertThat(petsSchema.description()).isEqualTo("pets of a person");
    }

    private enum TestEnum {
        VALUE1, VALUE2
    }

    @ParameterizedTest
    @MethodSource
    void test_isEnum(Type type, boolean expected) {
        assertThat(isEnum(type)).isEqualTo(expected);
    }

    static Stream<Arguments> test_isEnum() throws NoSuchMethodException {

        Method enumListMethod = GenericEnumHolder.class.getDeclaredMethod("getEnumList");
        Method optionalEnumMethod = GenericEnumHolder.class.getDeclaredMethod("getOptionalEnum");

        Method nonEnumListMethod = GenericEnumHolder.class.getDeclaredMethod("getNonEnumList");
        Method nonEnumOptionalMethod = GenericEnumHolder.class.getDeclaredMethod("getNonEnumOptional");

        return Stream.of(
            Arguments.of(TestEnum.class, true),
            Arguments.of(enumListMethod.getGenericReturnType(), true),
            Arguments.of(optionalEnumMethod.getGenericReturnType(), true),

            Arguments.of(String.class, false),
            Arguments.of(int.class, false),
            Arguments.of(List.class, false),
            Arguments.of(Optional.class, false),
            Arguments.of(nonEnumListMethod.getGenericReturnType(), false),
            Arguments.of(nonEnumOptionalMethod.getGenericReturnType(), false)
        );
    }

    private static class GenericEnumHolder<T extends Enum<T>> {

        private T enumValue;

        private List<TestEnum> getEnumList() {
            return null;
        }

        private Optional<TestEnum> getOptionalEnum() {
            return null;
        }

        private List<String> getNonEnumList() {
            return null;
        }

        private Optional<String> getNonEnumOptional() {
            return null;
        }
    }
}
