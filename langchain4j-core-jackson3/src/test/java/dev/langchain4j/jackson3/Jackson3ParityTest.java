package dev.langchain4j.jackson3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.JsonReadException;
import dev.langchain4j.internal.Json;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * Behaviour the Jackson 2 codec provides and which the Jackson 3 codec must match:
 * lenient date/time parsing, polymorphic handling of sealed types, and keeping the document
 * being read out of the failure message.
 */
class Jackson3ParityTest {

    static class WithDate {
        LocalDate date;
    }

    static class WithTime {
        LocalTime time;
    }

    static class WithDateTime {
        LocalDateTime dateTime;
    }

    sealed interface Shape permits Circle, Square {}

    record Circle(int radius) implements Shape {}

    record Square(int side) implements Shape {}

    static class HasShape {
        Shape shape;
    }

    static class Pojo {
        String name;
        int age;
    }

    @Test
    void reads_iso_date() {
        assertThat(Json.fromJson("{\"date\":\"2026-08-20\"}", WithDate.class).date).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void reads_field_wise_date_object_as_llms_often_emit() {
        WithDate w = Json.fromJson("{\"date\":{\"year\":2026,\"month\":8,\"day\":20}}", WithDate.class);
        assertThat(w.date).isEqualTo(LocalDate.of(2026, 8, 20));
    }

    @Test
    void reads_field_wise_time_object_defaulting_second_and_nano() {
        WithTime w = Json.fromJson("{\"time\":{\"hour\":13,\"minute\":5}}", WithTime.class);
        assertThat(w.time).isEqualTo(LocalTime.of(13, 5, 0, 0));
    }

    @Test
    void reads_field_wise_date_time_object() {
        WithDateTime w = Json.fromJson(
                "{\"dateTime\":{\"date\":{\"year\":2026,\"month\":8,\"day\":20},\"time\":{\"hour\":9,\"minute\":30}}}",
                WithDateTime.class);
        assertThat(w.dateTime).isEqualTo(LocalDateTime.of(2026, 8, 20, 9, 30));
    }

    @Test
    void writes_dates_as_iso_strings() {
        WithDate w = new WithDate();
        w.date = LocalDate.of(2026, 8, 20);
        assertThat(Json.toJson(w)).isEqualTo("{\"date\":\"2026-08-20\"}");
    }

    @Test
    void round_trips_a_sealed_interface_without_jackson_annotations() {
        HasShape original = new HasShape();
        original.shape = new Circle(3);

        HasShape restored = Json.fromJson(Json.toJson(original), HasShape.class);

        assertThat(restored.shape).isInstanceOf(Circle.class);
        assertThat(((Circle) restored.shape).radius()).isEqualTo(3);
    }

    @Test
    void discriminates_between_sealed_subtypes() {
        HasShape original = new HasShape();
        original.shape = new Square(5);

        HasShape restored = Json.fromJson(Json.toJson(original), HasShape.class);

        assertThat(restored.shape).isInstanceOf(Square.class);
        assertThat(((Square) restored.shape).side()).isEqualTo(5);
    }

    /**
     * Mirrors {@code JsonExceptionMessageTest} in langchain4j-core. The promise in
     * {@link dev.langchain4j.exception.JsonException} rests on Jackson leaving
     * {@code INCLUDE_SOURCE_IN_LOCATION} off, which has to hold for this codec too.
     */
    @Test
    void the_document_being_read_is_not_in_the_message() {
        String secret = "sk-not-a-real-key-2f7a";

        assertThatThrownBy(() -> Json.fromJson("{\"name\":\"" + secret + "\", \"age\": }", Pojo.class))
                .isInstanceOf(JsonReadException.class)
                .hasMessageContaining("Unexpected character")
                .hasMessageNotContaining(secret);
    }
}
