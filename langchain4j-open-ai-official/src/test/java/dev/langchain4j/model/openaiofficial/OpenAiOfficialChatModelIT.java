package dev.langchain4j.model.openaiofficial;

import com.openai.azure.AzureOpenAIServiceVersion;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelName.GPT_4_O;
import static dev.langchain4j.model.openaiofficial.OpenAiOfficialChatModelName.GPT_4_O_MINI;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiOfficialChatModelIT {

    OpenAiOfficialChatModel model = OpenAiOfficialChatModel.builder()
         //   .baseUrl(System.getenv("AZURE_OPENAI_ENDPOINT"))
           // .apiKey(System.getenv("AZURE_OPENAI_KEY"))
            .azureOpenAIServiceVersion(AzureOpenAIServiceVersion.getV2024_02_15_PREVIEW())
            .modelName(GPT_4_O_MINI)
            .temperature(0.0)
            .build();

    @Test
    void should_generate_answer_and_return_token_usage_and_finish_reason_stop() {

        // given
        UserMessage userMessage = userMessage("What is the capital of France?");

        // when
        Response<AiMessage> response = model.generate(userMessage);

        // then
        assertThat(response.content().text()).contains("Paris");

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(14);
        assertThat(tokenUsage.outputTokenCount()).isGreaterThan(0);
        assertThat(tokenUsage.totalTokenCount())
                .isEqualTo(tokenUsage.inputTokenCount() + tokenUsage.outputTokenCount());

        assertThat(response.finishReason()).isEqualTo(STOP);
    }
}
