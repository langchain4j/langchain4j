package dev.langchain4j.service.output;

import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.structured.Description;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemasTest {

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
        Optional<JsonSchema> jsonSchema = JsonSchemas.jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema addressSchema = (JsonObjectSchema) jsonSchema.get().schema().properties().get("address");
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
        Optional<JsonSchema> jsonSchema = JsonSchemas.jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema addressSchema = (JsonObjectSchema) jsonSchema.get().schema().properties().get("address");
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
        Optional<JsonSchema> jsonSchema = JsonSchemas.jsonSchemaFrom(Person.class);

        // then
        JsonObjectSchema addressSchema = (JsonObjectSchema) jsonSchema.get().schema().properties().get("address");
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
        Optional<JsonSchema> jsonSchema = JsonSchemas.jsonSchemaFrom(Person.class);

        // then
        JsonEnumSchema maritalStatusSchema = (JsonEnumSchema) jsonSchema.get().schema().properties().get("maritalStatus");
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
        Optional<JsonSchema> jsonSchema = JsonSchemas.jsonSchemaFrom(Person.class);

        // then
        JsonEnumSchema maritalStatusSchema = (JsonEnumSchema) jsonSchema.get().schema().properties().get("maritalStatus");
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
        Optional<JsonSchema> jsonSchema = JsonSchemas.jsonSchemaFrom(Person.class);

        // then
        JsonEnumSchema maritalStatusSchema = (JsonEnumSchema) jsonSchema.get().schema().properties().get("maritalStatus");
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
        Optional<JsonSchema> jsonSchema = JsonSchemas.jsonSchemaFrom(Person.class);

        // then
        JsonArraySchema petsSchema = (JsonArraySchema) jsonSchema.get().schema().properties().get("pets");
        assertThat(petsSchema.description()).isEqualTo("pets of a person");
    }
}