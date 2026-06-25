package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OllamaDateDeserializerTest {

    @Mock
    JsonParser jsonParser;

    @Mock
    DeserializationContext deserializationContext;

    @Test
    void should_trim_nanoseconds_and_deserialize_utc_date() throws IOException {
        when(jsonParser.getText()).thenReturn("2024-09-04T15:21:17.521503059Z");

        OffsetDateTime offsetDateTime = new OllamaDateDeserializer().deserialize(jsonParser, deserializationContext);

        assertThat(offsetDateTime.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(offsetDateTime.getYear()).isEqualTo(2024);
        assertThat(offsetDateTime.getMonthValue()).isEqualTo(9);
        assertThat(offsetDateTime.getHour()).isEqualTo(15);
        assertThat(offsetDateTime.getNano()).isEqualTo(0);
    }

    @Test
    void should_trim_nanoseconds_and_deserialize_utc_date_with_offset() throws IOException {
        when(jsonParser.getText()).thenReturn("2024-08-04T00:54:54.764563036+02:00");

        OffsetDateTime offsetDateTime = new OllamaDateDeserializer().deserialize(jsonParser, deserializationContext);

        assertThat(offsetDateTime.getOffset()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(offsetDateTime.getYear()).isEqualTo(2024);
        assertThat(offsetDateTime.getMonthValue()).isEqualTo(8);
        assertThat(offsetDateTime.getHour()).isEqualTo(0);
        assertThat(offsetDateTime.getNano()).isEqualTo(0);
    }

    @Test
    void should_trim_nanoseconds_and_deserialize_utc_date_with_negative_offset() throws IOException {
        when(jsonParser.getText()).thenReturn("2024-06-15T05:18:13.974383393-07:00");

        OffsetDateTime offsetDateTime = new OllamaDateDeserializer().deserialize(jsonParser, deserializationContext);

        assertThat(offsetDateTime.getOffset()).isEqualTo(ZoneOffset.ofHours(-7));
        assertThat(offsetDateTime.getYear()).isEqualTo(2024);
        assertThat(offsetDateTime.getMonthValue()).isEqualTo(6);
        assertThat(offsetDateTime.getHour()).isEqualTo(5);
        assertThat(offsetDateTime.getNano()).isEqualTo(0);
    }

    @Test
    void should_deserialize_short_fractional_seconds_with_offset() throws IOException {
        when(jsonParser.getText()).thenReturn("2024-08-04T00:54:54.54+02:00");

        OffsetDateTime offsetDateTime = new OllamaDateDeserializer().deserialize(jsonParser, deserializationContext);

        assertThat(offsetDateTime.getOffset()).isEqualTo(ZoneOffset.ofHours(2));
        assertThat(offsetDateTime.getYear()).isEqualTo(2024);
        assertThat(offsetDateTime.getMonthValue()).isEqualTo(8);
        assertThat(offsetDateTime.getDayOfMonth()).isEqualTo(4);
        assertThat(offsetDateTime.getHour()).isEqualTo(0);
        assertThat(offsetDateTime.getMinute()).isEqualTo(54);
        assertThat(offsetDateTime.getSecond()).isEqualTo(54);
        assertThat(offsetDateTime.getNano()).isEqualTo(0);
    }

    @Test
    void should_deserialize_short_fractional_seconds_with_utc() throws IOException {
        when(jsonParser.getText()).thenReturn("2024-08-04T00:00:04.04Z");

        OffsetDateTime offsetDateTime = new OllamaDateDeserializer().deserialize(jsonParser, deserializationContext);

        assertThat(offsetDateTime.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(offsetDateTime.getYear()).isEqualTo(2024);
        assertThat(offsetDateTime.getMonthValue()).isEqualTo(8);
        assertThat(offsetDateTime.getDayOfMonth()).isEqualTo(4);
        assertThat(offsetDateTime.getHour()).isEqualTo(0);
        assertThat(offsetDateTime.getMinute()).isEqualTo(0);
        assertThat(offsetDateTime.getSecond()).isEqualTo(4);
        assertThat(offsetDateTime.getNano()).isEqualTo(0);
    }

    @Test
    void should_deserialize_date_without_fractional_seconds() throws IOException {
        when(jsonParser.getText()).thenReturn("2024-08-04T00:54:54Z");

        OffsetDateTime offsetDateTime = new OllamaDateDeserializer().deserialize(jsonParser, deserializationContext);

        assertThat(offsetDateTime.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(offsetDateTime.getYear()).isEqualTo(2024);
        assertThat(offsetDateTime.getMonthValue()).isEqualTo(8);
        assertThat(offsetDateTime.getHour()).isEqualTo(0);
        assertThat(offsetDateTime.getMinute()).isEqualTo(54);
        assertThat(offsetDateTime.getSecond()).isEqualTo(54);
        assertThat(offsetDateTime.getNano()).isEqualTo(0);
    }
}
