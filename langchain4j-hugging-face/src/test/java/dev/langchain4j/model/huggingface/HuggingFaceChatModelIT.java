package dev.langchain4j.model.huggingface;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledOnJre;

import java.time.LocalDate;

import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.condition.JRE.JAVA_17;

@EnabledIf(value = "isMonday", disabledReason = "Not enough credits in the HF plan to run it more often")
@EnabledOnJre(value = JAVA_17, disabledReason = "Not enough credits in the HF plan to run it more often")
@EnabledIfEnvironmentVariable(named = "HF_API_KEY", matches = ".+")
class HuggingFaceChatModelIT {

    public static boolean isMonday() {
        return LocalDate.now().getDayOfWeek() == MONDAY;
    }

    @Test
    void should_send_messages_and_receive_response() {

        // given
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("HF_API_KEY"))
                .baseUrl("https://router.huggingface.co/v1")
                .modelName("HuggingFaceTB/SmolLM3-3B:hf-inference")
                .build();

        // when
        String answer = model.chat("What is the capital of Germany?");

        // then
        assertThat(answer).containsIgnoringCase("Berlin");
    }
}
