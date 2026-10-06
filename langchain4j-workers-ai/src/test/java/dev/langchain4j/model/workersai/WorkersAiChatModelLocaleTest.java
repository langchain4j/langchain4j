package dev.langchain4j.model.workersai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.workersai.client.WorkersAiChatCompletionRequest.MessageRole;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated // mutates the JVM-wide default locale
class WorkersAiChatModelLocaleTest {

    @Test
    void should_map_ai_message_role_independently_of_default_locale() {
        Locale defaultLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            assertThat(WorkersAiChatModel.toMessage(AiMessage.from("previous response")).getRole())
                    .isEqualTo(MessageRole.ai);
        } finally {
            Locale.setDefault(defaultLocale);
        }
    }
}
