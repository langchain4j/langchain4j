package dev.langchain4j.service;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.service.output.OutputParsingException;
import dev.langchain4j.test.mock.MockChatModel;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiServicesMapReturnTypeTest {

    private static final String JSON_INSTRUCTIONS =
            "\nYou must answer strictly in the following JSON format: {\"key\": (value)}";

    interface Meta {

        @SystemMessage("extract metadata")
        Map<String, Object> meta(@UserMessage String text);
    }

    @Test
    void should_append_json_format_instructions_when_return_type_is_Map() {
        ChatModel chatModel = spy(MockChatModel.thatResponds("{\"capital\":\"Paris\"}"));
        Meta assistant = AiServices.builder(Meta.class).chatModel(chatModel).build();

        Map<String, Object> metadata = assistant.meta("capital of France?");

        assertThat(metadata).containsEntry("capital", "Paris");
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                systemMessage("extract metadata"),
                                userMessage("capital of France?" + JSON_INSTRUCTIONS))
                        .build());
    }

    @Test
    void should_append_json_format_instructions_for_Map_when_model_supports_json_schema() {
        ChatModel chatModel = spy(MockChatModel.thatResponds("{\"capital\":\"Paris\"}")
                .withSupportedCapabilities(RESPONSE_FORMAT_JSON_SCHEMA));
        Meta assistant = AiServices.builder(Meta.class).chatModel(chatModel).build();

        assertThat(assistant.meta("capital of France?")).containsEntry("capital", "Paris");
        verify(chatModel)
                .chat(ChatRequest.builder()
                        .messages(
                                systemMessage("extract metadata"),
                                userMessage("capital of France?" + JSON_INSTRUCTIONS))
                        .build());
    }

    @Test
    void should_fail_when_Map_answer_is_not_json() {
        Meta assistant = AiServices.builder(Meta.class)
                .chatModel(MockChatModel.thatResponds("Paris"))
                .build();

        assertThatThrownBy(() -> assistant.meta("capital of France?"))
                .isExactlyInstanceOf(OutputParsingException.class);
    }
}
