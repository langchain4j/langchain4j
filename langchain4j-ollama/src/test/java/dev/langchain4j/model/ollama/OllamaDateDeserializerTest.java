package dev.langchain4j.model.ollama;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

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
}