package dev.langchain4j.model.google.genai;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.genai.types.CreateCachedContentConfig;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoogleGenAiCachesTest {

    @Test
    void should_map_system_instruction_contents_and_ttl_to_create_config() {
        List<ChatMessage> messages = List.of(
                SystemMessage.from("You are a helpful assistant."), UserMessage.from("Reusable document context."));

        CreateCachedContentConfig config = GoogleGenAiCaches.toCreateConfig(messages, Duration.ofMinutes(5));

        assertThat(config.systemInstruction()).isPresent();
        assertThat(config.contents()).isPresent();
        assertThat(config.contents().get()).isNotEmpty();
        assertThat(config.ttl()).contains(Duration.ofMinutes(5));
    }

    @Test
    void should_omit_ttl_when_null_and_system_instruction_when_absent() {
        CreateCachedContentConfig config =
                GoogleGenAiCaches.toCreateConfig(List.of(UserMessage.from("Reusable document context.")), null);

        assertThat(config.ttl()).isEmpty();
        assertThat(config.systemInstruction()).isEmpty();
        assertThat(config.contents()).isPresent();
    }
}
