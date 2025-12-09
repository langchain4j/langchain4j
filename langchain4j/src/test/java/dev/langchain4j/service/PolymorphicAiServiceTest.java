package dev.langchain4j.service;

import dev.langchain4j.json.Polymorphic;
import dev.langchain4j.json.PolymorphicValue;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
    class StubJsonModel implements ChatModel {

        private final String json;

        StubJsonModel(String json) {
            this.json = json;
        }

        @Override
        public String chat(String prompt) {
            return json;
        }
    }
    @Test
    void shouldDeserializeTextResponse() {
        String json = """
            { "type": "text", "text": "hello" }
        """;


        ChatModel model = new StubJsonModel(json);

        ChatbotService service = AiServices.builder(ChatbotService.class)
                .chatModel(model)
                .build();

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

        ChatModel model = new StubJsonModel(json);

        ChatbotService service = AiServices.builder(ChatbotService.class)
                .chatModel(model)
                .build();

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

        ChatModel model = new StubJsonModel(json);

        ChatbotService service = AiServices.builder(ChatbotService.class)
                .chatModel(model)
                .build();

        assertThatThrownBy(() -> service.reply("hi"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Unknown discriminator value");
    }
}
