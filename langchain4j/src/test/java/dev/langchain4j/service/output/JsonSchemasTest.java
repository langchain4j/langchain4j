package dev.langchain4j.service.output;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.Result;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static dev.langchain4j.service.output.JsonSchemas.jsonSchemaFrom;
import static org.assertj.core.api.Assertions.assertThat;

class JsonSchemasTest {

    class Pojo {

        String field;
    }

    @Test
    void should_return_json_schema_for_pojos() {
        assertThat(jsonSchemaFrom(Pojo.class)).isPresent();
        assertThat(jsonSchemaFrom(new TypeReference<Result<Pojo>>() {
        }.getType())).isPresent();
    }

    @Test
    void should_return_empty_for_not_pojos() {
        assertThat(jsonSchemaFrom(void.class)).isEmpty();
        assertThat(jsonSchemaFrom(String.class)).isEmpty();
        assertThat(jsonSchemaFrom(AiMessage.class)).isEmpty();
        assertThat(jsonSchemaFrom(Response.class)).isEmpty();
        assertThat(jsonSchemaFrom(Integer.class)).isEmpty();
        assertThat(jsonSchemaFrom(LocalDate.class)).isEmpty();
        assertThat(jsonSchemaFrom(new TypeReference<List<Pojo>>() {
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
}
