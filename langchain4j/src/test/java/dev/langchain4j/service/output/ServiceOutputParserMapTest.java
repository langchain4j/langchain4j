package dev.langchain4j.service.output;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceOutputParserMapTest {

    private final ServiceOutputParser sut = new ServiceOutputParser();

    @Test
    void map_return_type_gets_json_format_instructions_and_no_json_schema() {
        assertThat(sut.outputFormatInstructions(Map.class)).isEqualTo(MapOutputParser.FORMAT_INSTRUCTIONS);
        assertThat(sut.outputFormatInstructions(new TypeReference<Map<String, Integer>>() {}.getType()))
                .isEqualTo(MapOutputParser.FORMAT_INSTRUCTIONS);
        assertThat(sut.outputFormatInstructions(new TypeReference<Result<Map<String, Object>>>() {}.getType()))
                .isEqualTo(MapOutputParser.FORMAT_INSTRUCTIONS);

        assertThat(sut.jsonSchema(Map.class)).isEmpty();
        assertThat(sut.jsonSchema(new TypeReference<Map<String, String>>() {}.getType()))
                .isEmpty();
    }

    @Test
    void text_and_void_return_types_still_get_no_format_instructions() {
        assertThat(sut.outputFormatInstructions(String.class)).isEmpty();
        assertThat(sut.outputFormatInstructions(void.class)).isEmpty();
        assertThat(sut.outputFormatInstructions(Void.class)).isEmpty();
    }

    @Test
    void parses_map_json_and_rejects_plain_text() {
        ChatResponse json = ChatResponse.builder()
                .aiMessage(AiMessage.from("{\"capital\":\"Paris\"}"))
                .build();

        assertThat(sut.parse(json, new TypeReference<Map<String, String>>() {}.getType()))
                .isEqualTo(Map.of("capital", "Paris"));

        ChatResponse plain =
                ChatResponse.builder().aiMessage(AiMessage.from("Paris")).build();
        assertThatThrownBy(() -> sut.parse(plain, Map.class)).isExactlyInstanceOf(OutputParsingException.class);
    }
}
