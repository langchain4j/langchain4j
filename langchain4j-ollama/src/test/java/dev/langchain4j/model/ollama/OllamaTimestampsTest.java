package dev.langchain4j.model.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OllamaTimestampsTest {

    @ParameterizedTest
    @CsvSource({
        "2024-09-04T15:21:17.521503059Z, 0",
        "2024-08-04T00:54:54.764563036+02:00, 2",
        "2024-06-15T05:18:13.974383393-07:00, -7",
        "2024-08-04T00:54:54.54+02:00, 2",
        "2024-08-04T00:00:04.04Z, 0",
        "2024-08-04T00:54:54Z, 0"
    })
    void should_parse_a_timestamp_and_drop_its_fractional_seconds(String timestamp, int offsetHours) {
        OffsetDateTime parsed = OllamaTimestamps.parse(timestamp);

        assertThat(parsed).isEqualTo(OffsetDateTime.parse(timestamp).withNano(0));
        assertThat(parsed.getOffset()).isEqualTo(ZoneOffset.ofHours(offsetHours));
        assertThat(parsed.getNano()).isZero();
    }

    @Test
    void should_pass_an_already_parsed_timestamp_through() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2024-09-04T15:21:17.521503059Z");

        assertThat(OllamaTimestamps.parse(timestamp)).isSameAs(timestamp);
    }

    @Test
    void should_parse_null_as_null() {
        assertThat(OllamaTimestamps.parse(null)).isNull();
    }

    @Test
    void should_read_a_timestamp_off_the_wire() {
        OllamaModel model = OllamaJsonUtils.fromJson(
                "{\"name\":\"llama3\",\"modified_at\":\"2024-09-04T15:21:17.521503059Z\",\"size\":42}",
                OllamaModel.class);

        assertThat(model.getModifiedAt()).isEqualTo(OffsetDateTime.parse("2024-09-04T15:21:17Z"));
    }

}
