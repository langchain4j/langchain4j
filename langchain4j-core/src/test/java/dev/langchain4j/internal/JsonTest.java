package dev.langchain4j.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void conversionToJsonAndFromJsonWorks() {
        TestData testData = new TestData();
        testData.setSampleDate(LocalDate.of(2023, 1, 15));
        testData.setSampleDateTime(LocalDateTime.of(2023, 1, 15, 10, 20));
        testData.setSomeValue("value");

        String json = Json.toJson(testData);

        assertThat(json)
                .isEqualTo(
                        "{\"sampleDate\":\"2023-01-15\",\"sampleDateTime\":\"2023-01-15T10:20:00\",\"some_value\":\"value\"}");

        TestData deserializedData = Json.fromJson(json, TestData.class);

        assertThat(deserializedData.getSampleDate()).isEqualTo(testData.getSampleDate());
        assertThat(deserializedData.getSampleDateTime()).isEqualTo(testData.getSampleDateTime());
        assertThat(deserializedData.getSomeValue()).isEqualTo(testData.getSomeValue());
    }

    @Test
    void should_parse_local_date_time_with_rfc3339_offset() {
        // JSON Schema maps LocalDateTime to format "date-time" (RFC 3339), which requires an offset.
        // LLMs honoring that format may emit e.g. "2023-01-15T10:20:00Z", which must not fail parsing.
        TestData testData = Json.fromJson(
                "{\"sampleDate\":\"2023-01-15\",\"sampleDateTime\":\"2023-01-15T10:20:00Z\",\"some_value\":\"value\"}",
                TestData.class);

        assertThat(testData.getSampleDate()).isEqualTo(LocalDate.of(2023, 1, 15));
        assertThat(testData.getSampleDateTime()).isEqualTo(LocalDateTime.of(2023, 1, 15, 10, 20));
    }

    @Test
    void should_parse_local_time_with_rfc3339_offset() {
        // JSON Schema maps LocalTime to format "time" (RFC 3339), which requires an offset.
        LocalTime time = Json.fromJson("\"10:15:30Z\"", LocalTime.class);

        assertThat(time).isEqualTo(LocalTime.of(10, 15, 30));
    }

    @Test
    void should_serialize_zoned_date_time_types_as_iso_strings() {
        ZonedData data = new ZonedData();
        data.instant = Instant.parse("2023-01-15T10:20:00Z");
        data.offsetDateTime = OffsetDateTime.parse("2023-01-15T10:20:00+02:00");
        data.zonedDateTime = ZonedDateTime.parse("2023-01-15T10:20:00+01:00[Europe/Paris]");
        data.duration = Duration.ofHours(2);

        String json = Json.toJson(data);

        // ZonedDateTime is serialized offset-only (RFC 3339 date-time has no zone id).
        assertThat(json)
                .isEqualTo("{\"instant\":\"2023-01-15T10:20:00Z\",\"offsetDateTime\":\"2023-01-15T10:20:00+02:00\","
                        + "\"zonedDateTime\":\"2023-01-15T10:20:00+01:00\",\"duration\":\"PT2H\"}");
    }

    @Test
    void should_deserialize_zoned_date_time_types_preserving_offset() {
        ZonedData data = Json.fromJson(
                "{\"instant\":\"2023-01-15T10:20:00Z\",\"offsetDateTime\":\"2023-01-15T10:20:00+02:00\","
                        + "\"zonedDateTime\":\"2023-01-15T10:20:00+01:00[Europe/Paris]\",\"duration\":\"PT2H\"}",
                ZonedData.class);

        // Offsets must be preserved, not normalized to UTC (ADJUST_DATES_TO_CONTEXT_TIME_ZONE is disabled).
        assertThat(data.instant).isEqualTo(Instant.parse("2023-01-15T10:20:00Z"));
        assertThat(data.offsetDateTime).isEqualTo(OffsetDateTime.parse("2023-01-15T10:20:00+02:00"));
        assertThat(data.zonedDateTime).isEqualTo(ZonedDateTime.parse("2023-01-15T10:20:00+01:00[Europe/Paris]"));
        assertThat(data.duration).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void should_round_trip_zoned_date_time_types() {
        ZonedData data = new ZonedData();
        data.instant = Instant.parse("2023-01-15T10:20:00Z");
        data.offsetDateTime = OffsetDateTime.parse("2023-01-15T10:20:00+02:00");
        data.zonedDateTime = ZonedDateTime.parse("2023-01-15T10:20:00+01:00[Europe/Paris]");
        data.duration = Duration.ofHours(2);

        ZonedData deserialized = Json.fromJson(Json.toJson(data), ZonedData.class);

        assertThat(deserialized.instant).isEqualTo(data.instant);
        assertThat(deserialized.offsetDateTime).isEqualTo(data.offsetDateTime);
        // Offset-only serialization drops the zone id, so compare instant + offset.
        assertThat(deserialized.zonedDateTime.isEqual(data.zonedDateTime)).isTrue();
        assertThat(deserialized.zonedDateTime.getOffset()).isEqualTo(ZoneOffset.ofHours(1));
        assertThat(deserialized.duration).isEqualTo(data.duration);
    }

    private static class ZonedData {
        private Instant instant;
        private OffsetDateTime offsetDateTime;
        private ZonedDateTime zonedDateTime;
        private Duration duration;
    }

    private static class TestData {

        private LocalDate sampleDate;
        private LocalDateTime sampleDateTime;

        @JsonProperty("some_value")
        private String someValue;

        LocalDate getSampleDate() {
            return sampleDate;
        }

        void setSampleDate(LocalDate sampleDate) {
            this.sampleDate = sampleDate;
        }

        LocalDateTime getSampleDateTime() {
            return sampleDateTime;
        }

        void setSampleDateTime(LocalDateTime sampleDateTime) {
            this.sampleDateTime = sampleDateTime;
        }

        String getSomeValue() {
            return someValue;
        }

        void setSomeValue(String someValue) {
            this.someValue = someValue;
        }
    }
}
