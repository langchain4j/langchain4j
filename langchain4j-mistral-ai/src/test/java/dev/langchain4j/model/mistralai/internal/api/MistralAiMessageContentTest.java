package dev.langchain4j.model.mistralai.internal.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Mistral sends the content of a message either as a plain string or as an array of typed content
 * blocks, and both forms have to end up as the same list of {@link MistralAiMessageContent}.
 */
class MistralAiMessageContentTest {

    private final Json.JsonCodec codec = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.SNAKE_CASE)
            .build());

    @Test
    void should_read_a_chat_message_whose_content_is_a_plain_string() {
        MistralAiChatMessage message =
                codec.fromJson("{\"role\":\"assistant\",\"content\":\"Hello\"}", MistralAiChatMessage.class);

        assertThat(message.getRole()).isEqualTo(MistralAiRole.ASSISTANT);
        assertThat(message.getContent()).containsExactly(new MistralAiTextContent("Hello"));
        assertThat(message.getContent().get(0).asText()).isEqualTo("Hello");
    }

    @Test
    void should_read_a_chat_message_whose_content_is_an_array_of_blocks() {
        MistralAiChatMessage message = codec.fromJson(
                "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"Hello\"},"
                        + "{\"type\":\"thinking\",\"thinking\":[{\"type\":\"text\",\"text\":\"Hmm\"}]}]}",
                MistralAiChatMessage.class);

        assertThat(message.getContent()).hasSize(2);
        assertThat(message.getContent().get(0)).isEqualTo(new MistralAiTextContent("Hello"));
        assertThat(message.getContent().get(1)).isInstanceOf(MistralAiThinkingContent.class);
    }

    @Test
    void should_read_a_chat_message_without_content() {
        MistralAiChatMessage message = codec.fromJson("{\"role\":\"assistant\"}", MistralAiChatMessage.class);

        assertThat(message.getContent()).isNull();
    }

    @Test
    void should_read_a_chat_message_whose_content_is_null() {
        MistralAiChatMessage message =
                codec.fromJson("{\"role\":\"assistant\",\"content\":null}", MistralAiChatMessage.class);

        assertThat(message.getContent()).isNull();
    }

    @Test
    void should_read_a_delta_message_whose_content_is_a_plain_string() {
        MistralAiDeltaMessage delta =
                codec.fromJson("{\"role\":\"assistant\",\"content\":\"Hi\"}", MistralAiDeltaMessage.class);

        assertThat(delta.getContent()).containsExactly(new MistralAiTextContent("Hi"));
    }

    @Test
    void should_read_a_delta_message_whose_content_is_an_array_of_blocks() {
        MistralAiDeltaMessage delta = codec.fromJson(
                "{\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}]}",
                MistralAiDeltaMessage.class);

        assertThat(delta.getContent()).containsExactly(new MistralAiTextContent("Hi"));
    }

    @Test
    void should_write_the_content_back_as_an_array_of_blocks() {
        String json = codec.toJson(MistralAiChatMessage.builder()
                .role(MistralAiRole.USER)
                .content(List.of(new MistralAiTextContent("Hello")))
                .build());

        assertThat(json).contains("\"content\":[{\"text\":\"Hello\",\"type\":\"text\"}]");
    }
}
