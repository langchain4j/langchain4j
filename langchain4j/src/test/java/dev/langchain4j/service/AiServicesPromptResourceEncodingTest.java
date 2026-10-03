package dev.langchain4j.service;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;
import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiServicesPromptResourceEncodingTest {

    static final String NON_ASCII_SYSTEM_MESSAGE = "Répondez toujours en français : café, naïve, 한글, 🚀";

    @Spy
    ChatModel model = ChatModelMock.thatAlwaysResponds("Berlin");

    @SystemMessage(fromResource = "system-message-non-ascii.txt")
    interface AiService {

        String chat(String userMessage);
    }

    @Test
    void system_message_from_resource_is_read_as_utf8() {
        AiService aiService =
                AiServices.builder(AiService.class).chatModel(model).build();

        assertThat(aiService.chat("Country: Germany")).containsIgnoringCase("Berlin");
        verify(model)
                .chat(ChatRequest.builder()
                        .messages(systemMessage(NON_ASCII_SYSTEM_MESSAGE), userMessage("Country: Germany"))
                        .build());
    }
}
