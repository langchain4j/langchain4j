package dev.langchain4j.service;

import static dev.langchain4j.data.message.AiMessage.aiMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.json.Polymorphic;
import dev.langchain4j.json.PolymorphicValue;
import dev.langchain4j.model.chat.mock.ChatModelMock;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolymorphicAiServiceTest {

    @Polymorphic(discriminator = "type")
    sealed interface ChatbotResponse permits TextResponse, ImageResponse {}

    @PolymorphicValue("text")
    record TextResponse(String type, String text) implements ChatbotResponse {}

    @PolymorphicValue("image")
    record ImageResponse(String type, String url) implements ChatbotResponse {}

    interface ChatbotService {
        ChatbotResponse reply(String userMessage);
    }

    interface ChatbotListService {
        List<ChatbotResponse> reply(String userMessage);
    }

    @Test
    void shouldDeserializeTextResponse() {
        String json = """
            { "type": "text", "text": "hello" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        ChatbotService service =
                AiServices.builder(ChatbotService.class).chatModel(model).build();

        ChatbotResponse response = service.reply("hi");

        assertThat(response).isInstanceOf(TextResponse.class);
        TextResponse text = (TextResponse) response;
        assertThat(text.type()).isEqualTo("text");
        assertThat(text.text()).isEqualTo("hello");
    }

    @Test
    void shouldDeserializeImageResponse() {
        String json = """
            { "type": "image", "url": "https://example.com/x.png" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        ChatbotService service =
                AiServices.builder(ChatbotService.class).chatModel(model).build();

        ChatbotResponse response = service.reply("show image");

        assertThat(response).isInstanceOf(ImageResponse.class);
        ImageResponse image = (ImageResponse) response;
        assertThat(image.type()).isEqualTo("image");
        assertThat(image.url()).isEqualTo("https://example.com/x.png");
    }

    @Test
    void shouldFailOnUnknownDiscriminator() {
        String json = """
            { "type": "unknown", "text": "x" }
        """;

        ChatModelMock model = ChatModelMock.thatAlwaysResponds(json);

        ChatbotService service =
                AiServices.builder(ChatbotService.class).chatModel(model).build();

        assertThatThrownBy(() -> service.reply("hi"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown discriminator value");
    }

    @Test
    void prompt_should_contain_discriminator_instructions() {
        ChatModelMock model = ChatModelMock.thatAlwaysResponds("""
                { "type": "text", "text": "ok" }
                """);

        ChatbotService service =
                AiServices.builder(ChatbotService.class).chatModel(model).build();

        service.reply("hello");

        String prompt = model.userMessageText();
        assertThat(prompt)
                .contains("discriminator 'type'")
                .contains("type=text")
                .contains("type=image");
    }
}
