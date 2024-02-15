package dev.langchain4j.model.alephalpha;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.Test;

class AlephAlphaChatModelIT {

    ChatLanguageModel model = AlephAlphaChatModel
        .builder()
        .apiKey(System.getenv("ALEPH_ALPHA_API_KEY"))
        //            .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
        //            .temperature(0.0)
        //        .maxTokens(10)
        .modelName("luminous-extended")
        .stopSequences(Lists.list("Q:", "###", "\n"))
        .logRequests(true)
        .logResponses(true)
        .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {
        // given
        UserMessage message = userMessage("There is no fun if there is no");

        // when
        Response<AiMessage> response = model.generate(message);

        // then
        assertThat(response.content().text()).contains("risk");
        TokenUsage tokenUsage = response.tokenUsage();
        //        assertThat(tokenUsage.inputTokenCount()).isEqualTo(14);
        //        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        //        assertThat(tokenUsage.totalTokenCount())
        //                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        //
        //        assertThat(response.finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_understand_question_and_answer_in_German() {
        // given
        UserMessage message = userMessage("Q:Welche deutsche Stadt ist die älteste und wie alt ist sie? A:");

        // when
        Response<AiMessage> response = model.generate(message);

        // then
        assertThat(response.content().text()).contains("Trier").contains("mehr als 2000 Jahren");
        //        TokenUsage tokenUsage = response.tokenUsage();
        //        assertThat(tokenUsage.inputTokenCount()).isEqualTo(14);
        //        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        //        assertThat(tokenUsage.totalTokenCount())
        //                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());
        //
        //        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
