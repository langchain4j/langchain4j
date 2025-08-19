package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParsingUtilsTest {

    @Test
    void should_dump_problematic_text_in_base64() {

        // given
        String text = "invalid json";

        // when
        OutputParsingException e = ParsingUtils.outputParsingException(text, Object.class);

        // then
        assertThat(e).hasMessageContaining("aW52YWxpZCBqc29u");
    }

    @Test
    void should_dump_problematic_text_in_base64_null() {

        // given
        String text = null;

        // when
        OutputParsingException e = ParsingUtils.outputParsingException(text, Object.class);

        // then
        assertThat(e).hasMessageContaining("null");
    }
}
