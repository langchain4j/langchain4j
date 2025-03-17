package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JsonCodecTest {

    record Person(String name, int age) {}

    private static final String PERSON_JSON =
            """
            {
                "name": "Klaus",
                "age": 42
            }
            """;

    static List<Json.JsonCodec> codecs() {
        return List.of(new JacksonJsonCodec());
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record(Json.JsonCodec codec) {

        // when
        Person person = codec.fromJson(PERSON_JSON, Person.class);

        // then
        assertThat(person.name()).isEqualTo("Klaus");
        assertThat(person.age()).isEqualTo(42);
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record_missing_fields(Json.JsonCodec codec) {

        // given
        String json = "{}";

        // when
        Person pojo = codec.fromJson(json, Person.class);

        // then
        assertThat(pojo.name()).isNull();
        assertThat(pojo.age()).isEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record_different_field_order(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "age": 42,
                    "name": "Klaus"
                }
                """;

        // when
        Person pojo = codec.fromJson(json, Person.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).isEqualTo(42);
    }

    /**
     * To prevent issues caused by LLM hallucinations,
     * the default behavior is to fail when a hallucination is detected.
     * If LLM generates structured output or a tool call containing unknown fields or properties,
     * we fail by default.
     */
    @ParameterizedTest
    @MethodSource("codecs")
    void should_fail_on_unknown_fields_by_default(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "name": "Klaus",
                    "age": 42,
                    "married": false
                }
                """;

        // when-then
        assertThatThrownBy(() -> codec.fromJson(json, Person.class))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasCauseExactlyInstanceOf(UnrecognizedPropertyException.class)
                .hasMessageContaining("married");

        // if required, user can override the default behaviour and ignore unknown properties
        @JsonIgnoreProperties(ignoreUnknown = true)
        record LenientPersonRecord(String name, int age) {}

        LenientPersonRecord lenientPerson = codec.fromJson(json, LenientPersonRecord.class);
        assertThat(lenientPerson).isEqualTo(new LenientPersonRecord("Klaus", 42));
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record_null_value(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "name": "Klaus",
                    "age": null
                }
                """;

        // when
        Person pojo = codec.fromJson(json, Person.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).isEqualTo(0);
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record_wrong_type(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "name": "Klaus",
                    "age": "42"
                }
                """;

        // when
        Person pojo = codec.fromJson(json, Person.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).isEqualTo(42);
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record_wrong_type_2(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "name": "Klaus",
                    "age": 42.0
                }
                """;

        // when
        Person pojo = codec.fromJson(json, Person.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).isEqualTo(42);
    }

    record PersonRecordWithNestedRecord(String name, Address address) {}

    record Address(String city) {}

    @ParameterizedTest
    @MethodSource("codecs")
    void record_with_nested_record(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "name": "Klaus",
                    "address": {
                        "city": "Langley Falls"
                    }
                }
                """;

        // when
        PersonRecordWithNestedRecord pojo = codec.fromJson(json, PersonRecordWithNestedRecord.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.address().city()).isEqualTo("Langley Falls");
    }

    record PersonRecordWithCollections(
            String name,
            Collection<String> collection,
            List<String> list,
            Set<Object> set,
            String[] array,
            Map<Object, Object> map) {}

    @ParameterizedTest
    @MethodSource("codecs")
    void record_with_missing_collections(Json.JsonCodec codec) {

        // given
        String json = """
                {
                    "name": "Klaus"
                }
                """;

        // when
        PersonRecordWithCollections pojo = codec.fromJson(json, PersonRecordWithCollections.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.collection()).isNull();
        assertThat(pojo.list()).isNull();
        assertThat(pojo.set()).isNull();
        assertThat(pojo.array()).isNull();
        assertThat(pojo.map()).isNull();
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record_with_empty_collections(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "name": "Klaus",
                    "collection": [],
                    "list": [],
                    "set": [],
                    "array": [],
                    "map": {}
                }
                """;

        // when
        PersonRecordWithCollections pojo = codec.fromJson(json, PersonRecordWithCollections.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.collection()).isEmpty();
        assertThat(pojo.list()).isEmpty();
        assertThat(pojo.set()).isEmpty();
        assertThat(pojo.array()).isEmpty();
        assertThat(pojo.map()).isEmpty();
    }

    record PersonRecordWithOptional(String name, Optional<Integer> age) {}

    @Disabled("optional fields are currently not supported")
    @ParameterizedTest
    @MethodSource("codecs")
    void record_with_optional_present(Json.JsonCodec codec) {

        // when
        PersonRecordWithOptional pojo = codec.fromJson(PERSON_JSON, PersonRecordWithOptional.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).hasValue(42);
    }

    @Disabled("optional fields are currently not supported")
    @ParameterizedTest
    @MethodSource("codecs")
    void record_with_optional_absent(Json.JsonCodec codec) {

        // given
        String json = """
                {
                    "name": "Klaus"
                }
                """;

        // when
        PersonRecordWithOptional pojo = codec.fromJson(json, PersonRecordWithOptional.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).isEmpty();
    }

    @Disabled("optional fields are currently not supported")
    @ParameterizedTest
    @MethodSource("codecs")
    void record_with_optional_null(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "name": "Klaus",
                    "age": null
                }
                """;

        // when
        PersonRecordWithOptional pojo = codec.fromJson(json, PersonRecordWithOptional.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void inner_record(Json.JsonCodec codec) {

        // given
        record PersonInnerRecord(String name, int age) {}

        // when
        PersonInnerRecord pojo = codec.fromJson(PERSON_JSON, PersonInnerRecord.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).isEqualTo(42);
    }

    record PersonRecordWithValidation(String name, int age) {

        public PersonRecordWithValidation {
            if (age < 0) {
                throw new IllegalArgumentException("age must be positive");
            }
        }
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record_with_validation(Json.JsonCodec codec) {

        // given
        String json =
                """
                {
                    "name": "Klaus",
                    "age": -1
                }
                """;

        // when-then
        assertThatThrownBy(() -> codec.fromJson(json, PersonRecordWithValidation.class))
                .isExactlyInstanceOf(RuntimeException.class)
                .hasCauseExactlyInstanceOf(ValueInstantiationException.class)
                .hasRootCauseExactlyInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("age must be positive");
    }

    record PersonRecordCustomCtor(String name, int age) {

        public PersonRecordCustomCtor(String name) {
            this(name, 99);
        }
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void record_with_custom_ctor(Json.JsonCodec codec) {

        // when
        PersonRecordCustomCtor pojo = codec.fromJson(
                """
                {
                    "name": "Klaus"
                }
                """,
                PersonRecordCustomCtor.class);

        // then
        assertThat(pojo.name()).isEqualTo("Klaus");
        assertThat(pojo.age()).isEqualTo(0);
    }

    // static nested classes

    static class PersonStaticNestedClass {

        private String name;
        private int age;
    }

    @ParameterizedTest
    @MethodSource("codecs")
    void static_nested_class(Json.JsonCodec codec) {

        // when
        PersonStaticNestedClass pojo = codec.fromJson(PERSON_JSON, PersonStaticNestedClass.class);

        // then
        assertThat(pojo.name).isEqualTo("Klaus");
        assertThat(pojo.age).isEqualTo(42);
    }
}
