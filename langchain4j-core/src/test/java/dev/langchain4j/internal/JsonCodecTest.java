package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
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


    @Nested
    class JavaTime {

        record JavaTimeRecord(
                Instant instant,
                LocalDate localDate,
                LocalDate localDate2,
                LocalTime localTime,
                LocalTime localTime2,
                LocalDateTime localDateTime,
                LocalDateTime localDateTime2,
                OffsetTime offsetTime,
                OffsetDateTime offsetDateTime,
                ZonedDateTime zonedDateTime,
                Duration duration,
                Period period,
                Year year,
                YearMonth yearMonth,
                MonthDay monthDay,
                ZoneId zoneId,
                ZoneOffset zoneOffset) {
        }

        @ParameterizedTest
        @MethodSource("dev.langchain4j.internal.JsonCodecTest#codecs")
        void record_with_java_time_fields(Json.JsonCodec codec) {

            // given - regression test: without explicit (de)serializers in JacksonJsonCodec,
            // none of these except LocalDate/LocalTime/LocalDateTime would parse.
            var jsonSrc = """
            {
                "instant": "2007-12-03T10:15:30Z",
                "localDate": "2007-12-03",
                "localDate2": {
                    "year": "2007",
                    "month": "12",
                    "day": "04"
                },
                "localTime": "10:15:30",
                "localTime2": {
                    "hour": "10",
                    "minute": "15",
                    "second": "31"
                },
                "localDateTime": "2007-12-03T10:15:30",
                "localDateTime2": {
                    "date": {
                        "year": "2007",
                        "month": "12",
                        "day": "03"
                    },
                    "time": {
                        "hour": "10",
                        "minute": "15",
                        "second": "31"
                    }
                },
                "offsetTime": "10:15:30+01:00",
                "offsetDateTime": "2007-12-03T10:15:30+01:00",
                "zonedDateTime": "2007-12-03T10:15:30+01:00[Europe/Paris]",
                "duration": "PT15M",
                "period": "P1Y2M3D",
                "year": "2007",
                "yearMonth": "2007-12",
                "monthDay": "--12-03",
                "zoneId": "Europe/Paris",
                "zoneOffset": "+01:00"
            }
            """;

            // when
            JavaTimeRecord pojo = codec.fromJson(jsonSrc, JavaTimeRecord.class);

            // then
            assertThat(pojo.instant()).isEqualTo(Instant.parse("2007-12-03T10:15:30.00Z"));
            assertThat(pojo.localDate()).isEqualTo(LocalDate.of(2007, 12, 3));
            assertThat(pojo.localDate2()).isEqualTo(LocalDate.of(2007, 12, 4));
            assertThat(pojo.localTime()).isEqualTo(LocalTime.of(10, 15, 30));
            assertThat(pojo.localTime2()).isEqualTo(LocalTime.of(10, 15, 31));
            assertThat(pojo.localDateTime()).isEqualTo(LocalDateTime.of(2007, 12, 3, 10, 15, 30));
            assertThat(pojo.localDateTime2()).isEqualTo(LocalDateTime.of(2007, 12, 3, 10, 15, 31));
            assertThat(pojo.offsetTime()).isEqualTo(OffsetTime.parse("10:15:30+01:00"));
            assertThat(pojo.offsetDateTime()).isEqualTo(OffsetDateTime.parse("2007-12-03T10:15:30+01:00"));
            assertThat(pojo.zonedDateTime()).isEqualTo(ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]"));
            assertThat(pojo.duration()).isEqualTo(Duration.ofMinutes(15));
            assertThat(pojo.period()).isEqualTo(Period.of(1, 2, 3));
            assertThat(pojo.year()).isEqualTo(Year.of(2007));
            assertThat(pojo.yearMonth()).isEqualTo(YearMonth.of(2007, 12));
            assertThat(pojo.monthDay()).isEqualTo(MonthDay.of(12, 3));
            assertThat(pojo.zoneId()).isEqualTo(ZoneId.of("Europe/Paris"));
            assertThat(pojo.zoneOffset()).isEqualTo(ZoneOffset.of("+01:00"));
        }

        @ParameterizedTest
        @MethodSource("dev.langchain4j.internal.JsonCodecTest#codecs")
        void java_time_round_trip(Json.JsonCodec codec) {

            // given
            JavaTimeRecord javaObject = new JavaTimeRecord(
                    Instant.parse("2007-12-03T10:15:30.00Z"),
                    LocalDate.of(2007, 12, 3),
                    LocalDate.of(2007, 12, 4),
                    LocalTime.of(10, 15, 30),
                    LocalTime.of(10, 15, 31),
                    LocalDateTime.of(2007, 12, 3, 10, 15, 30),
                    LocalDateTime.of(2007, 12, 3, 10, 15, 31),
                    OffsetTime.parse("10:15:30+01:00"),
                    OffsetDateTime.parse("2007-12-03T10:15:30+01:00"),
                    ZonedDateTime.parse("2007-12-03T10:15:30+01:00[Europe/Paris]"),
                    Duration.ofMinutes(15),
                    Period.of(1, 2, 3),
                    Year.of(2007),
                    YearMonth.of(2007, 12),
                    MonthDay.of(12, 3),
                    ZoneId.of("Europe/Paris"),
                    ZoneOffset.of("+01:00"));
            var jsonTarget = """
            {
                "instant": "2007-12-03T10:15:30Z",
                "localDate": "2007-12-03",
                "localDate2": "2007-12-04",
                "localTime": "10:15:30",
                "localTime2": "10:15:31",
                "localDateTime": "2007-12-03T10:15:30",
                "localDateTime2": "2007-12-03T10:15:31",
                "offsetTime": "10:15:30+01:00",
                "offsetDateTime": "2007-12-03T10:15:30+01:00",
                "zonedDateTime": "2007-12-03T10:15:30+01:00[Europe/Paris]",
                "duration": "PT15M",
                "period": "P1Y2M3D",
                "year": "2007",
                "yearMonth": "2007-12",
                "monthDay": "--12-03",
                "zoneId": "Europe/Paris",
                "zoneOffset": "+01:00"
            }
            """;

            // when
            String json = codec.toJson(javaObject);

            // then
            assertThat(json).isEqualTo(jsonTarget.replaceAll("\\s", ""));
        }
    }
}
