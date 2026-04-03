package dev.langchain4j.model.anthropic.internal.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AnthropicContentTest {

    @Test
    void should_deserialize_content_with_unknown_type() throws JsonProcessingException {

        // given
        String json = "{\"type\": \"some_new_type\"}";

        // when
        AnthropicContent anthropicContent = new ObjectMapper().readValue(json, AnthropicContent.class);

        // then
        assertThat(anthropicContent.type).isEqualTo("some_new_type");
    }
}
