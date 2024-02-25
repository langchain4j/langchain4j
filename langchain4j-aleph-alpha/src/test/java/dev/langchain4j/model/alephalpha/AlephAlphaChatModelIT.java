package dev.langchain4j.model.alephalpha;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

class AlephAlphaChatModelIT {

    @Test
    void should_generate_simple_completion() {
        ChatLanguageModel model = AlephAlphaChatModel
            .builder()
            .apiKey(System.getenv("ALEPH_ALPHA_API_KEY"))
            .stopSequences(Lists.list("\n"))
            .logRequests(true)
            .logResponses(true)
            .build();
        UserMessage message = userMessage("A bird in the hand is worth two in the");

        Response<AiMessage> response = model.generate(message);

        assertThat(response.content().text()).isEqualTo(" bush.");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(10);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(3);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(13);
    }

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {
        ChatLanguageModel model = AlephAlphaChatModel
            .builder()
            .apiKey(System.getenv("ALEPH_ALPHA_API_KEY"))
            .maxTokens(15)
            .logRequests(true)
            .logResponses(true)
            .build();
        UserMessage message = userMessage("Tell me a story about rabbit Jack.\n");

        Response<AiMessage> response = model.generate(message);

        assertThat(response.content().text()).contains("Rabbit Jack is a rabbit");
        assertThat(response.finishReason()).isEqualTo(FinishReason.LENGTH);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(9);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(15);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(24);
    }

    @Test
    void should_obey_stop_sequence() {
        ChatLanguageModel model = AlephAlphaChatModel
            .builder()
            .apiKey(System.getenv("ALEPH_ALPHA_API_KEY"))
            .stopSequences(Lists.list("Q:"))
            .logRequests(true)
            .logResponses(true)
            .build();

        UserMessage message = userMessage("Q:What is ce capital of Italy? When it was founded? A:");

        Response<AiMessage> response = model.generate(message);

        assertThat(response.content().text()).contains("Rome").contains("753 BC");
    }

    @Test
    void should_understand_question_and_answer_in_German() {
        ChatLanguageModel model = AlephAlphaChatModel
            .builder()
            .apiKey(System.getenv("ALEPH_ALPHA_API_KEY"))
            .modelName("luminous-base-control")
            .logRequests(true)
            .logResponses(true)
            .build();
        UserMessage message = userMessage("In welcher Stadt und welchem Bundesland findet Oktoberfest statt?");

        Response<AiMessage> response = model.generate(message);

        assertThat(response.content().text()).contains("München").contains("Bayern");
        assertThat(response.finishReason()).isEqualTo(FinishReason.STOP);
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(11);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(4);
        assertThat(response.tokenUsage().totalTokenCount()).isEqualTo(15);
    }
}
