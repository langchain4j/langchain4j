package dev.langchain4j.model.openai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.LENGTH;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatModelIT {

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        ChatLanguageModel model = OpenAiChatModel.withApiKey(System.getenv("OPENAI_API_KEY"));
        UserMessage userMessage = userMessage("hello, how are you?");

        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(1);
        assertThat(tokenUsage.totalTokenCount()).isGreaterThan(14);

        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_length() {

        ChatLanguageModel model = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .maxTokens(3)
                .build();
        UserMessage userMessage = userMessage("hello, how are you?");

        Response<AiMessage> response = model.generate(userMessage);
        System.out.println(response);

        assertThat(response.content().text()).isNotBlank();

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(13);
        assertThat(tokenUsage.outputTokenCount()).isEqualTo(3);
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(16);

        assertThat(response.finishReason()).isEqualTo(LENGTH);
    }
}