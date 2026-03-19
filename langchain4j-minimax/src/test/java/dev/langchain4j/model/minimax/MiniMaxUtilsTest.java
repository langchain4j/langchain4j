package dev.langchain4j.model.minimax;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MiniMaxUtilsTest {

    @Test
    void should_return_null_for_null_temperature() {
        assertThat(MiniMaxUtils.clampTemperature(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource({
        "0.0, 0.0",
        "0.5, 0.5",
        "1.0, 1.0",
        "0.7, 0.7",
        "-0.1, 0.0",
        "-1.0, 0.0",
        "1.1, 1.0",
        "2.0, 1.0",
        "100.0, 1.0"
    })
    void should_clamp_temperature(double input, double expected) {
        assertThat(MiniMaxUtils.clampTemperature(input)).isEqualTo(expected);
    }

    @Test
    void should_have_correct_default_url() {
        assertThat(MiniMaxUtils.DEFAULT_MINIMAX_URL).isEqualTo("https://api.minimax.io/v1");
    }
}
